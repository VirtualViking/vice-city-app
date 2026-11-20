package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.CodigosError
import vicecity.compartido.EliminacionSolicitud
import vicecity.compartido.EstadoCola
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.FinReproduccion
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.ListaBloqueos
import vicecity.compartido.ListaDefecto
import vicecity.compartido.Modo
import vicecity.compartido.MotivoEliminacion
import vicecity.compartido.NuevaListaDefecto
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.Origen
import vicecity.compartido.PausaSolicitudes
import vicecity.compartido.ReproduccionFallida
import vicecity.compartido.RespuestaBusqueda
import vicecity.compartido.RespuestaSolicitud
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionDto
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.Solicitudes
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reproductor, lista por defecto, eliminación con bloqueo y pausa de solicitudes (RF-11, RF-12, RF-17, RF-18, RF-25). */
class ReproduccionTest : PruebaIntegracion() {
    private val ana = UUID.randomUUID().toString()
    private val beto = UUID.randomUUID().toString()

    private fun guardarCanciones() = transaction(bd.database) {
        RepositorioCanciones.guardar(
            listOf(
                video("cancion0001", "Canción uno", "Artista Uno - Topic"),
                video("cancion0002", "Canción dos", "Artista Dos - Topic"),
                video("cancion0003", "Canción tres", "Artista Tres - Topic"),
                video("listado0001", "Clásico uno", "Clásicos - Topic"),
                video("listado0002", "Clásico dos", "Clásicos - Topic"),
                video("listado0003", "Clásico tres", "Clásicos - Topic"),
                video("larguisima1", "Mix de una hora", "DJ Mix", duracionSeg = 3600),
            ),
            Instant.now(),
        )
    }

    private suspend fun Contexto.preparar(): Pair<String, SesionDto> {
        guardarCanciones()
        val token = loginAdmin()
        return token to abrir(token)
    }

