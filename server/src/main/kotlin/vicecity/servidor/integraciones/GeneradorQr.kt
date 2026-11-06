package vicecity.servidor.integraciones

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Genera la imagen PNG de un código QR con la librería ZXing. */
object GeneradorQr {
    private const val NEGRO = 0x000000
    private const val BLANCO = 0xFFFFFF

    /**
     * QR cuadrado de [tamano] píxeles con corrección de errores alta (H): se sigue leyendo aunque
     * hasta un 30 % esté sucio, doblado o tapado. Lleva el margen blanco de 4 módulos que exige el estándar.
     */
    fun png(texto: String, tamano: Int): ByteArray {
        val opciones = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamano, tamano, opciones)
        val imagen = BufferedImage(matriz.width, matriz.height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until matriz.width) {
            for (y in 0 until matriz.height) {
                imagen.setRGB(x, y, if (matriz[x, y]) NEGRO else BLANCO)
            }
        }
        return ByteArrayOutputStream().use { salida ->
            ImageIO.write(imagen, "png", salida)
            salida.toByteArray()
        }
    }
}