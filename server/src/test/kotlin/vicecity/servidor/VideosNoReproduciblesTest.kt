package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.CodigosError
import vicecity.compartido.ElementoActual
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.FinReproduccion
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.NuevaListaDefecto
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.ReproduccionFallida
import vicecity.compartido.RespuestaBusqueda
import vicecity.compartido.Rutas
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.VideosNoReproducibles
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RF-25: los videos que YouTube no deja reproducir en la app se recuerdan, dejan de ofrecerse y la lista por defecto los salta. */
class VideosNoReproduciblesTest : PruebaIntegracion() {
    private val ana = UUID.randomUUID().toString()
    private val beto = UUID.randomUUID().toString()

    private fun responderSalsa() = catalogo.responder(
        "salsa",
        video("bloqueado01", "Salsa bloqueada (Video Oficial)", "Disquera VEVO"),
        video("permitido01", "Salsa permitida", "Orquesta - Topic"),
    )

    private suspend fun Contexto.buscar(q: String): Set<String> =
        cliente.get(Rutas.busqueda(codigoQr())) {
            header(HEADER_DISPOSITIVO, ana)
            parameter("q", q)
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<RespuestaBusqueda>().resultados.map { it.videoId }.toSet()

    private suspend fun Contexto.buscarPersonal(token: String, q: String): Set<String> =
        cliente.get(Rutas.BUSQUEDA_PERSONAL) {
            bearerAuth(token)
            parameter("q", q)
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<RespuestaBusqueda>().resultados.map { it.videoId }.toSet()

    private suspend fun Contexto.pedir(sesionId: String, videoId: String, dispositivo: String): HttpResponse =
        cliente.post(Rutas.solicitudes(sesionId)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            contentType(ContentType.Application.Json)
            setBody(NuevaSolicitud(videoId))
        }

    private suspend fun Contexto.iniciar(token: String): EstadoReproduccion =
        cliente.post(Rutas.REPRODUCTOR_INICIAR) { bearerAuth(token) }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.fallida(token: String, videoId: String, inicioEn: String, codigo: Int): EstadoReproduccion =
        cliente.post(Rutas.REPRODUCTOR_FALLIDA) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ReproduccionFallida(videoId, inicioEn, codigo))
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.fallida(token: String, actual: ElementoActual, codigo: Int) = fallida(token, actual.videoId, actual.inicioEn, codigo)

    private suspend fun Contexto.terminar(token: String, actual: ElementoActual): EstadoReproduccion =
        cliente.post(Rutas.REPRODUCTOR_TERMINADA) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FinReproduccion(actual.videoId, actual.inicioEn))
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    /** videoId → código de error con que se registró. */
    private fun noReproducibles(): Map<String, Int> = transaction(bd.database) {
        VideosNoReproducibles.selectAll().associate { it[VideosNoReproducibles.videoId] to it[VideosNoReproducibles.codigoError].toInt() }
    }

    @Test
    fun `un video bloqueado para incrustar se recuerda, deja de salir en las busquedas y no se puede pedir`() = conApp {
        responderSalsa()
        val token = loginAdmin()
        val sesion = abrir(token)
        assertEquals(setOf("bloqueado01", "permitido01"), buscar("salsa"))
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "bloqueado01", ana).status)
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "permitido01", ana).status)

        val inicio = iniciar(token)
        assertEquals("bloqueado01", inicio.actual!!.videoId)
        // La vista pública avisa el error 150 de YouTube: se salta, como antes, y además se recuerda
        val despues = fallida(token, inicio.actual!!, 150)
        assertEquals("permitido01", despues.actual!!.videoId)
        assertEquals(mapOf("bloqueado01" to 150), noReproducibles())

        // Ya no sale en las búsquedas, aunque la respuesta venga de la caché
        assertEquals(setOf("permitido01"), buscar("salsa"))
        assertEquals(setOf("permitido01"), buscarPersonal(token, "salsa"))

        // Si alguien lo pide igual, el servidor lo rechaza
        val e = error(pedir(sesion.id, "bloqueado01", beto), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO)
        assertEquals("Esta canción no se puede reproducir en la app", e.mensaje)
        assertEquals("NO_REPRODUCIBLE", e.detalles["motivo"])
    }

    @Test
    fun `la lista por defecto salta los videos que YouTube no deja reproducir`() = conApp {
        transaction(bd.database) {
            RepositorioCanciones.guardar(
                listOf(video("listado0001", "Clásico uno", "Clásicos - Topic"), video("listado0002", "Clásico dos", "Clásicos - Topic")),
                Instant.now(),
            )
        }
        val token = loginAdmin()
        abrir(token)
        val lista = cliente.put(Rutas.LISTA_DEFECTO) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(NuevaListaDefecto(listOf("listado0001", "listado0002")))
        }
        assertEquals(HttpStatusCode.OK, lista.status)

        var estado = iniciar(token)
        assertEquals("listado0001", estado.actual!!.videoId)
        estado = fallida(token, estado.actual!!, 101)
        assertEquals("listado0002", estado.actual!!.videoId)
        // Al dar la vuelta, la que falló ya no vuelve a sonar
        estado = terminar(token, estado.actual!!)
        assertEquals("listado0002", estado.actual!!.videoId)

        // Si ninguna se puede reproducir, queda en silencio
        estado = fallida(token, estado.actual!!, 100)
        assertNull(estado.actual)
        assertEquals(mapOf("listado0001" to 101, "listado0002" to 100), noReproducibles())
    }

    @Test
    fun `un video que no arranca sin aviso de YouTube tambien se salta y se recuerda`() = conApp {
        responderSalsa()
        val token = loginAdmin()
        val sesion = abrir(token)
        buscar("salsa")
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "bloqueado01", ana).status)
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "permitido01", ana).status)
        val inicio = iniciar(token)

        // La vista pública avisa SIN_ARRANQUE: el video no empezó y YouTube no disparó su error
        val despues = fallida(token, inicio.actual!!, ReproduccionFallida.SIN_ARRANQUE)
        assertEquals("permitido01", despues.actual!!.videoId)
        assertEquals(mapOf("bloqueado01" to ReproduccionFallida.SIN_ARRANQUE), noReproducibles())
        assertEquals(setOf("permitido01"), buscar("salsa"))
    }

    @Test
    fun `una falla pasajera o el aviso de otra cancion no se recuerdan`() = conApp {
        responderSalsa()
        val token = loginAdmin()
        val sesion = abrir(token)
        buscar("salsa")
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "bloqueado01", ana).status)
        val inicio = iniciar(token)

        // Aviso de una canción que no es la que suena (por ejemplo, de una pantalla atrasada): no cambia nada
        val ajeno = fallida(token, "permitido01", inicio.actual!!.inicioEn, 150)
        assertEquals("bloqueado01", ajeno.actual!!.videoId)

        // Error 5 (falla del navegador): se salta, pero se puede volver a intentar otro día
        val despues = fallida(token, inicio.actual!!, 5)
        assertNull(despues.actual)
        assertTrue(noReproducibles().isEmpty())
        assertEquals(setOf("bloqueado01", "permitido01"), buscar("salsa"))
    }
}