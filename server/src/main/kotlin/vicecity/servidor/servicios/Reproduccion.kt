package vicecity.servidor.servicios

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.EstadoSesion
import vicecity.compartido.EstadoSolicitud
import vicecity.compartido.Modo
import vicecity.servidor.datos.Canciones
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.ListaDefectoItems
import vicecity.servidor.datos.RepositorioBloqueos
import vicecity.servidor.datos.RepositorioCola
import vicecity.servidor.datos.RepositorioListaDefecto
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Cómo terminó lo que sonaba. */
enum class FinCancion { TERMINADA, SALTADA, FALLIDA, ELIMINADA }

/**
 * Reglas de lo que suena (RF-11, RF-12, RF-17, RF-24, RF-25). Se usan dentro de una transacción.
 * El servidor decide siempre qué sigue: la solicitud con más votos (con empate, la más antigua) o, con la cola vacía,
 * la siguiente canción de la lista por defecto, en círculo. La vista pública solo reproduce y avisa cuando algo termina o falla.
 */
object Reproduccion {

    /** Bloqueo por sesión mientras dura la transacción: dos pantallas o dos acciones del DJ no cambian la canción a la vez. */
    fun JdbcTransaction.bloquearReproductor(sesionId: UUID) {
        val clave = UUID.nameUUIDFromBytes("REPRODUCTOR:$sesionId".toByteArray()).mostSignificantBits
        exec("SELECT pg_advisory_xact_lock($clave)")
    }

    fun sesion(sesionId: UUID): ResultRow = Sesiones.selectAll().where { Sesiones.id eq sesionId }.single()

    /**
     * Si el reproductor está encendido y no suena nada, pone a sonar la siguiente.
     * Se llama cuando llega una solicitud o cuando cambia la lista por defecto.
     */
    fun JdbcTransaction.siEstaLibre(sesionId: UUID, ahora: Instant) {
        bloquearReproductor(sesionId)
        val sesion = sesion(sesionId)
        if (sesion[Sesiones.estado] != EstadoSesion.ABIERTA.name) return
        if (sesion[Sesiones.estadoReproductor] != EstadoReproductor.REPRODUCIENDO.name) return
        if (RepositorioCola.actual(sesion) != null) return
        siguiente(sesionId, ahora)
    }

    /** Termina lo que suena y registra el evento. Con ELIMINADA el evento lo registra quien elimina (SOLICITUD_ELIMINADA). */
    fun terminarActual(sesionId: UUID, ahora: Instant, fin: FinCancion, personalId: UUID? = null, codigoError: Int? = null) {
        val sesion = sesion(sesionId)
        val establecimientoId = sesion[Sesiones.establecimientoId]
        val videoLista = sesion[Sesiones.videoActual]
        if (videoLista != null) {
            Sesiones.update({ Sesiones.id eq sesionId }) {
                it[videoActual] = null
                it[inicioActual] = null
            }
            registrarFin(establecimientoId, sesionId, null, videoLista, ORIGEN_LISTA, sesion[Sesiones.inicioActual] ?: ahora, ahora, fin, personalId, codigoError)
            return
        }
        val sonando = RepositorioCola.sonando(sesionId) ?: return
        val solicitudId = sonando[Solicitudes.id]
        Solicitudes.update({ Solicitudes.id eq solicitudId }) {
            it[estado] = when (fin) {
                FinCancion.FALLIDA -> EstadoSolicitud.FALLIDA.name
                FinCancion.ELIMINADA -> EstadoSolicitud.ELIMINADA.name
                else -> EstadoSolicitud.REPRODUCIDA.name
            }
            it[finReproduccion] = ahora
        }
        if (fin != FinCancion.ELIMINADA) {
            val inicio = sonando[Solicitudes.inicioReproduccion] ?: ahora
            registrarFin(establecimientoId, sesionId, solicitudId, sonando[Solicitudes.videoId], ORIGEN_COLA, inicio, ahora, fin, personalId, codigoError)
        }
    }

