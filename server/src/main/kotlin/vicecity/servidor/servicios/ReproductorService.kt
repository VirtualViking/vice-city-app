package vicecity.servidor.servicios

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.EliminacionSolicitud
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.EstadoSesion
import vicecity.compartido.EstadoSolicitud
import vicecity.compartido.FinReproduccion
import vicecity.compartido.ReproduccionFallida
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioBloqueos
import vicecity.servidor.datos.RepositorioCola
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.RepositorioListaDefecto
import vicecity.servidor.datos.RepositorioNoReproducibles
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.datosInvalidos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.UsuarioPersonal
import vicecity.servidor.servicios.Reproduccion.bloquearReproductor
import vicecity.servidor.sesionCerrada
import vicecity.servidor.sinSesionAbierta
import vicecity.servidor.solicitudNoActiva
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Control de la reproducción desde el panel del DJ y la vista pública (RF-11, RF-17, RF-25, HU-08, HU-12, HU-14).
 * Todas las acciones responden el estado completo, para que la pantalla se actualice sin otra consulta.
 */
class ReproductorService(private val bd: BaseDatos) {

    suspend fun estado(usuario: UsuarioPersonal): EstadoReproduccion = bd.tx { estadoEnTx(usuario.establecimientoId) }

    /** Enciende el reproductor. Si no suena nada, empieza la siguiente; si estaba en pausa, la retoma. */
    suspend fun iniciar(usuario: UsuarioPersonal): EstadoReproduccion = accion(usuario) { sesion, ahora ->
        encender(sesion, usuario, ahora)
    }

    suspend fun pausar(usuario: UsuarioPersonal): EstadoReproduccion = accion(usuario) { sesion, ahora ->
        if (sesion[Sesiones.estadoReproductor] == EstadoReproductor.REPRODUCIENDO.name) {
            cambiarEstado(sesion, EstadoReproductor.PAUSADO)
            registrarEnReproductor(TipoEvento.REPRODUCTOR_PAUSADO, sesion, usuario, ahora)
        }
    }

    suspend fun reanudar(usuario: UsuarioPersonal): EstadoReproduccion = accion(usuario) { sesion, ahora ->
        encender(sesion, usuario, ahora)
    }

    /** El DJ salta lo que suena: queda como reproducida (con la marca "saltada") y empieza la siguiente. */
    suspend fun saltar(usuario: UsuarioPersonal): EstadoReproduccion = accion(usuario) { sesion, ahora ->
        val sesionId = sesion[Sesiones.id]
        if (RepositorioCola.actual(sesion) != null) {
            Reproduccion.terminarActual(sesionId, ahora, FinCancion.SALTADA, usuario.personalId)
            Reproduccion.siguiente(sesionId, ahora)
        }
    }

    /**
     * La vista pública avisa que una canción terminó. Solo cuenta si todavía es la que suena:
     * así un reintento o una segunda pantalla no saltan otra canción.
     */
    suspend fun terminada(usuario: UsuarioPersonal, fin: FinReproduccion): EstadoReproduccion = accion(usuario) { sesion, ahora ->
        if (esLaQueSuena(sesion, fin.videoId, fin.inicioEn)) {
            val sesionId = sesion[Sesiones.id]
            Reproduccion.terminarActual(sesionId, ahora, FinCancion.TERMINADA)
            Reproduccion.siguiente(sesionId, ahora)
        }
    }

    /**
     * RF-25: el video no se pudo reproducir (por ejemplo, su dueño no permite verlo incrustado). Se salta y queda FALLIDA.
     * Si el error no cambia al reintentar (bloqueado, privado o eliminado), el video se recuerda para no volver a ofrecerlo.
     */
    suspend fun fallida(usuario: UsuarioPersonal, falla: ReproduccionFallida): EstadoReproduccion = accion(usuario) { sesion, ahora ->
        if (esLaQueSuena(sesion, falla.videoId, falla.inicioEn)) {
            val sesionId = sesion[Sesiones.id]
            if (falla.codigo in RepositorioNoReproducibles.CODIGOS_PERMANENTES) {
                RepositorioNoReproducibles.registrar(falla.videoId, falla.codigo, ahora)
            }
            Reproduccion.terminarActual(sesionId, ahora, FinCancion.FALLIDA, codigoError = falla.codigo)
            Reproduccion.siguiente(sesionId, ahora)
        }
    }

