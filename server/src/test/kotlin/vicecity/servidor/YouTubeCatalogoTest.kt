package vicecity.servidor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import vicecity.servidor.integraciones.CatalogoNoDisponible
import vicecity.servidor.integraciones.YouTubeCatalogo
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Adaptador de YouTube contra respuestas simuladas: formato de las peticiones, lectura de la respuesta y filtros. */
class YouTubeCatalogoTest {
    private val peticiones = mutableListOf<HttpRequestData>()

    private fun catalogo(manejador: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        YouTubeCatalogo(
            apiKey = "clave-secreta",
            http = HttpClient(MockEngine { peticion -> peticiones += peticion; manejador(peticion) }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
        )

    private fun MockRequestHandleScope.json(cuerpo: String) =
        respond(cuerpo, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `devuelve solo musica reproducible, en el orden de relevancia`() = runBlocking {
        val resultados = catalogo { peticion ->
            if (peticion.url.encodedPath.endsWith("/search")) json(BUSQUEDA) else json(VIDEOS)
        }.buscar("karol g", 20)

        assertEquals(listOf("valido00002", "valido00001"), resultados.map { it.videoId })
        val provenza = resultados.first()
        assertEquals("KAROL G - PROVENZA (Official Video)", provenza.titulo)
        assertEquals("KarolGVEVO", provenza.canal)
        assertEquals(211, provenza.duracionSeg)
        assertEquals("https://i.ytimg.com/vi/valido00002/mqdefault.jpg", provenza.miniaturaUrl)
        assertEquals(listOf("Reggaeton", "Pop music"), provenza.categorias)
    }

    @Test
    fun `envia la clave en un header y nunca en la URL`() = runBlocking {
        catalogo { peticion -> if (peticion.url.encodedPath.endsWith("/search")) json(BUSQUEDA) else json(VIDEOS) }.buscar("karol g", 20)

        val busqueda = peticiones.first()
        assertEquals("clave-secreta", busqueda.headers["x-goog-api-key"])
        assertFalse("clave-secreta" in busqueda.url.toString())
        assertEquals("10", busqueda.url.parameters["videoCategoryId"])
        assertEquals("true", busqueda.url.parameters["videoEmbeddable"])
        assertEquals("CO", busqueda.url.parameters["regionCode"])
        assertEquals("karol g", busqueda.url.parameters["q"])
        assertTrue(peticiones.none { "clave-secreta" in it.url.toString() })
    }

    @Test
    fun `sin resultados no consulta los detalles`() = runBlocking {
        val resultados = catalogo { json("""{"items":[]}""") }.buscar("nada", 20)
        assertTrue(resultados.isEmpty())
        assertEquals(1, peticiones.size)
    }

    @Test
    fun `cuota agotada o falla de red se informan como catalogo no disponible`() {
        val cuota = assertFailsWith<CatalogoNoDisponible> {
            runBlocking {
                catalogo { respondError(HttpStatusCode.Forbidden, CUOTA_AGOTADA, headersOf(HttpHeaders.ContentType, "application/json")) }
                    .buscar("karol g", 20)
            }
        }
        assertTrue("quotaExceeded" in cuota.message!!)

        assertFailsWith<CatalogoNoDisponible> {
            runBlocking { catalogo { throw IOException("sin red") }.buscar("karol g", 20) }
        }
    }

    @Test
    fun `convierte la duracion y los temas de YouTube`() {
        assertEquals(225, YouTubeCatalogo.duracionEnSegundos("PT3M45S"))
        assertEquals(3600, YouTubeCatalogo.duracionEnSegundos("PT1H"))
        assertEquals(0, YouTubeCatalogo.duracionEnSegundos("P0D"))
        assertEquals(0, YouTubeCatalogo.duracionEnSegundos("no-es-duracion"))
        assertEquals("Pop music", YouTubeCatalogo.nombreDeTema("https://en.wikipedia.org/wiki/Pop_music"))
        assertEquals("Música latina", YouTubeCatalogo.nombreDeTema("https://en.wikipedia.org/wiki/M%C3%BAsica_latina"))
        assertEquals(null, YouTubeCatalogo.nombreDeTema("https://en.wikipedia.org/wiki/Music"))
    }

    private companion object {
        val BUSQUEDA = """
            {"items":[
              {"id":{"kind":"youtube#video","videoId":"valido00002"}},
              {"id":{"kind":"youtube#video","videoId":"noincrusta1"}},
              {"id":{"kind":"youtube#video","videoId":"envivo00001"}},
              {"id":{"kind":"youtube#video","videoId":"nomusica001"}},
              {"id":{"kind":"youtube#video","videoId":"bloqueadoco"}},
              {"id":{"kind":"youtube#video","videoId":"valido00001"}}
            ]}
        """.trimIndent()

        fun video(id: String, categoria: String = "10", incrustable: Boolean = true, enVivo: String = "none", bloqueados: String = "[]") = """
            {"id":"$id",
             "snippet":{"title":"${if (id == "valido00002") "KAROL G - PROVENZA (Official Video)" else "Canción $id"}",
                        "channelTitle":"${if (id == "valido00002") "KarolGVEVO" else "Canal"}",
                        "categoryId":"$categoria","liveBroadcastContent":"$enVivo",
                        "thumbnails":{"default":{"url":"https://i.ytimg.com/vi/$id/default.jpg"},"medium":{"url":"https://i.ytimg.com/vi/$id/mqdefault.jpg"}}},
             "contentDetails":{"duration":"PT3M31S","regionRestriction":{"blocked":$bloqueados}},
             "status":{"embeddable":$incrustable,"privacyStatus":"public"},
             "topicDetails":{"topicCategories":["https://en.wikipedia.org/wiki/Music","https://en.wikipedia.org/wiki/Reggaeton","https://en.wikipedia.org/wiki/Pop_music"]}}
        """.trimIndent()

        val VIDEOS = """
            {"items":[
              ${video("valido00001")},
              ${video("noincrusta1", incrustable = false)},
              ${video("envivo00001", enVivo = "live")},
              ${video("nomusica001", categoria = "22")},
              ${video("bloqueadoco", bloqueados = "[\"CO\",\"US\"]")},
              ${video("valido00002")}
            ]}
        """.trimIndent()

        val CUOTA_AGOTADA = """{"error":{"code":403,"message":"quota","errors":[{"reason":"quotaExceeded"}]}}"""
    }
}