package vicecity.compartido

import kotlinx.serialization.Serializable

@Serializable
enum class EstadoSesion { ABIERTA, CERRADA }

@Serializable
enum class Modo { COLA, LISTA_DEFECTO }

@Serializable
enum class EstadoReproductor { DETENIDO, REPRODUCIENDO, PAUSADO }

/** Sesión de solicitudes de una noche. Fechas en ISO 8601 (UTC). */
@Serializable
data class SesionDto(
    val id: String,
    val estado: EstadoSesion,
    val modo: Modo,
    val estadoReproductor: EstadoReproductor,
    val inicio: String,
    val fin: String? = null,
)

/** Respuesta de POST /api/v1/admin/sesiones/{id}/cerrar */
@Serializable
data class CierreSesion(
    val sesion: SesionDto,
    val noReproducidas: Int,
)

/** Respuesta de GET /api/v1/admin/sesiones */
@Serializable
data class ListaSesiones(
    val sesiones: List<SesionDto>,
)

/** Respuesta de GET /api/v1/admin/sesiones/activa. [sesion] es null si no hay una sesión abierta. */
@Serializable
data class SesionActiva(
    val sesion: SesionDto? = null,
)