    /**
     * HU-14: el DJ quita de la cola (o de lo que suena) contenido que no es musical o es inapropiado.
     * Con [EliminacionSolicitud.bloquear], la canción tampoco se puede pedir durante 24 horas.
     */
    suspend fun eliminar(usuario: UsuarioPersonal, solicitudId: UUID, eliminacion: EliminacionSolicitud): EstadoReproduccion = bd.tx {
        val solicitud = RepositorioCola.solicitud(solicitudId) ?: throw noEncontrado("la canción")
        val sesionId = solicitud[Solicitudes.sesionId]
        val sesion = Sesiones.selectAll()
            .where { (Sesiones.id eq sesionId) and (Sesiones.establecimientoId eq usuario.establecimientoId) }
            .singleOrNull() ?: throw noEncontrado("la canción")
        if (sesion[Sesiones.estado] != EstadoSesion.ABIERTA.name) throw sesionCerrada()
        bloquearReproductor(sesionId)

        val ahora = Instant.now()
        val estado = RepositorioCola.solicitud(solicitudId)!![Solicitudes.estado]
        val videoId = solicitud[Solicitudes.videoId]
        when (estado) {
            EstadoSolicitud.EN_COLA.name -> Solicitudes.update({ Solicitudes.id eq solicitudId }) {
                it[Solicitudes.estado] = EstadoSolicitud.ELIMINADA.name
            }
            EstadoSolicitud.REPRODUCIENDO.name -> Reproduccion.terminarActual(sesionId, ahora, FinCancion.ELIMINADA, usuario.personalId)
            else -> throw solicitudNoActiva(estado)
        }
        val hasta = if (eliminacion.bloquear) ahora.plus(DURACION_BLOQUEO) else null
        if (hasta != null) {
            RepositorioBloqueos.bloquear(usuario.establecimientoId, videoId, eliminacion.motivo, usuario.personalId, ahora, hasta)
        }
        EventoService.registrar(
            TipoEvento.SOLICITUD_ELIMINADA, usuario.establecimientoId, sesionId = sesionId, solicitudId = solicitudId, videoId = videoId,
            datos = buildJsonObject {
                put("motivo", eliminacion.motivo.name)
                put("personal_id", usuario.personalId.toString())
                put("estado_anterior", estado)
                hasta?.let { put("bloqueada_hasta", it.toString()) }
            },
            ocurridoEn = ahora,
        )
        if (estado == EstadoSolicitud.REPRODUCIENDO.name) Reproduccion.siguiente(sesionId, ahora)
        estadoEnTx(usuario.establecimientoId)
    }

    // ---------- Apoyo

    /** Ejecuta [cambio] sobre la sesión abierta, con el reproductor bloqueado, y devuelve el estado nuevo. */
    private suspend fun accion(
        usuario: UsuarioPersonal,
        cambio: JdbcTransaction.(sesion: ResultRow, ahora: Instant) -> Unit,
    ): EstadoReproduccion = bd.tx {
        val abierta = RepositorioEstablecimientos.sesionAbierta(usuario.establecimientoId) ?: throw sinSesionAbierta()
        val sesionId = abierta[Sesiones.id]
        bloquearReproductor(sesionId)
        cambio(Reproduccion.sesion(sesionId), Instant.now())
        estadoEnTx(usuario.establecimientoId)
    }

    private fun encender(sesion: ResultRow, usuario: UsuarioPersonal, ahora: Instant) {
        val antes = EstadoReproductor.valueOf(sesion[Sesiones.estadoReproductor])
        if (antes != EstadoReproductor.REPRODUCIENDO) cambiarEstado(sesion, EstadoReproductor.REPRODUCIENDO)
        if (antes == EstadoReproductor.PAUSADO) registrarEnReproductor(TipoEvento.REPRODUCTOR_REANUDADO, sesion, usuario, ahora)
        val sesionId = sesion[Sesiones.id]
        if (RepositorioCola.actual(Reproduccion.sesion(sesionId)) == null) Reproduccion.siguiente(sesionId, ahora)
    }

    private fun cambiarEstado(sesion: ResultRow, nuevo: EstadoReproductor) {
        Sesiones.update({ Sesiones.id eq sesion[Sesiones.id] }) { it[estadoReproductor] = nuevo.name }
    }

    private fun registrarEnReproductor(tipo: TipoEvento, sesion: ResultRow, usuario: UsuarioPersonal, ahora: Instant) {
        EventoService.registrar(
            tipo, usuario.establecimientoId, sesionId = sesion[Sesiones.id],
            datos = buildJsonObject { put("personal_id", usuario.personalId.toString()) },
            ocurridoEn = ahora,
        )
    }

    private fun esLaQueSuena(sesion: ResultRow, videoId: String, inicioEn: String): Boolean {
        val inicio = runCatching { Instant.parse(inicioEn) }.getOrElse { throw datosInvalidos("inicioEn", "Fecha inválida en 'inicioEn'") }
        val actual = RepositorioCola.actual(sesion) ?: return false
        return actual.videoId == videoId && Instant.parse(actual.inicioEn) == inicio
    }

    private fun estadoEnTx(establecimientoId: UUID): EstadoReproduccion {
        val establecimiento = RepositorioEstablecimientos.porId(establecimientoId)!!
        val sesion = RepositorioEstablecimientos.sesionAbierta(establecimientoId)
        return EstadoReproduccion(
            establecimiento = establecimiento[Establecimientos.nombre],
            sesion = sesion?.let(::sesionDto),
            actual = sesion?.let(RepositorioCola::actual),
            cola = sesion?.let { RepositorioCola.cola(it[Sesiones.id]) }.orEmpty(),
            cancionesListaDefecto = RepositorioListaDefecto.cantidad(establecimientoId),
            actualizadoEn = Instant.now().toString(),
        )
    }

    private companion object {
        val DURACION_BLOQUEO: Duration = Duration.ofHours(24)
    }
}