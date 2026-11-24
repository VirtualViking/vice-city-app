package vicecity.servidor.analitica

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import vicecity.compartido.EjecucionPipeline
import vicecity.compartido.EstadoSesion
import vicecity.compartido.ResultadoRetencion
import vicecity.servidor.BaseDatos
import vicecity.servidor.analitica.Retencion.aplicar
import vicecity.servidor.datos.AnCancionGeneros
import vicecity.servidor.datos.AnCanciones
import vicecity.servidor.datos.AnEjecuciones
import vicecity.servidor.datos.AnEventosRechazados
import vicecity.servidor.datos.AnParticipaciones
import vicecity.servidor.datos.AnReproducciones
import vicecity.servidor.datos.AnSales
import vicecity.servidor.datos.AnSesiones
import vicecity.servidor.datos.AnSolicitudes
import vicecity.servidor.datos.AnVotos
import vicecity.servidor.datos.CancionCategorias
import vicecity.servidor.datos.Canciones
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.dominio.Agenda
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Pipeline de datos de la capa analítica (Entregable 6). Cada ejecución hace, por cada sesión pendiente:
 * 1. Ingesta: lee los eventos crudos de la sesión (tabla evento, que la operación llena en la misma transacción que cada acción).
 * 2. Limpieza: valida, quita duplicados y aparta los eventos inválidos con su motivo ([Limpieza]).
 * 3. Transformación y anonimización: reconstruye solicitudes, votos y reproducciones, completa lo que falta
 *    y reemplaza los identificadores por códigos de la sesión ([Transformacion], [Anonimizador]).
 * 4. Almacenamiento: reemplaza los hechos de la sesión en el esquema analitica y actualiza la dimensión canción.
 * Una sesión cerrada se procesa por última vez y se consolida: queda fija y su clave se borra.
 * Una vez al día aplica además la retención ([Retencion]). Cada ejecución queda registrada en analitica.ejecucion.
 */
class PipelineDatos(private val bd: BaseDatos, private val reloj: () -> Instant = Instant::now) {
    private val log = LoggerFactory.getLogger(PipelineDatos::class.java)

    /** Ejecuta el pipeline. Si ya hay una ejecución en curso, espera a que termine (nunca corren dos a la vez). */
    suspend fun ejecutar(origen: String): EjecucionPipeline = candado.withLock {
        val inicio = reloj()
        val ejecucionId = bd.tx {
            AnEjecuciones.insert {
                it[AnEjecuciones.origen] = origen
                it[AnEjecuciones.inicio] = inicio
                it[eventosLeidos] = 0
                it[eventosCargados] = 0
                it[eventosRechazados] = 0
                it[sesionesProcesadas] = 0
                it[sesionesConsolidadas] = 0
                it[detalle] = JsonObject(emptyMap())
            }[AnEjecuciones.id]
        }

        val total = Acumulado()
        val pendientes = bd.tx { sesionesPendientes() }
        for (sesion in pendientes) {
            try {
                total.sumar(bd.tx { procesarSesion(sesion, ejecucionId, reloj()) })
            } catch (e: Exception) {
                // Una sesión con un problema no detiene las demás; se reintenta en la siguiente ejecución
                log.warn("No se pudo procesar la sesión {} en el pipeline: {}", sesion.id, e.message)
                total.errores++
            }
        }

        val retencion = try {
            if (bd.tx { tocaRetencion(inicio) }) bd.tx { aplicar(reloj()) } else null
        } catch (e: Exception) {
            log.warn("No se pudo aplicar la retención: {}", e.message)
            null
        }

        val fin = reloj()
        bd.tx {
            AnEjecuciones.update({ AnEjecuciones.id eq ejecucionId }) {
                it[AnEjecuciones.fin] = fin
                it[eventosLeidos] = total.leidos
                it[eventosCargados] = total.cargados
                it[eventosRechazados] = total.rechazados
                it[sesionesProcesadas] = total.sesiones
                it[sesionesConsolidadas] = total.consolidadas
                it[detalle] = detalle(total, retencion)
            }
            aEjecucion(AnEjecuciones.selectAll().where { AnEjecuciones.id eq ejecucionId }.single())
        }
    }

    /** Fin de la última ejecución terminada, o null si nunca se ha ejecutado. */
    suspend fun ultimaEjecucion(): Instant? = bd.tx {
        AnEjecuciones.select(AnEjecuciones.fin).where { AnEjecuciones.fin.isNotNull() }
            .orderBy(AnEjecuciones.fin, SortOrder.DESC).limit(1).singleOrNull()?.get(AnEjecuciones.fin)
    }

