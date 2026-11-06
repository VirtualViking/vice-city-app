package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import vicecity.compartido.CodigosError
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.RespuestaSugerencias
import vicecity.compartido.Rutas
import vicecity.compartido.Sugerencia
import vicecity.compartido.TipoSugerencia
import vicecity.servidor.CatalogoFalso.Companion.video
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Autocompletado con datos propios: búsquedas anteriores, artistas y canciones ya vistas. */
class SugerenciasTest : PruebaIntegracion() {
    private val dispositivo = UUID.randomUUID().toString()

    private suspend fun Contexto.buscar(q: String) =
        cliente.get(Rutas.busqueda(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo); parameter("q", q) }

    private suspend fun Contexto.sugerir(q: String): List<Sugerencia> {
        val r = cliente.get(Rutas.sugerencias(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo); parameter("q", q) }
        assertEquals(HttpStatusCode.OK, r.status)
        return r.body<RespuestaSugerencias>().sugerencias
    }

    private fun prepararCatalogo() {
        catalogo.responder(
            "bad bunny",
            video("titime00001", "Tití Me Preguntó", "Bad Bunny - Topic"),
            video("moscow00001", "Bad Bunny - Moscow Mule (Video Oficial)", "BadBunnyVEVO"),
        )
        catalogo.responder("baladas", video("balada00001", "Baladas Románticas Mix", "Canal Baladas"))
    }

    @Test
    fun `sugiere busquedas anteriores, artistas y canciones sin llamar a YouTube`() = conApp {
        prepararCatalogo()
        buscar("Bad Bunny")
        buscar("bad bunny")
        buscar("Baladas")
        val consultasAntes = catalogo.consultas

        val sugerencias = sugerir("ba")
        assertEquals(consultasAntes, catalogo.consultas)
        // Primero lo más buscado; "Bad Bunny" no se repite como artista porque ya está como búsqueda
        assertEquals(Sugerencia("bad bunny", TipoSugerencia.BUSQUEDA), sugerencias[0])
        assertEquals(Sugerencia("Baladas", TipoSugerencia.BUSQUEDA), sugerencias[1])
        assertTrue(Sugerencia("Baladas Románticas Mix", TipoSugerencia.CANCION) in sugerencias)
        assertEquals(sugerencias.size, sugerencias.map { it.texto.lowercase() }.distinct().size)
    }

    @Test
    fun `encuentra por cualquier palabra y sin importar tildes`() = conApp {
        prepararCatalogo()
        buscar("bad bunny")

        assertEquals(listOf(Sugerencia("Tití Me Preguntó", TipoSugerencia.CANCION)), sugerir("PREGUNTO"))
        assertTrue(Sugerencia("Tití Me Preguntó", TipoSugerencia.CANCION) in sugerir("titi"))
        assertTrue(Sugerencia("Bad Bunny - Moscow Mule (Video Oficial)", TipoSugerencia.CANCION) in sugerir("mosc"))
    }

    @Test
    fun `con menos de dos letras o sin coincidencias no sugiere nada`() = conApp {
        prepararCatalogo()
        buscar("bad bunny")
        assertTrue(sugerir("b").isEmpty())
        assertTrue(sugerir("zz").isEmpty())
        assertTrue(sugerir("50%").isEmpty())    // los comodines de LIKE se buscan como texto
    }

    @Test
    fun `valida el dispositivo y el establecimiento`() = conApp {
        val sinDispositivo = cliente.get(Rutas.sugerencias(codigoQr())) { parameter("q", "ba") }
        error(sinDispositivo, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        val noExiste = cliente.get(Rutas.sugerencias("noexiste")) { header(HEADER_DISPOSITIVO, dispositivo); parameter("q", "ba") }
        error(noExiste, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
    }
}