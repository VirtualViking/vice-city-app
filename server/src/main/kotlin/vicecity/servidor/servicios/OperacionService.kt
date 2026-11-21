package vicecity.servidor.servicios

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import vicecity.compartido.ConfiguracionOperacion
import vicecity.compartido.DiaSemana
import vicecity.compartido.EstadoEstablecimiento
import vicecity.compartido.EstadoOperacion
import vicecity.compartido.EstadoSesion
import vicecity.compartido.EstadoSolicitud
import vicecity.compartido.Horario
import vicecity.compartido.ResultadoLimpieza
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.CacheBusquedas
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Horarios
import vicecity.servidor.datos.RepositorioBloqueos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.datosInvalidos
import vicecity.servidor.dominio.Agenda
import vicecity.servidor.mantenimientoInactivo
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.UsuarioPersonal
import vicecity.servidor.servicios.CicloSesion.bloquearOperacion
import vicecity.servidor.sesionEnCurso
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Operación del establecimiento: horario, programación automática, mantenimiento y limpieza.
 * - Con la programación automática, la sesión se abre al empezar la franja del horario y se cierra al terminar.
 *   El DJ puede cerrarla antes (esa franja ya no se vuelve a abrir) o abrir una fuera del horario (esa no se cierra sola).
 * - Render se duerme sin tráfico, así que el horario se aplica en cada consulta del QR y del panel,
 *   además de la revisión periódica mientras el servidor está despierto.
 * - En mantenimiento el QR queda desactivado y no se puede abrir sesión, ni a mano ni automáticamente.
 */
class OperacionService(private val bd: BaseDatos, private val reloj: () -> Instant = Instant::now) {
    private val log = LoggerFactory.getLogger(OperacionService::class.java)

    suspend fun estado(usuario: UsuarioPersonal): EstadoOperacion {
        reconciliar(usuario.establecimientoId)
        return bd.tx { estadoEnTx(usuario.establecimientoId) }
    }

    /** Guarda el horario y el interruptor de programación automática, y lo aplica enseguida. */
    suspend fun configurar(usuario: UsuarioPersonal, configuracion: ConfiguracionOperacion): EstadoOperacion {
        val franjas = validar(configuracion.horarios)
        bd.tx {
            val establecimientoId = usuario.establecimientoId
            Horarios.deleteWhere { Horarios.establecimientoId eq establecimientoId }
            Horarios.batchInsert(franjas.entries) { (dia, horas) ->
                this[Horarios.establecimientoId] = establecimientoId
                this[Horarios.dia] = dia.value.toShort()
                this[Horarios.apertura] = horas.first
                this[Horarios.cierre] = horas.second
            }
            Establecimientos.update({ Establecimientos.id eq establecimientoId }) {
                it[programacionAutomatica] = configuracion.programacionAutomatica
            }
        }
        return estado(usuario)
    }

    /** El mantenimiento solo se activa entre sesiones. Al desactivarlo, la programación automática vuelve a aplicar. */
    suspend fun cambiarMantenimiento(usuario: UsuarioPersonal, activo: Boolean): EstadoOperacion {
        bd.tx {
            bloquearOperacion(usuario.establecimientoId)
            if (activo && RepositorioEstablecimientos.sesionAbierta(usuario.establecimientoId) != null) throw sesionEnCurso()
            Establecimientos.update({ Establecimientos.id eq usuario.establecimientoId }) { it[mantenimiento] = activo }
        }
        return estado(usuario)
    }

