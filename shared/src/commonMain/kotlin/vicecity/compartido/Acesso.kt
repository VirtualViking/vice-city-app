package vicecity.compartido

import kotlinx.serialization.Serializable

/** Sesión abierta vista por el cliente: solo lo necesario para unirse a ella. */
@Serializable
data class SesionPublica(
    val id: String,
    val modo: Modo,
    /** El DJ pausó las solicitudes y los votos; la música sigue sonando. */
    val solicitudesPausadas: Boolean = false,
)

/**
 * Respuesta de GET /api/v1/establecimientos/{codigoQr}: lo que el cliente ve al escanear el QR (RF-01).
 * [estado] dice si el QR está activo. Con FUERA_DE_HORARIO, [proximaApertura] es la siguiente hora de apertura (ISO 8601, UTC).
 */
@Serializable
data class EstablecimientoPublico(
    val nombre: String,
    val sesionActiva: SesionPublica? = null,
    val estado: EstadoEstablecimiento = EstadoEstablecimiento.SIN_SESION,
    val proximaApertura: String? = null,
)