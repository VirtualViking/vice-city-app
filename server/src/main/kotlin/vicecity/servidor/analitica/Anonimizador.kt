package vicecity.servidor.analitica

import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Anonimización de la capa analítica (opción A del diseño).
 * Cada sesión tiene una clave aleatoria de 32 bytes (analitica.sal_sesion). Con ella:
 * - el hash del dispositivo se reemplaza por un código de participante: HMAC-SHA256(clave, hash), 16 caracteres;
 * - el id de la solicitud se reemplaza por una referencia: HMAC-SHA256(clave, id), 16 caracteres.
 * Dentro de la sesión el mismo celular tiene siempre el mismo código (sirve para usuarios únicos y concentración de votos).
 * Al consolidar la sesión la clave se borra: desde ahí no hay forma de volver del código al dispositivo,
 * y el mismo celular aparece con códigos distintos en sesiones distintas.
 */
object Anonimizador {
    private val aleatorio = SecureRandom()
    const val LARGO_CODIGO = 16

    fun nuevaSal(): ByteArray = ByteArray(32).also(aleatorio::nextBytes)

    fun participante(sal: ByteArray, dispositivoHash: String): String = codigo(sal, "D:${dispositivoHash.trim()}")

    fun solicitud(sal: ByteArray, solicitudId: UUID): String = codigo(sal, "S:$solicitudId")

    private fun codigo(sal: ByteArray, valor: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sal, "HmacSHA256"))
        return mac.doFinal(valor.toByteArray()).joinToString("") { "%02x".format(it) }.take(LARGO_CODIGO)
    }
}