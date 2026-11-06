package vicecity.servidor.seguridad

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

/** Claves secretas que vienen de variables de entorno. */
object Secretos {
    private val log = LoggerFactory.getLogger(Secretos::class.java)

    /**
     * Devuelve [valor] o, si falta, una clave aleatoria temporal y un aviso en el log.
     * Sirve para desarrollo; en producción la variable siempre debe estar definida.
     */
    fun oTemporal(valor: String?, variable: String, consecuencia: String): String {
        if (valor != null) return valor
        log.warn("{} no está definida: se usa una clave temporal y {}", variable, consecuencia)
        return Base64.getEncoder().encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
    }
}