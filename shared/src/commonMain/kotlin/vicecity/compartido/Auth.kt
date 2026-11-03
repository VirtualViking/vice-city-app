package vicecity.compartido

import kotlinx.serialization.Serializable

@Serializable
enum class Rol { ADMIN, DJ }

/** POST /api/v1/auth/login */
@Serializable
data class LoginSolicitud(
    val email: String,
    val password: String,
)

@Serializable
data class LoginRespuesta(
    val token: String,
    val expiraEn: String,          // ISO 8601 en UTC
    val rol: Rol,
    val establecimientoId: String,
    val nombre: String,
)