    // ---------- Etapas

    /** Sesiones operativas que aún no están consolidadas en la capa analítica, de la más antigua a la más reciente. */
    private fun sesionesPendientes(): List<SesionCruda> {
        val consolidadas = AnSesiones.select(AnSesiones.sesionId).where { AnSesiones.consolidada eq true }
        return Sesiones.selectAll().where { Sesiones.id notInSubQuery consolidadas }.orderBy(Sesiones.inicio).map {
            SesionCruda(
                id = it[Sesiones.id], establecimientoId = it[Sesiones.establecimientoId],
                inicio = it[Sesiones.inicio], fin = it[Sesiones.fin],
                cerrada = it[Sesiones.estado] == EstadoSesion.CERRADA.name, automatica = it[Sesiones.automatica],
            )
        }
    }

    private fun JdbcTransaction.procesarSesion(sesion: SesionCruda, ejecucionId: Long, ahora: Instant): ResultadoSesion {
        val clave = UUID.nameUUIDFromBytes("PIPELINE:${sesion.id}".toByteArray()).mostSignificantBits
        exec("SELECT pg_advisory_xact_lock($clave)")

        // 1. Ingesta
        val eventos = Eventos.selectAll().where { Eventos.sesionId eq sesion.id }
            .orderBy(Eventos.ocurridoEn to SortOrder.ASC, Eventos.id to SortOrder.ASC)
            .map(::crudo)
        val videos = eventos.mapNotNull { it.videoId }.toSet()
        val conocidas = if (videos.isEmpty()) emptySet() else
            Canciones.select(Canciones.videoId).where { Canciones.videoId inList videos }.map { it[Canciones.videoId] }.toSet()

        // 2. Limpieza
        val limpieza = Limpieza.limpiar(eventos, sesion, conocidas, ahora)

        // 3. Transformación y anonimización
        val sal = AnSales.select(AnSales.sal).where { AnSales.sesionId eq sesion.id }.singleOrNull()?.get(AnSales.sal)
            ?: Anonimizador.nuevaSal().also { nueva ->
                AnSales.insert {
                    it[sesionId] = sesion.id
                    it[AnSales.sal] = nueva
                    it[creadaEn] = ahora
                }
            }
        val hechos = Transformacion.transformar(sesion, limpieza.validos, sal, ahora)

        // 4. Almacenamiento
        val normalizaciones = hechos.normalizaciones.toMutableMap()
        cargarCanciones(hechos.solicitudes.map { it.videoId }.toSet() + hechos.reproducciones.map { it.videoId }, ahora)
            .forEach { (k, v) -> normalizaciones[k] = (normalizaciones[k] ?: 0) + v }
        guardarHechos(sesion, hechos, ahora)
        if (limpieza.rechazados.isNotEmpty()) {
            AnEventosRechazados.batchInsert(limpieza.rechazados, ignore = true, shouldReturnGeneratedValues = false) { (e, motivo) ->
                this[AnEventosRechazados.eventoId] = e.id
                this[AnEventosRechazados.ejecucionId] = ejecucionId
                this[AnEventosRechazados.tipo] = e.tipo.take(40)
                this[AnEventosRechazados.motivo] = motivo.name
                this[AnEventosRechazados.detectadoEn] = ahora
            }
        }

        // Consolidación: la sesión cerrada queda fija y su clave se borra (anonimización irreversible)
        if (sesion.cerrada) {
            AnSesiones.update({ AnSesiones.sesionId eq sesion.id }) { it[consolidada] = true }
            AnSales.deleteWhere { AnSales.sesionId eq sesion.id }
        }
        return ResultadoSesion(
            leidos = eventos.size,
            cargados = limpieza.validos.size,
            rechazados = limpieza.rechazados.groupingBy { it.second.name }.eachCount(),
            normalizaciones = normalizaciones,
            consolidada = sesion.cerrada,
        )
    }

