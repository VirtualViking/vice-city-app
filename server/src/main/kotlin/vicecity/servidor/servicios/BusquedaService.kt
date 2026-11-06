package vicecity.servidor.servicios

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import vicecity.compartido.FuenteBusqueda
import vicecity.compartido.RespuestaBusqueda
import vicecity.servidor.BaseDatos
import vicecity.servidor.ReglasBusqueda
import vicecity.servidor.datos.Canciones
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioCache
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.RepositorioDispositivos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.RepositorioEventos
import vicecity.servidor.datos.RepositorioTerminos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datosInvalidos
import vicecity.servidor.dominio.Textos
import vicecity.servidor.integraciones.CatalogoNoDisponible
import vicecity.servidor.integraciones.CatalogoVideos
import vicecity.servidor.limiteBusquedas
import vicecity.servidor.noEncontrado
import vicecity.servidor.youtubeNoDisponible
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * Búsqueda de videos musicales para el cliente (RF-04), con caché (RNF-14) y protección del cupo de YouTube.
 * Orden: caché vigente → YouTube (si hay cupo) → canciones ya guardadas (respaldo) → error.
 */
class BusquedaService(
    private val bd: BaseDatos,
    private val catalogo: CatalogoVideos,
    private val reglas: ReglasBusqueda,
) {
    private val log = LoggerFactory.getLogger(BusquedaService::class.java)

    suspend fun buscar(codigoQr: String, texto: String, dispositivoHash: String): RespuestaBusqueda {
        val consulta = Textos.limpiar(texto)
        if (consulta.length !in LARGO_PERMITIDO) {
            throw datosInvalidos("q", "Escribe entre ${LARGO_PERMITIDO.first} y ${LARGO_PERMITIDO.last} caracteres para buscar")
        }
        val termino = Textos.normalizar(consulta)
        val ahora = Instant.now()
        val contexto = bd.tx { contexto(codigoQr, dispositivoHash, ahora) }

        val enCache = bd.tx { RepositorioCache.vigente(termino, ahora) }
        val (videoIds, fuente) = if (enCache != null) {
            enCache to FuenteBusqueda.CACHE
        } else {
            consultarCatalogo(consulta, termino, dispositivoHash, ahora)
        }

        return bd.tx {
            val resultados = RepositorioCanciones.porIds(videoIds)
                .filter { it[Canciones.duracionSeg] <= contexto.duracionMaxSeg }   // RF-26: lo que no se podría pedir no se muestra
                .map(RepositorioCanciones::aResultado)
            RepositorioTerminos.registrar(contexto.establecimientoId, termino, Textos.recortar(consulta, 100), ahora)
            EventoService.registrar(
                TipoEvento.BUSQUEDA_REALIZADA, contexto.establecimientoId, sesionId = contexto.sesionId,
                dispositivoHash = dispositivoHash,
                datos = buildJsonObject {
                    put("termino", termino)
                    put("resultados", resultados.size)
                    put("fuente", fuente.name)
                },
                ocurridoEn = ahora,
            )
            RespuestaBusqueda(resultados, fuente)
        }
    }

    /** Pide a YouTube si hay cupo; si no se puede, responde con las canciones ya guardadas o con el error que corresponda. */
    private suspend fun consultarCatalogo(
        consulta: String, termino: String, dispositivoHash: String, ahora: Instant,
    ): Pair<List<String>, FuenteBusqueda> {
        val porDispositivo = bd.tx {
            RepositorioEventos.busquedasYouTube(this, ahora.minus(reglas.ventanaPorDispositivo.toJavaDuration()), dispositivoHash)
        }
        if (porDispositivo.cantidad >= reglas.maxNuevasPorDispositivo) {
            val reintentarEn = (porDispositivo.primera ?: ahora).plus(reglas.ventanaPorDispositivo.toJavaDuration())
            return respaldoLocal(termino) ?: throw limiteBusquedas(reintentarEn)
        }
        val diarias = bd.tx { RepositorioEventos.busquedasYouTube(this, ahora.minus(24.hours.toJavaDuration())) }
        if (diarias.cantidad >= reglas.maxNuevasDiarias) {
            log.warn("Se alcanzó el tope diario de {} búsquedas en YouTube", reglas.maxNuevasDiarias)
            return respaldoLocal(termino) ?: throw youtubeNoDisponible()
        }

        val videos = try {
            catalogo.buscar(consulta, reglas.maxResultadosYouTube)
        } catch (e: CatalogoNoDisponible) {
            log.warn("Búsqueda en YouTube no disponible: {}", e.message)
            return respaldoLocal(termino) ?: throw youtubeNoDisponible()
        }
        bd.tx {
            RepositorioCanciones.guardar(videos, ahora)
            RepositorioCache.guardar(termino, videos.map { it.videoId }, ahora.plus(reglas.duracionCache.toJavaDuration()))
        }
        return videos.map { it.videoId } to FuenteBusqueda.YOUTUBE
    }

    /** Canciones ya guardadas que coinciden con el término, o null si no hay ninguna. */
    private suspend fun respaldoLocal(termino: String): Pair<List<String>, FuenteBusqueda>? {
        val ids = bd.tx { RepositorioCanciones.buscarLocal(termino, reglas.maxResultadosYouTube).map { it[Canciones.videoId] } }
        return ids.takeIf { it.isNotEmpty() }?.let { it to FuenteBusqueda.LOCAL }
    }

    private data class Contexto(val establecimientoId: UUID, val sesionId: UUID?, val duracionMaxSeg: Int)

    /** Establecimiento del QR, su sesión abierta (si hay) y registro de la visita del dispositivo. */
    private fun contexto(codigoQr: String, dispositivoHash: String, ahora: Instant): Contexto {
        val establecimiento = RepositorioEstablecimientos.porCodigoQr(codigoQr) ?: throw noEncontrado("el establecimiento")
        val establecimientoId = establecimiento[Establecimientos.id]
        RepositorioDispositivos.registrar(dispositivoHash, ahora)
        return Contexto(
            establecimientoId = establecimientoId,
            sesionId = RepositorioEstablecimientos.sesionAbierta(establecimientoId)?.get(Sesiones.id),
            duracionMaxSeg = establecimiento[Establecimientos.duracionMaxSeg],
        )
    }

    private companion object {
        val LARGO_PERMITIDO = 2..100
    }
}