package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.CodigosError
import vicecity.compartido.EstadoCola
import vicecity.compartido.EstadoSolicitud
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.HEADER_IDEMPOTENCIA
import vicecity.compartido.MiActividad
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.RespuestaSolicitud
import vicecity.compartido.RespuestaVoto
import vicecity.compartido.ResultadoSolicitud
import vicecity.compartido.Rutas
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.datos.Votos
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Solicitudes, votos y cola del cliente (RF-05 a RF-09, RF-26, RF-27, RNF-18). */
class ColaTest : PruebaIntegracion() {
    private val ana = UUID.randomUUID().toString()
    private val beto = UUID.randomUUID().toString()
    private val caro = UUID.randomUUID().toString()

    /** Canciones que ya salieron en búsquedas: solo esas se pueden pedir. */
    private fun guardarCanciones() = transaction(bd.database) {
        RepositorioCanciones.guardar(
            listOf(
                video("cancion0001", "Canción uno", "Artista Uno - Topic"),
                video("cancion0002", "Canción dos", "Artista Dos - Topic"),
                video("cancion0003", "Canción tres", "Artista Tres - Topic"),
                video("cancion0004", "Canción cuatro", "Artista Cuatro - Topic"),
                video("cancion0005", "Canción cinco", "Artista Cinco - Topic"),
                video("cancion0006", "Canción seis", "Artista Seis - Topic"),
                video("larguisima1", "Mix de una hora", "DJ Mix", duracionSeg = 3600),
            ),
            Instant.now(),
        )
    }

    private suspend fun Contexto.preparar(): String {
        guardarCanciones()
        return abrir(loginAdmin()).id
    }

    private suspend fun Contexto.pedir(sesionId: String, videoId: String, dispositivo: String, clave: String? = null): HttpResponse =
        cliente.post(Rutas.solicitudes(sesionId)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            if (clave != null) header(HEADER_IDEMPOTENCIA, clave)
            contentType(ContentType.Application.Json)
            setBody(NuevaSolicitud(videoId))
        }

    private suspend fun Contexto.votar(solicitudId: String, dispositivo: String, clave: String? = null): HttpResponse =
        cliente.post(Rutas.votos(solicitudId)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            if (clave != null) header(HEADER_IDEMPOTENCIA, clave)
        }

    private suspend fun Contexto.cola(sesionId: String): EstadoCola =
        cliente.get(Rutas.cola(sesionId)) { header(HEADER_DISPOSITIVO, ana) }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.actividad(sesionId: String, dispositivo: String): MiActividad =
        cliente.get(Rutas.miActividad(sesionId)) { header(HEADER_DISPOSITIVO, dispositivo) }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.solicitudCreada(sesionId: String, videoId: String, dispositivo: String): String {
        val r = pedir(sesionId, videoId, dispositivo)
        assertEquals(HttpStatusCode.Created, r.status)
        return r.body<RespuestaSolicitud>().solicitud.solicitudId
    }

    private fun eventos(tipo: String) = transaction(bd.database) { Eventos.selectAll().where { Eventos.tipo eq tipo }.toList() }

    @Test
    fun `pedir una cancion la pone en la cola con el voto propio`() = conApp {
        val sesion = preparar()
        val r = pedir(sesion, "cancion0001", ana)
        assertEquals(HttpStatusCode.Created, r.status)
        val respuesta = r.body<RespuestaSolicitud>()
        assertEquals(ResultadoSolicitud.SOLICITUD_CREADA, respuesta.resultado)
        assertEquals(1, respuesta.solicitud.votos)
        assertEquals("Canción uno", respuesta.solicitud.titulo)
        assertEquals("Artista Uno", respuesta.solicitud.artista)

        val cola = cola(sesion)
        assertEquals(listOf("cancion0001"), cola.cola.map { it.videoId })
        assertNull(cola.actual)

        val actividad = actividad(sesion, ana)
        assertEquals(EstadoSolicitud.EN_COLA, actividad.solicitudes.single().estado)
        assertEquals(1, actividad.solicitudes.single().posicion)
        assertEquals(listOf(respuesta.solicitud.solicitudId), actividad.votos)
        assertEquals(2, actividad.solicitudesRestantes)
        // El voto de su propia solicitud no cuenta para el límite
        assertEquals(3, actividad.votosRestantes)

        assertEquals(1, eventos("SOLICITUD_CREADA").size)
        assertEquals(1, eventos("VOTO_REGISTRADO").size)
    }