    /**
     * Limpieza entre sesiones (solo en mantenimiento): cierra solicitudes que quedaron pendientes en sesiones cerradas,
     * borra las búsquedas vencidas de la caché, quita los bloqueos vencidos y reinicia la rotación de la lista por defecto.
     */
    suspend fun limpiar(usuario: UsuarioPersonal): ResultadoLimpieza = bd.tx {
        val establecimientoId = usuario.establecimientoId
        val establecimiento = RepositorioEstablecimientos.porId(establecimientoId) ?: throw noEncontrado("el establecimiento")
        if (!establecimiento[Establecimientos.mantenimiento]) throw mantenimientoInactivo()
        val ahora = reloj()

        val cerradas = Sesiones.select(Sesiones.id)
            .where { (Sesiones.establecimientoId eq establecimientoId) and (Sesiones.estado eq EstadoSesion.CERRADA.name) }
            .map { it[Sesiones.id] }
        val pendientes = listOf(EstadoSolicitud.EN_COLA.name, EstadoSolicitud.REPRODUCIENDO.name)
        val solicitudesCerradas = if (cerradas.isEmpty()) 0 else Solicitudes.update({
            (Solicitudes.sesionId inList cerradas) and (Solicitudes.estado inList pendientes)
        }) { it[estado] = EstadoSolicitud.NO_REPRODUCIDA.name }

        val busquedasVencidas = CacheBusquedas.deleteWhere { CacheBusquedas.expiraEn lessEq ahora }
        val bloqueosVencidos = RepositorioBloqueos.borrarVencidos(establecimientoId, ahora)
        val rotacion = establecimiento[Establecimientos.posicionListaDefecto] != null
        Establecimientos.update({ Establecimientos.id eq establecimientoId }) { it[posicionListaDefecto] = null }

        ResultadoLimpieza(solicitudesCerradas, busquedasVencidas, bloqueosVencidos, rotacion)
    }

    /** Aplica el horario a todos los establecimientos con programación automática. La llama la revisión periódica. */
    suspend fun reconciliarTodos() {
        val ids = bd.tx {
            Establecimientos.select(Establecimientos.id)
                .where { Establecimientos.programacionAutomatica eq true }
                .map { it[Establecimientos.id] }
        }
        ids.forEach { reconciliar(it) }
    }

    /**
     * Abre o cierra la sesión según el horario, si la programación automática está encendida.
     * Un fallo aquí no debe tumbar la consulta que lo provocó: se registra en el log y la próxima revisión lo reintenta.
     */
    suspend fun reconciliar(establecimientoId: UUID) {
        try {
            bd.tx { reconciliarEnTx(establecimientoId) }
        } catch (e: Exception) {
            log.warn("No se pudo aplicar el horario del establecimiento {}: {}", establecimientoId, e.message)
        }
    }

    /** Si el QR está activo y, si está fuera de horario, cuándo vuelve a abrir. Se usa al escanear el QR. */
    fun estadoPublico(establecimiento: ResultRow, sesionAbierta: ResultRow?): Pair<EstadoEstablecimiento, Instant?> {
        val ahora = reloj()
        val agenda = agendaDe(establecimiento[Establecimientos.id])
        val estado = estadoDe(establecimiento, sesionAbierta, agenda, ahora)
        val proxima = if (estado == EstadoEstablecimiento.FUERA_DE_HORARIO) agenda.siguienteFranja(ahora)?.inicio else null
        return estado to proxima
    }

    // ---------- Apoyo

    private fun JdbcTransaction.reconciliarEnTx(establecimientoId: UUID) {
        bloquearOperacion(establecimientoId)
        val establecimiento = RepositorioEstablecimientos.porId(establecimientoId) ?: return
        if (!establecimiento[Establecimientos.programacionAutomatica] || establecimiento[Establecimientos.mantenimiento]) return
        val agenda = agendaDe(establecimientoId)
        if (agenda.vacia) return
        val ahora = reloj()

        // Cerrar: una sesión que empezó dentro de una franja se cierra cuando esa franja termina
        RepositorioEstablecimientos.sesionAbierta(establecimientoId)?.let { sesion ->
            val franja = agenda.franjaEn(sesion[Sesiones.inicio])
            if (franja != null && ahora >= franja.fin) {
                // Si el servidor estaba dormido, el cierre queda con la hora del horario y no con la hora en que despertó
                val momento = if (Duration.between(franja.fin, ahora) > TOLERANCIA_CIERRE) franja.fin else ahora
                CicloSesion.cerrar(sesion, momento, personalId = null)
            }
        }

        // Abrir: dentro de una franja, si no hay sesión abierta y ninguna empezó en esta franja (el DJ pudo cerrarla antes)
        val franja = agenda.franjaEn(ahora) ?: return
        if (RepositorioEstablecimientos.sesionAbierta(establecimientoId) != null) return
        val yaHubo = Sesiones.selectAll()
            .where { (Sesiones.establecimientoId eq establecimientoId) and (Sesiones.inicio greaterEq franja.inicio) }
            .count() > 0
        if (!yaHubo) CicloSesion.abrir(establecimientoId, ahora, personalId = null, automatica = true)
    }