    private fun guardarHechos(sesion: SesionCruda, hechos: HechosSesion, ahora: Instant) {
        AnSesiones.upsert {
            it[sesionId] = sesion.id
            it[establecimientoId] = sesion.establecimientoId
            it[inicio] = sesion.inicio
            it[fin] = sesion.fin
            it[fechaLocal] = sesion.inicio.atZone(Agenda.ZONA_COLOMBIA).toLocalDate()
            it[automatica] = sesion.automatica
            it[duracionSeg] = sesion.fin?.let { f -> Duration.between(sesion.inicio, f).seconds.coerceAtLeast(0).toInt() }
            it[transiciones] = hechos.transiciones
            it[consolidada] = false
            it[procesadaEn] = ahora
        }
        AnParticipaciones.deleteWhere { AnParticipaciones.sesionId eq sesion.id }
        AnSolicitudes.deleteWhere { AnSolicitudes.sesionId eq sesion.id }
        AnVotos.deleteWhere { AnVotos.sesionId eq sesion.id }
        AnReproducciones.deleteWhere { AnReproducciones.sesionId eq sesion.id }

        AnParticipaciones.batchInsert(hechos.participaciones, shouldReturnGeneratedValues = false) { p ->
            this[AnParticipaciones.sesionId] = sesion.id
            this[AnParticipaciones.participante] = p.participante
            this[AnParticipaciones.accedio] = p.accedio
            this[AnParticipaciones.nuevo] = p.nuevo
            this[AnParticipaciones.solicitudes] = p.solicitudes
            this[AnParticipaciones.votos] = p.votos
        }
        AnSolicitudes.batchInsert(hechos.solicitudes, shouldReturnGeneratedValues = false) { s ->
            this[AnSolicitudes.sesionId] = sesion.id
            this[AnSolicitudes.solicitudRef] = s.solicitudRef
            this[AnSolicitudes.participante] = s.participante
            this[AnSolicitudes.videoId] = s.videoId
            this[AnSolicitudes.solicitadaEn] = s.solicitadaEn
            this[AnSolicitudes.horaLocal] = s.horaLocal.toShort()
            this[AnSolicitudes.estadoFinal] = s.estadoFinal
            this[AnSolicitudes.votos] = s.votos
            this[AnSolicitudes.esperaSeg] = s.esperaSeg
            this[AnSolicitudes.enColaSeg] = s.enColaSeg
        }
        AnVotos.batchInsert(hechos.votos, shouldReturnGeneratedValues = false) { v ->
            this[AnVotos.sesionId] = sesion.id
            this[AnVotos.solicitudRef] = v.solicitudRef
            this[AnVotos.participante] = v.participante
            this[AnVotos.origen] = v.origen
            this[AnVotos.ocurridoEn] = v.ocurridoEn
            this[AnVotos.horaLocal] = v.horaLocal.toShort()
        }
        AnReproducciones.batchInsert(hechos.reproducciones, shouldReturnGeneratedValues = false) { r ->
            this[AnReproducciones.sesionId] = sesion.id
            this[AnReproducciones.orden] = r.orden
            this[AnReproducciones.videoId] = r.videoId
            this[AnReproducciones.origen] = r.origen.name
            this[AnReproducciones.solicitudRef] = r.solicitudRef
            this[AnReproducciones.inicio] = r.inicio
            this[AnReproducciones.fin] = r.fin
            this[AnReproducciones.segundos] = r.segundos
            this[AnReproducciones.resultado] = r.resultado.name
            this[AnReproducciones.finInferido] = r.finInferido
            this[AnReproducciones.votos] = r.votos
            this[AnReproducciones.esperaSeg] = r.esperaSeg
        }
    }

    /** Dimensión canción: copia limpia de las canciones que aparecen en los hechos. Devuelve las normalizaciones hechas. */
    private fun cargarCanciones(videoIds: Set<String>, ahora: Instant): Map<String, Int> {
        if (videoIds.isEmpty()) return emptyMap()
        val conteo = mutableMapOf<String, Int>()
        val generos = CancionCategorias.selectAll().where { CancionCategorias.videoId inList videoIds }
            .groupBy({ it[CancionCategorias.videoId] }, { it[CancionCategorias.categoria] })
        Canciones.selectAll().where { Canciones.videoId inList videoIds }.forEach { c ->
            val videoId = c[Canciones.videoId]
            val artista = Normalizacion.artista(c[Canciones.artista], c[Canciones.canal])
            if (artista != (c[Canciones.artista] ?: c[Canciones.canal])) conteo["ARTISTA_NORMALIZADO"] = (conteo["ARTISTA_NORMALIZADO"] ?: 0) + 1
            AnCanciones.upsert {
                it[AnCanciones.videoId] = videoId
                it[titulo] = c[Canciones.titulo]
                it[AnCanciones.artista] = artista
                it[duracionSeg] = c[Canciones.duracionSeg]
                it[actualizadaEn] = ahora
            }
            AnCancionGeneros.deleteWhere { AnCancionGeneros.videoId eq videoId }
            val limpios = generos[videoId].orEmpty().map(Normalizacion::genero).distinct()
            if (limpios.isNotEmpty()) {
                AnCancionGeneros.batchInsert(limpios, shouldReturnGeneratedValues = false) { g ->
                    this[AnCancionGeneros.videoId] = videoId
                    this[AnCancionGeneros.genero] = g.take(60)
                }
            }
        }
        return conteo
    }