    @Test
    fun `pedir una cancion que ya esta en la cola registra un voto que cuenta para el limite`() = conApp {
        val sesion = preparar()
        val id = solicitudCreada(sesion, "cancion0001", ana)

        val r = pedir(sesion, "cancion0001", beto)
        assertEquals(HttpStatusCode.OK, r.status)
        val respuesta = r.body<RespuestaSolicitud>()
        assertEquals(ResultadoSolicitud.VOTO_REGISTRADO, respuesta.resultado)
        assertEquals(id, respuesta.solicitud.solicitudId)
        assertEquals(2, respuesta.solicitud.votos)

        val actividad = actividad(sesion, beto)
        assertTrue(actividad.solicitudes.isEmpty())
        assertEquals(listOf(id), actividad.votos)
        assertEquals(2, actividad.votosRestantes)
        assertEquals(3, actividad.solicitudesRestantes)

        // Quien la pidió no puede volver a pedirla: ya tiene su voto
        error(pedir(sesion, "cancion0001", ana), HttpStatusCode.Conflict, CodigosError.YA_VOTADO)
    }

    @Test
    fun `la cola se ordena por votos y con empate por antiguedad`() = conApp {
        val sesion = preparar()
        val uno = solicitudCreada(sesion, "cancion0001", ana)
        val dos = solicitudCreada(sesion, "cancion0002", ana)
        val tres = solicitudCreada(sesion, "cancion0003", beto)
        assertEquals(listOf(uno, dos, tres), cola(sesion).cola.map { it.solicitudId })

        assertEquals(HttpStatusCode.Created, votar(tres, caro).status)
        assertEquals(HttpStatusCode.Created, votar(dos, caro).status)
        assertEquals(HttpStatusCode.Created, votar(tres, ana).status)

        val cola = cola(sesion).cola
        assertEquals(listOf(tres, dos, uno), cola.map { it.solicitudId })
        assertEquals(listOf(3, 2, 1), cola.map { it.votos })
        assertEquals(listOf(2, 3), actividad(sesion, ana).solicitudes.map { it.posicion }.sortedBy { it })
    }

    @Test
    fun `cada dispositivo tiene 3 votos por sesion para canciones de otros`() = conApp {
        val sesion = preparar()
        val deAna = listOf("cancion0001", "cancion0002", "cancion0003").map { solicitudCreada(sesion, it, ana) }
        val deCaro = solicitudCreada(sesion, "cancion0004", caro)
        val propia = solicitudCreada(sesion, "cancion0005", beto)

        error(votar(propia, beto), HttpStatusCode.Conflict, CodigosError.YA_VOTADO)
        deAna.forEach { id ->
            val r = votar(id, beto)
            assertEquals(HttpStatusCode.Created, r.status)
            assertEquals(2, r.body<RespuestaVoto>().votos)
        }
        error(votar(deAna.first(), beto), HttpStatusCode.Conflict, CodigosError.YA_VOTADO)
        val e = error(votar(deCaro, beto), HttpStatusCode.Conflict, CodigosError.LIMITE_VOTOS)
        assertEquals("3", e.detalles["limite"])

        val actividad = actividad(sesion, beto)
        assertEquals(0, actividad.votosRestantes)
        assertEquals((deAna + propia).toSet(), actividad.votos.toSet())
        // Sin votos tampoco puede sumar uno pidiendo una canción que ya está en la cola
        error(pedir(sesion, "cancion0004", beto), HttpStatusCode.Conflict, CodigosError.LIMITE_VOTOS)
        assertEquals(1, cola(sesion).cola.single { it.solicitudId == deCaro }.votos)
    }

    @Test
    fun `cada dispositivo pide hasta 3 canciones cada 15 minutos`() = conApp {
        val sesion = preparar()
        listOf("cancion0001", "cancion0002", "cancion0003").forEach { solicitudCreada(sesion, it, ana) }

        val e = error(pedir(sesion, "cancion0004", ana), HttpStatusCode.TooManyRequests, CodigosError.LIMITE_SOLICITUDES)
        val reintentarEn = Instant.parse(e.detalles["reintentarEn"])
        assertTrue(reintentarEn > Instant.now().plus(Duration.ofMinutes(14)))

        val actividad = actividad(sesion, ana)
        assertEquals(0, actividad.solicitudesRestantes)
        assertEquals(reintentarEn, Instant.parse(actividad.proximaSolicitudEn))
        assertEquals("LIMITE_SOLICITUDES", eventos("SOLICITUD_RECHAZADA").single()[Eventos.datos]["motivo"].toString().trim('"'))

        // Pasada la ventana puede volver a pedir
        transaction(bd.database) {
            Solicitudes.update { it[solicitadaEn] = Instant.now().minus(Duration.ofMinutes(16)) }
        }
        assertEquals(HttpStatusCode.Created, pedir(sesion, "cancion0004", ana).status)
        // Otro dispositivo no se ve afectado
        assertEquals(HttpStatusCode.Created, pedir(sesion, "cancion0005", beto).status)
    }