    /** Pone a sonar la siguiente: la primera de la cola o, si está vacía, la siguiente de la lista por defecto. */
    fun siguiente(sesionId: UUID, ahora: Instant) {
        val sesion = sesion(sesionId)
        val establecimientoId = sesion[Sesiones.establecimientoId]
        val modoAnterior = Modo.valueOf(sesion[Sesiones.modoActual])

        val proxima = RepositorioCola.cola(sesionId).firstOrNull()
        if (proxima != null) {
            val solicitudId = UUID.fromString(proxima.solicitudId)
            Solicitudes.update({ Solicitudes.id eq solicitudId }) {
                it[estado] = EstadoSolicitud.REPRODUCIENDO.name
                it[inicioReproduccion] = ahora
            }
            Sesiones.update({ Sesiones.id eq sesionId }) {
                it[modoActual] = Modo.COLA.name
                it[videoActual] = null
                it[inicioActual] = null
            }
            if (modoAnterior != Modo.COLA) registrarCambioModo(establecimientoId, sesionId, modoAnterior, Modo.COLA, ahora)
            EventoService.registrar(
                TipoEvento.REPRODUCCION_INICIADA, establecimientoId, sesionId = sesionId, solicitudId = solicitudId, videoId = proxima.videoId,
                datos = buildJsonObject {
                    put("origen", ORIGEN_COLA)
                    put("votos", proxima.votos)
                    put("espera_seg", Duration.between(Instant.parse(proxima.solicitadaEn), ahora).seconds)
                },
                ocurridoEn = ahora,
            )
            return
        }

        // Cola vacía: la lista por defecto, sin las canciones bloqueadas
        val bloqueadas = RepositorioBloqueos.bloqueados(establecimientoId, ahora)
        val lista = RepositorioListaDefecto.canciones(establecimientoId).filter { it[Canciones.videoId] !in bloqueadas }
        if (lista.isEmpty()) {
            // Nada que poner: queda en silencio hasta que llegue una solicitud
            Sesiones.update({ Sesiones.id eq sesionId }) {
                it[videoActual] = null
                it[inicioActual] = null
            }
            return
        }
        val establecimiento = Establecimientos.selectAll().where { Establecimientos.id eq establecimientoId }.single()
        val ultima = sesion[Sesiones.posicionListaDefecto] ?: establecimiento[Establecimientos.posicionListaDefecto] ?: 0
        val elegida = lista.firstOrNull { it[ListaDefectoItems.posicion] > ultima } ?: lista.first()
        val posicion = elegida[ListaDefectoItems.posicion]
        val videoId = elegida[Canciones.videoId]
        Sesiones.update({ Sesiones.id eq sesionId }) {
            it[modoActual] = Modo.LISTA_DEFECTO.name
            it[videoActual] = videoId
            it[inicioActual] = ahora
            it[posicionListaDefecto] = posicion
        }
        // La rotación sigue de una noche a la siguiente
        Establecimientos.update({ Establecimientos.id eq establecimientoId }) { it[posicionListaDefecto] = posicion }
        if (modoAnterior != Modo.LISTA_DEFECTO) registrarCambioModo(establecimientoId, sesionId, modoAnterior, Modo.LISTA_DEFECTO, ahora)
        EventoService.registrar(
            TipoEvento.REPRODUCCION_INICIADA, establecimientoId, sesionId = sesionId, videoId = videoId,
            datos = buildJsonObject {
                put("origen", ORIGEN_LISTA)
                put("posicion", posicion)
            },
            ocurridoEn = ahora,
        )
    }

    private fun registrarFin(
        establecimientoId: UUID, sesionId: UUID, solicitudId: UUID?, videoId: String, origen: String,
        inicio: Instant, ahora: Instant, fin: FinCancion, personalId: UUID?, codigoError: Int?,
    ) {
        if (fin == FinCancion.FALLIDA) {
            EventoService.registrar(
                TipoEvento.REPRODUCCION_FALLIDA, establecimientoId, sesionId = sesionId, solicitudId = solicitudId, videoId = videoId,
                datos = buildJsonObject {
                    put("origen", origen)
                    put("motivo", "YOUTUBE_${codigoError ?: 0}")
                },
                ocurridoEn = ahora,
            )
            return
        }
        EventoService.registrar(
            TipoEvento.REPRODUCCION_FINALIZADA, establecimientoId, sesionId = sesionId, solicitudId = solicitudId, videoId = videoId,
            datos = buildJsonObject {
                put("origen", origen)
                put("segundos_reproducidos", Duration.between(inicio, ahora).seconds.coerceAtLeast(0))
                if (fin == FinCancion.SALTADA) {
                    put("saltada", true)
                    personalId?.let { put("personal_id", it.toString()) }
                }
            },
            ocurridoEn = ahora,
        )
    }

    private fun registrarCambioModo(establecimientoId: UUID, sesionId: UUID, de: Modo, a: Modo, ahora: Instant) {
        EventoService.registrar(
            TipoEvento.MODO_CAMBIADO, establecimientoId, sesionId = sesionId,
            datos = buildJsonObject {
                put("de", de.name)
                put("a", a.name)
            },
            ocurridoEn = ahora,
        )
    }

    /** Valores de "origen" en los eventos de reproducción (catálogo de eventos, sección 5.2). */
    private const val ORIGEN_COLA = "COLA"
    private const val ORIGEN_LISTA = "LISTA_DEFECTO"
}