    /** La retención se aplica una vez al día. */
    private fun tocaRetencion(ahora: Instant): Boolean =
        AnEjecuciones.select(AnEjecuciones.detalle)
            .where { AnEjecuciones.inicio greater ahora.minus(Duration.ofHours(24)) }
            .none { "retencion" in it[AnEjecuciones.detalle] }

    private fun detalle(total: Acumulado, retencion: ResultadoRetencion?): JsonObject = buildJsonObject {
        putJsonObject("rechazos") { total.motivos.toSortedMap().forEach { (k, v) -> put(k, v) } }
        putJsonObject("normalizaciones") { total.normalizaciones.toSortedMap().forEach { (k, v) -> put(k, v) } }
        if (total.errores > 0) put("sesiones_con_error", total.errores)
        retencion?.let {
            putJsonObject("retencion") {
                put("solicitudes", it.solicitudes)
                put("votos", it.votos)
                put("dispositivos", it.dispositivos)
                put("eventos", it.eventos)
            }
        }
    }

    private class ResultadoSesion(
        val leidos: Int, val cargados: Int, val rechazados: Map<String, Int>, val normalizaciones: Map<String, Int>, val consolidada: Boolean,
    )

    private class Acumulado {
        var leidos = 0
        var cargados = 0
        var rechazados = 0
        var sesiones = 0
        var consolidadas = 0
        var errores = 0
        val motivos = mutableMapOf<String, Int>()
        val normalizaciones = mutableMapOf<String, Int>()

        fun sumar(r: ResultadoSesion) {
            leidos += r.leidos
            cargados += r.cargados
            rechazados += r.rechazados.values.sum()
            sesiones++
            if (r.consolidada) consolidadas++
            r.rechazados.forEach { (k, v) -> motivos[k] = (motivos[k] ?: 0) + v }
            r.normalizaciones.forEach { (k, v) -> normalizaciones[k] = (normalizaciones[k] ?: 0) + v }
        }
    }

    companion object {
        /** Una sola ejecución a la vez en todo el servidor. */
        private val candado = Mutex()

        private fun crudo(fila: ResultRow) = EventoCrudo(
            id = fila[Eventos.id],
            tipo = fila[Eventos.tipo],
            establecimientoId = fila[Eventos.establecimientoId],
            sesionId = fila[Eventos.sesionId],
            solicitudId = fila[Eventos.solicitudId],
            videoId = fila[Eventos.videoId],
            dispositivoHash = fila[Eventos.dispositivoHash]?.trim(),
            ocurridoEn = fila[Eventos.ocurridoEn],
            datos = fila[Eventos.datos],
        )

        /** Fila de analitica.ejecucion convertida al contrato de la API. */
        fun aEjecucion(fila: ResultRow): EjecucionPipeline {
            val detalle = fila[AnEjecuciones.detalle]
            fun mapa(clave: String): Map<String, Int> =
                (detalle[clave] as? JsonObject)?.mapNotNull { (k, v) -> v.jsonPrimitive.intOrNull?.let { k to it } }?.toMap().orEmpty()
            return EjecucionPipeline(
                id = fila[AnEjecuciones.id],
                origen = fila[AnEjecuciones.origen],
                inicio = fila[AnEjecuciones.inicio].toString(),
                fin = fila[AnEjecuciones.fin]?.toString(),
                eventosLeidos = fila[AnEjecuciones.eventosLeidos],
                eventosCargados = fila[AnEjecuciones.eventosCargados],
                eventosRechazados = fila[AnEjecuciones.eventosRechazados],
                sesionesProcesadas = fila[AnEjecuciones.sesionesProcesadas],
                sesionesConsolidadas = fila[AnEjecuciones.sesionesConsolidadas],
                rechazosPorMotivo = mapa("rechazos"),
                normalizaciones = mapa("normalizaciones"),
                retencion = detalle["retencion"]?.jsonObject?.let { r ->
                    fun n(k: String) = r[k]?.jsonPrimitive?.intOrNull ?: 0
                    ResultadoRetencion(n("solicitudes"), n("votos"), n("dispositivos"), n("eventos"))
                },
            )
        }
    }
}