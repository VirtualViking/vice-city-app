package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.CodigosError
import vicecity.compartido.FuenteBusqueda
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.RespuestaBusqueda
import vicecity.compartido.Rutas
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.CacheBusquedas
import vicecity.servidor.datos.CancionCategorias
import vicecity.servidor.datos.Canciones
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.TerminosBuscados
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Búsqueda del cliente con caché (RNF-14), límites de cupo y respaldo local, con YouTube simulado. */
class BusquedaTest : PruebaIntegracion() {
    override val config = super.config.copy(
        reglasBusqueda = ReglasBusqueda(maxNuevasPorDispositivo = 2, maxNuevasDiarias = 3),
    )
    private val dispositivo = UUID.randomUUID().toString()

    private suspend fun Contexto.buscar(q: String, dispositivoId: String = dispositivo): HttpResponse =
        cliente.get(Rutas.busqueda(codigoQr())) {
            header(HEADER_DISPOSITIVO, dispositivoId)
            parameter("q", q)
        }

    private fun responderKarolG() = catalogo.responder(
        "karol g",
        video("provenza001", "KAROL G - PROVENZA (Official Video)", "KarolGVEVO", categorias = listOf("Reggaeton")),
        video("bichota0001", "Bichota", "Karol G - Topic"),
        video("larguisimo1", "KAROL G - Mix de una hora", "KarolGVEVO", duracionSeg = 3600),
    )

    @Test
    fun `la primera busqueda va a YouTube y devuelve titulo, artista, duracion y miniatura`() = conApp {
        responderKarolG()
        val r = buscar("Karol G")
        assertEquals(HttpStatusCode.OK, r.status)
        val respuesta = r.body<RespuestaBusqueda>()

        assertEquals(FuenteBusqueda.YOUTUBE, respuesta.fuente)
        // El video de una hora supera la duración máxima del establecimiento (480 s) y no se muestra
        assertEquals(listOf("provenza001", "bichota0001"), respuesta.resultados.map { it.videoId })
        val provenza = respuesta.resultados.first()
        assertEquals("KAROL G", provenza.artista)
        assertEquals("KarolGVEVO", provenza.canal)
        assertEquals(210, provenza.duracionSeg)
        assertEquals("https://i.ytimg.com/vi/provenza001/mqdefault.jpg", provenza.miniaturaUrl)
        assertEquals("Karol G", respuesta.resultados[1].artista)

        transaction(bd.database) {
            assertEquals(3, Canciones.selectAll().count())
            assertEquals("Reggaeton", CancionCategorias.selectAll().single()[CancionCategorias.categoria])
            val evento = Eventos.selectAll().where { Eventos.tipo eq "BUSQUEDA_REALIZADA" }.single()
            assertEquals("\"YOUTUBE\"", evento[Eventos.datos]["fuente"].toString())
            assertEquals("2", evento[Eventos.datos]["resultados"].toString())
            assertNotNull(evento[Eventos.dispositivoHash])
        }
    }

    @Test
    fun `el mismo termino escrito distinto sale de la cache sin gastar cupo`() = conApp {
        responderKarolG()
        buscar("Karol G")
        val segunda = buscar("  KÁROL   g ").body<RespuestaBusqueda>()

        assertEquals(FuenteBusqueda.CACHE, segunda.fuente)
        assertEquals(2, segunda.resultados.size)
        assertEquals(1, catalogo.consultas)
        transaction(bd.database) {
            val termino = TerminosBuscados.selectAll().single()
            assertEquals("karol g", termino[TerminosBuscados.termino])
            assertEquals(2, termino[TerminosBuscados.veces])
            assertEquals("KÁROL g", termino[TerminosBuscados.texto])
        }
    }

    @Test
    fun `una cache vencida vuelve a consultar YouTube`() = conApp {
        responderKarolG()
        buscar("karol g")
        transaction(bd.database) { CacheBusquedas.update { it[expiraEn] = Instant.now().minusSeconds(60) } }

        assertEquals(FuenteBusqueda.YOUTUBE, buscar("karol g").body<RespuestaBusqueda>().fuente)
        assertEquals(2, catalogo.consultas)
    }

    @Test
    fun `al pasar el limite por dispositivo usa las canciones guardadas o responde 429`() = conApp {
        responderKarolG()
        buscar("karol g")
        buscar("shakira")                    // segunda búsqueda nueva: llega al límite de 2

        // Hay canciones guardadas que coinciden: responde con ellas sin llamar a YouTube
        val local = buscar("bichota").body<RespuestaBusqueda>()
        assertEquals(FuenteBusqueda.LOCAL, local.fuente)
        assertEquals(listOf("bichota0001"), local.resultados.map { it.videoId })
        assertEquals(2, catalogo.consultas)

        // Nada guardado coincide: 429 con la hora en que puede volver a intentar
        val e = error(buscar("metallica"), HttpStatusCode.TooManyRequests, CodigosError.LIMITE_BUSQUEDAS)
        assertTrue(Instant.parse(e.detalles["reintentarEn"]).isAfter(Instant.now()))

        // Otro dispositivo no está limitado
        assertEquals(HttpStatusCode.OK, buscar("metallica", UUID.randomUUID().toString()).status)
    }

    @Test
    fun `al pasar el tope diario responde 503 si no hay canciones guardadas`() = conApp {
        repeat(3) { buscar("busqueda $it", UUID.randomUUID().toString()) }
        error(buscar("una mas", UUID.randomUUID().toString()), HttpStatusCode.ServiceUnavailable, CodigosError.YOUTUBE_NO_DISPONIBLE)
        assertEquals(3, catalogo.consultas)
    }

    @Test
    fun `si YouTube falla usa las canciones guardadas y si no hay responde 503`() = conApp {
        responderKarolG()
        buscar("karol g")
        catalogo.disponible = false

        val local = buscar("provenza").body<RespuestaBusqueda>()
        assertEquals(FuenteBusqueda.LOCAL, local.fuente)
        assertEquals(listOf("provenza001"), local.resultados.map { it.videoId })

        error(buscar("metallica"), HttpStatusCode.ServiceUnavailable, CodigosError.YOUTUBE_NO_DISPONIBLE)
    }

    @Test
    fun `valida el texto, el dispositivo y el establecimiento`() = conApp {
        error(buscar("a"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(buscar("x".repeat(101)), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(cliente.get(Rutas.busqueda(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(cliente.get(Rutas.busqueda(codigoQr())) { parameter("q", "karol") }, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        val r = cliente.get(Rutas.busqueda("noexiste")) { header(HEADER_DISPOSITIVO, dispositivo); parameter("q", "karol") }
        error(r, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        assertEquals(0, catalogo.consultas)
    }

    @Test
    fun `con sesion abierta la busqueda queda asociada a la sesion`() = conApp {
        val sesion = abrir(loginAdmin())
        responderKarolG()
        buscar("karol g")
        transaction(bd.database) {
            val evento = Eventos.selectAll().where { Eventos.tipo eq "BUSQUEDA_REALIZADA" }.single()
            assertEquals(sesion.id, evento[Eventos.sesionId].toString())
        }
    }
}