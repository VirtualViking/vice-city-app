package vicecity.servidor.seguridad

import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Convierte el identificador anónimo del celular (X-Dispositivo-Id) en dispositivo_hash (RNF-17).
 * HMAC-SHA256 con una clave del servidor: el mismo celular siempre da el mismo hash,
 * pero sin la clave no se puede recuperar ni adivinar el identificador original, que nunca se guarda.
 */
class HuellaDispositivo(secreto: String?) {
    private val clave = SecretKeySpec(
        Secretos.oTemporal(secreto, "DISPOSITIVO_SECRETO", "los celulares se verán como nuevos al reiniciar").toByteArray(),
        ALGORITMO,
    )

    /** Hash hexadecimal de 64 caracteres del UUID recibido. */
    fun de(dispositivoId: UUID): String {
        val mac = Mac.getInstance(ALGORITMO).apply { init(clave) }
        return HexFormat.of().formatHex(mac.doFinal(dispositivoId.toString().toByteArray()))
    }

    private companion object {
        const val ALGORITMO = "HmacSHA256"
    }
}