    private suspend fun Contexto.estado(token: String): EstadoReproduccion =
        cliente.get(Rutas.REPRODUCTOR) { bearerAuth(token) }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.accion(token: String, ruta: String): EstadoReproduccion =
        cliente.post(ruta) { bearerAuth(token) }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.terminar(token: String, actual: EstadoReproduccion): EstadoReproduccion =
        cliente.post(Rutas.REPRODUCTOR_TERMINADA) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FinReproduccion(actual.actual!!.videoId, actual.actual!!.inicioEn))
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.pedir(sesionId: String, videoId: String, dispositivo: String): HttpResponse =
        cliente.post(Rutas.solicitudes(sesionId)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            contentType(ContentType.Application.Json)
            setBody(NuevaSolicitud(videoId))
        }

    private suspend fun Contexto.solicitudCreada(sesionId: String, videoId: String, dispositivo: String): String {
        val r = pedir(sesionId, videoId, dispositivo)
        assertEquals(HttpStatusCode.Created, r.status)
        return r.body<RespuestaSolicitud>().solicitud.solicitudId
    }

    private suspend fun Contexto.guardarLista(token: String, vararg videoIds: String): HttpResponse =
        cliente.put(Rutas.LISTA_DEFECTO) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(NuevaListaDefecto(videoIds.toList()))
        }

    private suspend fun Contexto.eliminar(token: String, solicitudId: String, bloquear: Boolean): HttpResponse =
        cliente.post(Rutas.eliminarSolicitud(solicitudId)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(EliminacionSolicitud(MotivoEliminacion.INAPROPIADO, bloquear))
        }

    private fun eventos(tipo: String) = transaction(bd.database) { Eventos.selectAll().where { Eventos.tipo eq tipo }.toList() }

    private fun estadoSolicitud(id: String) = transaction(bd.database) {
        Solicitudes.selectAll().where { Solicitudes.id eq UUID.fromString(id) }.single()[Solicitudes.estado]
    }

    @Test
    fun `iniciar pone a sonar la cancion con mas votos y al terminar sigue la siguiente`() = conApp {
        val (token, sesion) = preparar()
        val uno = solicitudCreada(sesion.id, "cancion0001", ana)
        val dos = solicitudCreada(sesion.id, "cancion0002", ana)
        assertEquals(HttpStatusCode.Created, cliente.post(Rutas.votos(dos)) { header(HEADER_DISPOSITIVO, beto) }.status)

        val inicio = accion(token, Rutas.REPRODUCTOR_INICIAR)
        assertEquals(EstadoReproductor.REPRODUCIENDO, inicio.sesion!!.estadoReproductor)
        assertEquals(Origen.SOLICITUD, inicio.actual!!.origen)
        assertEquals(dos, inicio.actual!!.solicitudId)
        assertEquals(listOf(uno), inicio.cola.map { it.solicitudId })

        val despues = terminar(token, inicio)
        assertEquals(uno, despues.actual!!.solicitudId)
        assertEquals("REPRODUCIDA", estadoSolicitud(dos))
        // Un reintento del mismo aviso no salta otra canción
        val repetido = terminar(token, inicio)
        assertEquals(uno, repetido.actual!!.solicitudId)

        // El cliente ve lo mismo en su cola
        val cola: EstadoCola = cliente.get(Rutas.cola(sesion.id)) { header(HEADER_DISPOSITIVO, ana) }.body()
        assertEquals("cancion0001", cola.actual!!.videoId)
        assertEquals(2, eventos("REPRODUCCION_INICIADA").size)
        assertEquals(1, eventos("REPRODUCCION_FINALIZADA").size)
    }

    @Test
    fun `con la cola vacia suena la lista por defecto en circulo y una solicitud entra al terminar la cancion actual`() = conApp {
        val (token, sesion) = preparar()
        assertEquals(HttpStatusCode.OK, guardarLista(token, "listado0001", "listado0002").status)

        var estado = accion(token, Rutas.REPRODUCTOR_INICIAR)
        assertEquals(Origen.LISTA_DEFECTO, estado.actual!!.origen)
        assertEquals("listado0001", estado.actual!!.videoId)
        assertEquals(Modo.LISTA_DEFECTO, estado.sesion!!.modo)
        assertEquals(2, estado.cancionesListaDefecto)

        estado = terminar(token, estado)
        assertEquals("listado0002", estado.actual!!.videoId)
        estado = terminar(token, estado)
        assertEquals("listado0001", estado.actual!!.videoId)   // vuelve a empezar

        // Llega una solicitud: no interrumpe; suena cuando termina la canción de la lista
        val pedida = solicitudCreada(sesion.id, "cancion0003", ana)
        assertEquals("listado0001", estado(token).actual!!.videoId)
        estado = terminar(token, estado)
        assertEquals(pedida, estado.actual!!.solicitudId)
        assertEquals(Modo.COLA, estado.sesion!!.modo)
        // Sin más solicitudes, vuelve a la lista donde iba
        estado = terminar(token, estado)
        assertEquals("listado0002", estado.actual!!.videoId)

        val cambios = eventos("MODO_CAMBIADO").map { it[Eventos.datos]["a"].toString().trim('"') }
        assertEquals(listOf("LISTA_DEFECTO", "COLA", "LISTA_DEFECTO"), cambios)
    }

    @Test
    fun `la rotacion de la lista sigue de una noche a la siguiente`() = conApp {
        val (token, sesion) = preparar()
        guardarLista(token, "listado0001", "listado0002", "listado0003")
        val estado = accion(token, Rutas.REPRODUCTOR_INICIAR)
        terminar(token, estado)   // suena la 2
        assertEquals(HttpStatusCode.OK, cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }.status)

        abrir(token)
        assertEquals("listado0003", accion(token, Rutas.REPRODUCTOR_INICIAR).actual!!.videoId)
    }

    @Test
    fun `sin lista ni solicitudes queda en silencio y la primera solicitud suena enseguida`() = conApp {
        val (token, sesion) = preparar()
        val estado = accion(token, Rutas.REPRODUCTOR_INICIAR)
        assertNull(estado.actual)
        assertEquals(EstadoReproductor.REPRODUCIENDO, estado.sesion!!.estadoReproductor)

        val id = solicitudCreada(sesion.id, "cancion0001", ana)
        assertEquals(id, estado(token).actual!!.solicitudId)
        assertEquals("REPRODUCIENDO", estadoSolicitud(id))
    }

    @Test
    fun `pausar reanudar y saltar`() = conApp {
        val (token, sesion) = preparar()
        solicitudCreada(sesion.id, "cancion0001", ana)
        val segunda = solicitudCreada(sesion.id, "cancion0002", ana)
        accion(token, Rutas.REPRODUCTOR_INICIAR)

        assertEquals(EstadoReproductor.PAUSADO, accion(token, Rutas.REPRODUCTOR_PAUSAR).sesion!!.estadoReproductor)
        accion(token, Rutas.REPRODUCTOR_PAUSAR)   // pausar dos veces no registra otro evento
        assertEquals(EstadoReproductor.REPRODUCIENDO, accion(token, Rutas.REPRODUCTOR_REANUDAR).sesion!!.estadoReproductor)
        assertEquals(1, eventos("REPRODUCTOR_PAUSADO").size)
        assertEquals(1, eventos("REPRODUCTOR_REANUDADO").size)

        val saltada = accion(token, Rutas.REPRODUCTOR_SALTAR)
        assertEquals(segunda, saltada.actual!!.solicitudId)
        val fin = eventos("REPRODUCCION_FINALIZADA").single()[Eventos.datos]
        assertEquals("true", fin["saltada"].toString())
    }

    @Test
    fun `un video que no carga queda FALLIDA y suena el siguiente`() = conApp {
        val (token, sesion) = preparar()
        val uno = solicitudCreada(sesion.id, "cancion0001", ana)
        val dos = solicitudCreada(sesion.id, "cancion0002", ana)
        val estado = accion(token, Rutas.REPRODUCTOR_INICIAR)

        val r = cliente.post(Rutas.REPRODUCTOR_FALLIDA) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ReproduccionFallida(estado.actual!!.videoId, estado.actual!!.inicioEn, 150))
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(dos, r.body<EstadoReproduccion>().actual!!.solicitudId)
        assertEquals("FALLIDA", estadoSolicitud(uno))
        assertEquals("YOUTUBE_150", eventos("REPRODUCCION_FALLIDA").single()[Eventos.datos]["motivo"].toString().trim('"'))
    }

    @Test
    fun `eliminar con bloqueo saca la cancion y no se puede volver a pedir ni buscar por 24 horas`() = conApp {
        val (token, sesion) = preparar()
        val sonando = solicitudCreada(sesion.id, "cancion0001", ana)
        val enCola = solicitudCreada(sesion.id, "cancion0002", ana)
        solicitudCreada(sesion.id, "cancion0003", beto)
        accion(token, Rutas.REPRODUCTOR_INICIAR)

        // De la cola
        val r = eliminar(token, enCola, bloquear = true)
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(listOf("cancion0003"), r.body<EstadoReproduccion>().cola.map { it.videoId })
        assertEquals("ELIMINADA", estadoSolicitud(enCola))
        val bloqueo = cliente.get(Rutas.BLOQUEOS) { bearerAuth(token) }.body<ListaBloqueos>().bloqueos.single()
        assertEquals("cancion0002", bloqueo.videoId)
        assertTrue(Instant.parse(bloqueo.hasta) > Instant.now().plusSeconds(23 * 3600))
        val e = error(pedir(sesion.id, "cancion0002", beto), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_BLOQUEADO)
        assertEquals(bloqueo.hasta, e.detalles["hasta"])

        // Tampoco aparece en las búsquedas
        catalogo.responder("canción", video("cancion0002", "Canción dos", "Artista Dos - Topic"), video("cancion0003", "Canción tres", "Artista Tres - Topic"))
        val busqueda: RespuestaBusqueda = cliente.get(Rutas.busqueda(codigoQr())) { header(HEADER_DISPOSITIVO, beto); parameter("q", "canción") }.body()
        assertEquals(listOf("cancion0003"), busqueda.resultados.map { it.videoId })

        // Lo que suena: sale y suena la siguiente, sin bloqueo
        val despues = eliminar(token, sonando, bloquear = false).body<EstadoReproduccion>()
        assertEquals("cancion0003", despues.actual!!.videoId)
        assertEquals("ELIMINADA", estadoSolicitud(sonando))
        assertEquals(2, eventos("SOLICITUD_ELIMINADA").size)
        error(eliminar(token, sonando, bloquear = false), HttpStatusCode.Conflict, CodigosError.SOLICITUD_NO_ACTIVA)

        // Al desbloquear se puede volver a pedir
        assertTrue(cliente.delete(Rutas.bloqueo("cancion0002")) { bearerAuth(token) }.body<ListaBloqueos>().bloqueos.isEmpty())
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "cancion0002", beto).status)
        error(cliente.delete(Rutas.bloqueo("cancion0002")) { bearerAuth(token) }, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
    }

    @Test
    fun `la lista por defecto solo acepta canciones validas y se puede armar buscando`() = conApp {
        val (token, _) = preparar()
        error(guardarLista(token, "noBuscado01"), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO)
        error(guardarLista(token, "larguisima1"), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO)
        error(guardarLista(token, "listado0001", "listado0001"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(guardarLista(token, "corto"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)

        catalogo.responder("salsa", video("salsa000001", "Salsa uno", "Orquesta - Topic"))
        val busqueda: RespuestaBusqueda = cliente.get(Rutas.BUSQUEDA_PERSONAL) { bearerAuth(token); parameter("q", "salsa") }.body()
        assertEquals(listOf("salsa000001"), busqueda.resultados.map { it.videoId })

        val lista = guardarLista(token, "salsa000001", "listado0002").body<ListaDefecto>()
        assertEquals(listOf(1 to "salsa000001", 2 to "listado0002"), lista.canciones.map { it.posicion to it.videoId })
        assertEquals(lista, cliente.get(Rutas.LISTA_DEFECTO) { bearerAuth(token) }.body<ListaDefecto>())
        // Vaciarla también es válido
        assertTrue(guardarLista(token).body<ListaDefecto>().canciones.isEmpty())
    }

    @Test
    fun `con las solicitudes pausadas no se puede pedir ni votar y la musica sigue`() = conApp {
        val (token, sesion) = preparar()
        val id = solicitudCreada(sesion.id, "cancion0001", ana)
        accion(token, Rutas.REPRODUCTOR_INICIAR)

        val pausa = cliente.put(Rutas.pausaSolicitudes(sesion.id)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PausaSolicitudes(true))
        }
        assertEquals(HttpStatusCode.OK, pausa.status)
        assertTrue(pausa.body<SesionDto>().solicitudesPausadas)

        error(pedir(sesion.id, "cancion0002", beto), HttpStatusCode.Conflict, CodigosError.SOLICITUDES_PAUSADAS)
        val cola: EstadoCola = cliente.get(Rutas.cola(sesion.id)) { header(HEADER_DISPOSITIVO, ana) }.body()
        assertTrue(cola.solicitudesPausadas)
        assertEquals(EstadoReproductor.REPRODUCIENDO, estado(token).sesion!!.estadoReproductor)
        assertEquals("SOLICITUDES_PAUSADAS", eventos("SOLICITUD_RECHAZADA").single()[Eventos.datos]["motivo"].toString().trim('"'))

        cliente.put(Rutas.pausaSolicitudes(sesion.id)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PausaSolicitudes(false))
        }
        assertEquals(HttpStatusCode.Created, pedir(sesion.id, "cancion0002", beto).status)
        assertNotNull(id)
    }

    @Test
    fun `el reproductor necesita token y una sesion abierta`() = conApp {
        guardarCanciones()
        val token = loginAdmin()
        error(cliente.get(Rutas.REPRODUCTOR), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
        val sinSesion = estado(token)
        assertNull(sinSesion.sesion)
        assertEquals("Bar de pruebas", sinSesion.establecimiento)
        error(cliente.post(Rutas.REPRODUCTOR_INICIAR) { bearerAuth(token) }, HttpStatusCode.Conflict, CodigosError.SIN_SESION_ABIERTA)

        // El DJ también puede operar el reproductor
        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        val dj = login("dj@vicecity.test", "clave-dj-123")
        abrir(dj)
        assertFalse(accion(dj, Rutas.REPRODUCTOR_INICIAR).sesion!!.solicitudesPausadas)
    }
}