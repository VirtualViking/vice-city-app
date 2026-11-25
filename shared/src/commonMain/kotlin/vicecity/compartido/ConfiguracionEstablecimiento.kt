package vicecity.compartido

import kotlinx.serialization.Serializable

/**
 * Reglas de la cola del establecimiento (RF-08, RF-09, RF-19, RF-26). Se leen en cada solicitud: un cambio aplica desde la siguiente.
 * - [limiteSolicitudes] canciones por celular cada [ventanaLimiteMin] minutos.
 * - [antirepeticionMin]: minutos después de sonar en que una canción no se puede volver a pedir (0 = se puede enseguida).
 * - [duracionMaxMin]: los videos más largos no aparecen en la búsqueda ni se pueden pedir.
 */
@Serializable
data class ReglasCola(
    val limiteSolicitudes: Int,
    val ventanaLimiteMin: Int,
    val antirepeticionMin: Int,
    val duracionMaxMin: Int,
)

/** Respuesta de GET y PUT /api/v1/admin/configuracion (solo administrador, HU-15 y HU-13). */
@Serializable
data class ConfiguracionEstablecimiento(
    val nombre: String,
    /** Código del QR. No cambia: los QR ya impresos siguen funcionando. */
    val codigoQr: String,
    /** Dirección que contiene el QR: abre la app en este establecimiento. */
    val enlace: String,
    val reglas: ReglasCola,
)

/** Cuerpo de PUT /api/v1/admin/configuracion. */
@Serializable
data class CambioConfiguracion(
    val nombre: String,
    val reglas: ReglasCola,
)

/** Rangos permitidos. El servidor los valida y la web los muestra como ayuda. */
object LimitesConfiguracion {
    val NOMBRE = 3..60
    val LIMITE_SOLICITUDES = 1..20
    val VENTANA_LIMITE_MIN = 5..240
    val ANTIREPETICION_MIN = 0..360
    val DURACION_MAX_MIN = 2..20
}