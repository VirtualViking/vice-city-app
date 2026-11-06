package vicecity.servidor

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import vicecity.compartido.CodigosError
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.dominio.EnlaceQr
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Código QR del establecimiento en PNG (RF-15): se lee con un lector real y lleva a la app con el código. */
class QrTest : PruebaIntegracion() {

    @Test
    fun `el ADMIN descarga un PNG que se lee y abre la app del establecimiento`() = conApp {
        val r = cliente.get(Rutas.ESTABLECIMIENTO_QR) { bearerAuth(loginAdmin()); parameter("tamano", 600) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Image.PNG, r.headers[HttpHeaders.ContentType]?.let(ContentType::parse))
        assertTrue("qr-${codigoQr()}.png" in r.headers[HttpHeaders.ContentDisposition].orEmpty())

        val imagen = ImageIO.read(ByteArrayInputStream(r.readRawBytes()))
        assertEquals(600, imagen.width)
        assertEquals(600, imagen.height)
        assertEquals("https://virtualviking.github.io/vice-city-app/?e=${codigoQr()}", leerQr(imagen))
    }

    @Test
    fun `sin tamano usa 1024 pixeles y valida el rango`() = conApp {
        val token = loginAdmin()
        val r = cliente.get(Rutas.ESTABLECIMIENTO_QR) { bearerAuth(token) }
        assertEquals(1024, ImageIO.read(ByteArrayInputStream(r.readRawBytes())).width)

        error(cliente.get(Rutas.ESTABLECIMIENTO_QR) { bearerAuth(token); parameter("tamano", 100) }, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(cliente.get(Rutas.ESTABLECIMIENTO_QR) { bearerAuth(token); parameter("tamano", "grande") }, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
    }

    @Test
    fun `solo el ADMIN puede descargarlo`() = conApp {
        error(cliente.get(Rutas.ESTABLECIMIENTO_QR), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        val tokenDj = login("dj@vicecity.test", "clave-dj-123")
        error(cliente.get(Rutas.ESTABLECIMIENTO_QR) { bearerAuth(tokenDj) }, HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
    }

    @Test
    fun `el enlace respeta la barra final de la URL web`() {
        assertEquals("https://ejemplo.co/app/?e=abc", EnlaceQr.de("https://ejemplo.co/app/", "abc"))
        assertEquals("https://ejemplo.co/app/?e=abc", EnlaceQr.de("https://ejemplo.co/app", "abc"))
    }

    /** Decodifica el QR como lo haría la cámara de un celular. */
    private fun leerQr(imagen: BufferedImage): String {
        val pixeles = imagen.getRGB(0, 0, imagen.width, imagen.height, null, 0, imagen.width)
        val fuente = RGBLuminanceSource(imagen.width, imagen.height, pixeles)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(fuente))).text
    }
}