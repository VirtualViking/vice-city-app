package vicecity.servidor

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import vicecity.compartido.CodigosError
import vicecity.compartido.ErrorApi
import vicecity.compartido.EstadoBaseDatos
import vicecity.compartido.Rutas
import vicecity.compartido.Salud
import kotlin.test.Test
import kotlin.test.assertEquals

class SaludTest {
    private val config = Configuracion(
        urlBaseDatos = null,
        origenesPermitidos = Configuracion.leerOrigenes("https://virtualviking.github.io"),
        youtubeApiKey = null,
    )

    @Test
    fun `salud responde ok sin base de datos`() = testApplication {
        application { modulo(config, baseDatos = null) }
        val respuesta = client.get(Rutas.SALUD)
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val salud = Json.decodeFromString<Salud>(respuesta.bodyAsText())
        assertEquals("ok", salud.estado)
        assertEquals(EstadoBaseDatos.NO_CONFIGURADA, salud.baseDatos)
    }

    @Test
    fun `CORS permite el origen de GitHub Pages`() = testApplication {
        application { modulo(config, baseDatos = null) }
        val respuesta = client.get(Rutas.SALUD) { header(HttpHeaders.Origin, "https://virtualviking.github.io") }
        assertEquals("https://virtualviking.github.io", respuesta.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `sin base de datos las rutas que la necesitan responden 503`() = testApplication {
        application { modulo(config, baseDatos = null) }
        val respuesta = client.post(Rutas.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@b.co","password":"x"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, respuesta.status)
        assertEquals(CodigosError.BASE_DATOS_NO_DISPONIBLE, Json.decodeFromString<ErrorApi>(respuesta.bodyAsText()).codigo)
    }

    @Test
    fun `convierte la URL de Neon a JDBC`() {
        val jdbc = BaseDatos.aJdbc("postgresql://usuario:cl%40ve@ep-ejemplo.neon.tech/vicecity?sslmode=require")
        assertEquals("jdbc:postgresql://ep-ejemplo.neon.tech/vicecity?sslmode=require", jdbc.url)
        assertEquals("usuario", jdbc.usuario)
        assertEquals("cl@ve", jdbc.clave)
    }
}
