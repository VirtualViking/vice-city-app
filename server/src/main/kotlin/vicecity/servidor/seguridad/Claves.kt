package vicecity.servidor.seguridad

import at.favre.lib.crypto.bcrypt.BCrypt

/** Contraseñas del personal con bcrypt (RNF-09). Nunca se guardan en texto plano. */
object Claves {
    private const val COSTO = 12

    fun hash(clave: String): String = BCrypt.withDefaults().hashToString(COSTO, clave.toCharArray())

    fun coincide(clave: String, hash: String): Boolean =
        BCrypt.verifyer().verify(clave.toCharArray(), hash).verified

    /** Hash de relleno: se verifica contra él cuando el email no existe, para no revelar qué emails existen por el tiempo de respuesta. */
    val HASH_RELLENO: String by lazy { hash("relleno-que-nunca-coincide") }
}