    @Test
    fun `una cancion que sono hace poco no se puede pedir otra vez`() = conApp {
        val sesion = preparar()
        val id = solicitudCreada(sesion, "cancion0001", ana)
        transaction(bd.database) {
            Solicitudes.update({ Solicitudes.id eq UUID.fromString(id) }) {
                it[estado] = "REPRODUCIDA"
                it[finReproduccion] = Instant.now().minus(Duration.ofMinutes(10))
            }
        }
        val e = error(pedir(sesion, "cancion0001", beto), HttpStatusCode.Conflict, CodigosError.CANCION_RECIENTE)
        assertTrue(Instant.parse(e.detalles["disponibleEn"]) > Instant.now().plus(Duration.ofMinutes(49)))
        // Ya no está en la cola: votarla tampoco se puede
        error(votar(id, beto), HttpStatusCode.Conflict, CodigosError.SOLICITUD_NO_ACTIVA)

        transaction(bd.database) {
            Solicitudes.update { it[finReproduccion] = Instant.now().minus(Duration.ofMinutes(61)) }
        }
        assertEquals(HttpStatusCode.Created, pedir(sesion, "cancion0001", beto).status)
    }

    @Test
    fun `solo se pueden pedir videos validos para el establecimiento`() = conApp {
        val sesion = preparar()
        val e = error(pedir(sesion, "noBuscado01", ana), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO)
        assertEquals("NO_DISPONIBLE", e.detalles["motivo"])
        val larga = error(pedir(sesion, "larguisima1", ana), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO)
        assertEquals("DURACION_EXCEDIDA", larga.detalles["motivo"])
        error(pedir(sesion, "corto", ana), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        // Los rechazos de reglas no gastan solicitudes
        assertEquals(3, actividad(sesion, ana).solicitudesRestantes)
    }

    @Test
    fun `reintentar con la misma Idempotency-Key no duplica nada`() = conApp {
        val sesion = preparar()
        val clave = UUID.randomUUID().toString()
        val primera = pedir(sesion, "cancion0001", ana, clave)
        val segunda = pedir(sesion, "cancion0001", ana, clave)
        assertEquals(HttpStatusCode.Created, primera.status)
        assertEquals(HttpStatusCode.Created, segunda.status)
        assertEquals(primera.body<RespuestaSolicitud>().solicitud.solicitudId, segunda.body<RespuestaSolicitud>().solicitud.solicitudId)
        assertEquals(1, eventos("SOLICITUD_CREADA").size)

        val id = primera.body<RespuestaSolicitud>().solicitud.solicitudId
        val claveVoto = UUID.randomUUID().toString()
        assertEquals(2, votar(id, beto, claveVoto).body<RespuestaVoto>().votos)
        assertEquals(HttpStatusCode.Created, votar(id, beto, claveVoto).status)
        assertEquals(2, transaction(bd.database) { Votos.selectAll().count() }.toInt())
        assertEquals(2, actividad(sesion, beto).votosRestantes)
    }

    @Test
    fun `peticiones simultaneas respetan los limites`() = conApp {
        val sesion = preparar()
        // El mismo celular toca cinco canciones a la vez: solo entran tres
        val estados = coroutineScope {
            listOf("cancion0001", "cancion0002", "cancion0003", "cancion0004", "cancion0005")
                .map { async { pedir(sesion, it, ana).status } }
                .awaitAll()
        }
        assertEquals(3, estados.count { it == HttpStatusCode.Created })
        assertEquals(2, estados.count { it == HttpStatusCode.TooManyRequests })

        // Dos celulares piden a la vez la misma canción: una solicitud con dos votos
        val otra = coroutineScope {
            listOf(beto, caro).map { async { pedir(sesion, "cancion0006", it).status } }.awaitAll()
        }
        assertEquals(setOf(HttpStatusCode.Created, HttpStatusCode.OK), otra.toSet())
        assertEquals(2, cola(sesion).cola.single { it.videoId == "cancion0006" }.votos)
    }

    @Test
    fun `con la sesion cerrada no se puede pedir ni votar`() = conApp {
        val token = loginAdmin()
        guardarCanciones()
        val sesion = abrir(token).id
        val id = solicitudCreada(sesion, "cancion0001", ana)
        assertEquals(HttpStatusCode.OK, cliente.post(Rutas.cerrarSesion(sesion)) { bearerAuth(token) }.status)

        error(pedir(sesion, "cancion0002", ana), HttpStatusCode.Conflict, CodigosError.SESION_CERRADA)
        error(votar(id, beto), HttpStatusCode.Conflict, CodigosError.SESION_CERRADA)
        error(cliente.get(Rutas.cola(sesion)) { header(HEADER_DISPOSITIVO, ana) }, HttpStatusCode.Conflict, CodigosError.SESION_CERRADA)
        error(cliente.get(Rutas.cola(UUID.randomUUID().toString())) { header(HEADER_DISPOSITIVO, ana) }, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(cliente.get(Rutas.cola("no-es-un-uuid")) { header(HEADER_DISPOSITIVO, ana) }, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(votar(UUID.randomUUID().toString(), ana), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(cliente.get(Rutas.cola(sesion)), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        assertNotNull(eventos("SOLICITUD_NO_REPRODUCIDA").singleOrNull())
    }
}