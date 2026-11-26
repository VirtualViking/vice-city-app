package vicecity.compartido

import kotlinx.serialization.Serializable

/** Cuenta del personal vista por el administrador (HU-18). Nunca incluye la contraseña. */
@Serializable
data class CuentaPersonal(
    val id: String,
    val nombre: String,
    val email: String,
    val rol: Rol,
    val activo: Boolean,
    /** ISO 8601 en UTC. */
    val creadoEn: String,
    /** true si es la cuenta con la que se hizo la consulta: no se puede desactivar ni cambiar de rol. */
    val esUsted: Boolean,
)

/** GET /api/v1/admin/personal: las cuentas del establecimiento, primero las activas. */
@Serializable
data class ListaPersonal(val cuentas: List<CuentaPersonal>)

/** POST /api/v1/admin/personal. [clave] es la contraseña inicial: el administrador se la entrega a la persona. */
@Serializable
data class NuevaCuentaPersonal(
    val nombre: String,
    val email: String,
    val rol: Rol,
    val clave: String,
)

/** PUT /api/v1/admin/personal/{id}: nombre, rol y si la cuenta puede entrar. */
@Serializable
data class CambioCuentaPersonal(
    val nombre: String,
    val rol: Rol,
    val activo: Boolean,
)

/** PUT /api/v1/admin/personal/{id}/clave: contraseña nueva (también sirve para cambiar la propia). */
@Serializable
data class NuevaClave(val clave: String)

/** Rangos permitidos. El servidor los valida y la web los muestra como ayuda. */
object LimitesPersonal {
    val NOMBRE = 2..60
    /** bcrypt solo usa los primeros 72 bytes de la contraseña. */
    val CLAVE = 8..72
    const val EMAIL_MAX = 160
}