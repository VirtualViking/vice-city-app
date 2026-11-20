package vicecity.compartido

import kotlinx.serialization.Serializable

/**
 * Si el QR del establecimiento está activo.
 * - ABIERTO: hay una sesión abierta; se puede pedir y votar.
 * - SIN_SESION: dentro del horario (o sin horario), pero la sesión aún no se abre; se puede buscar.
 * - FUERA_DE_HORARIO: el QR está desactivado hasta la próxima apertura.
 * - MANTENIMIENTO: el QR está desactivado mientras el administrador hace mantenimiento.
 */
@Serializable
enum class EstadoEstablecimiento { ABIERTO, SIN_SESION, FUERA_DE_HORARIO, MANTENIMIENTO }

@Serializable
enum class DiaSemana { LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO, DOMINGO }

/**
 * Horario de un día, en hora de Colombia, con formato HH:mm.
 * Si [cierre] es anterior a [apertura], la franja termina al día siguiente (por ejemplo, 20:00 a 03:00).
 */
@Serializable
data class Horario(
    val dia: DiaSemana,
    val apertura: String,
    val cierre: String,
)

/** Cuerpo de PUT /api/v1/admin/operacion. Un día sin horario no abre. */
@Serializable
data class ConfiguracionOperacion(
    val programacionAutomatica: Boolean,
    val horarios: List<Horario>,
)

/**
 * Respuesta de GET y PUT /api/v1/admin/operacion. Las fechas son ISO 8601 (UTC).
 * [proximaApertura] y [proximoCierre] salen del horario; son null si no hay horario.
 */
@Serializable
data class EstadoOperacion(
    val programacionAutomatica: Boolean,
    val horarios: List<Horario>,
    val mantenimiento: Boolean,
    val estado: EstadoEstablecimiento,
    val proximaApertura: String? = null,
    val proximoCierre: String? = null,
    val zonaHoraria: String,
)

/** Cuerpo de PUT /api/v1/admin/operacion/mantenimiento */
@Serializable
data class CambioMantenimiento(
    val activo: Boolean,
)

/** Respuesta de POST /api/v1/admin/operacion/limpieza: qué se limpió. */
@Serializable
data class ResultadoLimpieza(
    val solicitudesCerradas: Int,
    val busquedasVencidas: Int,
    val bloqueosVencidos: Int,
    val rotacionReiniciada: Boolean,
)