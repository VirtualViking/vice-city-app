package vicecity.servidor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import vicecity.compartido.Rutas
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Miniaturas servidas por el backend, con YouTube simulado y sin base de datos. */
class MiniaturasTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val pedidas = mutableListOf<String>()

    private val youtube = HttpClient(MockEngine { peticion ->
        pedidas += peticion.url.encodedPath
        if ("existe00001" in peticion.url.encodedPath) {
            respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
        } else {
            respondError(HttpStatusCode.NotFound)
        }
    })

    private val config = Configuracion(
        urlBaseDatos = null,
        origenesPermitidos = Configuracion.leerOrigenes("https://virtualviking.github.io"),
        youtubeApiKey = null,
    )

    @Test
    fun `devuelve el JPEG con cache de una semana y CORS para la web`() = testApplication {
        application { modulo(config, baseDatos = null, http = youtube) }
        val r = client.get(Rutas.miniatura("existe00001")) { header(HttpHeaders.Origin, "https://virtualviking.github.io") }

        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Image.JPEG, r.headers[HttpHeaders.ContentType]?.let(ContentType::parse))
        assertContentEquals(jpeg, r.readRawBytes())
        assertTrue("max-age=604800" in r.headers[HttpHeaders.CacheControl].orEmpty())
        assertEquals("https://virtualviking.github.io", r.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals(listOf("/vi/existe00001/mqdefault.jpg"), pedidas)
    }

    @Test
    fun `la segunda vez sale de la memoria sin volver a YouTube`() = testApplication {
        application { modulo(config, baseDatos = null, http = youtube) }
        repeat(3) { assertEquals(HttpStatusCode.OK, client.get(Rutas.miniatura("existe00001")).status) }
        assertEquals(1, pedidas.size)
    }

    @Test
    fun `un id invalido o una miniatura inexistente responden 404`() = testApplication {
        application { modulo(config, baseDatos = null, http = youtube) }
        assertEquals(HttpStatusCode.NotFound, client.get(Rutas.miniatura("no-existe01")).status)
        assertEquals(HttpStatusCode.NotFound, client.get(Rutas.miniatura("corto")).status)
        assertEquals(HttpStatusCode.NotFound, client.get(Rutas.miniatura("..%2F..%2Fetc")).status)
        assertEquals(listOf("/vi/no-existe01/mqdefault.jpg"), pedidas)
    }
}