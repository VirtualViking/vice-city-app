package vicecity.compartido

import kotlinx.serialization.Serializable

/** Sesión abierta vista por el cliente: solo lo necesario para unirse a ella. */
@Serializable
data class SesionPublica(
    val id: String,
    val modo: Modo,
)

/** Respuesta de GET /api/v1/establecimientos/{codigoQr}: lo que el cliente ve al escanear el QR (RF-01). */
@Serializable
data class EstablecimientoPublico(
    val nombre: String,
    val sesionActiva: SesionPublica? = null,
)