    private fun estadoEnTx(establecimientoId: UUID): EstadoOperacion {
        val establecimiento = RepositorioEstablecimientos.porId(establecimientoId) ?: throw noEncontrado("el establecimiento")
        val sesion = RepositorioEstablecimientos.sesionAbierta(establecimientoId)
        val agenda = agendaDe(establecimientoId)
        val ahora = reloj()
        return EstadoOperacion(
            programacionAutomatica = establecimiento[Establecimientos.programacionAutomatica],
            horarios = horariosDe(establecimientoId),
            mantenimiento = establecimiento[Establecimientos.mantenimiento],
            estado = estadoDe(establecimiento, sesion, agenda, ahora),
            proximaApertura = agenda.siguienteFranja(ahora)?.inicio?.toString(),
            proximoCierre = agenda.proximoCierre(ahora)?.toString(),
            zonaHoraria = Agenda.ZONA_COLOMBIA.id,
        )
    }

    private fun estadoDe(establecimiento: ResultRow, sesion: ResultRow?, agenda: Agenda, ahora: Instant) = when {
        establecimiento[Establecimientos.mantenimiento] -> EstadoEstablecimiento.MANTENIMIENTO
        sesion != null -> EstadoEstablecimiento.ABIERTO
        !agenda.vacia && agenda.franjaEn(ahora) == null -> EstadoEstablecimiento.FUERA_DE_HORARIO
        else -> EstadoEstablecimiento.SIN_SESION
    }

    private fun filasHorario(establecimientoId: UUID) =
        Horarios.selectAll().where { Horarios.establecimientoId eq establecimientoId }.orderBy(Horarios.dia).toList()

    private fun agendaDe(establecimientoId: UUID) = Agenda(
        filasHorario(establecimientoId).associate { DayOfWeek.of(it[Horarios.dia].toInt()) to (it[Horarios.apertura] to it[Horarios.cierre]) },
    )

    private fun horariosDe(establecimientoId: UUID) = filasHorario(establecimientoId).map {
        Horario(
            dia = DiaSemana.entries[it[Horarios.dia] - 1],
            apertura = it[Horarios.apertura].format(FORMATO_HORA),
            cierre = it[Horarios.cierre].format(FORMATO_HORA),
        )
    }

    /** Un horario por día, con horas HH:mm y apertura distinta del cierre. */
    private fun validar(horarios: List<Horario>): Map<DayOfWeek, Pair<LocalTime, LocalTime>> {
        if (horarios.map { it.dia }.toSet().size != horarios.size) throw datosInvalidos("horarios", "Cada día puede tener un solo horario")
        return horarios.associate { h ->
            val apertura = hora(h.apertura, "apertura")
            val cierre = hora(h.cierre, "cierre")
            if (apertura == cierre) throw datosInvalidos("horarios", "La apertura y el cierre del ${h.dia.name.lowercase()} no pueden ser iguales")
            DayOfWeek.of(h.dia.ordinal + 1) to (apertura to cierre)
        }
    }

    private fun hora(texto: String, campo: String): LocalTime = try {
        LocalTime.parse(texto.trim(), FORMATO_HORA)
    } catch (e: DateTimeParseException) {
        throw datosInvalidos(campo, "Hora inválida en '$campo': usa el formato HH:mm, por ejemplo 20:00")
    }

    private companion object {
        val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val TOLERANCIA_CIERRE: Duration = Duration.ofMinutes(10)
    }
}