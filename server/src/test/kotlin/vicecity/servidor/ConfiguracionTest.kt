package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.CambioConfiguracion
import vicecity.compartido.CodigosError
import vicecity.compartido.ConfiguracionEstablecimiento
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.ReglasCola
import vicecity.compartido.RespuestaSolicitud
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.Solicitudes
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Configuración del establecimiento: nombre, código QR y reglas de la cola (HU-15, HU-13, RF-15, RF-19). */
class ConfiguracionTest : PruebaIntegracion() {
    private val ana = UUID.randomUUID().toString()
    private val beto = UUID.randomUUID().toString()
    private val caro = UUID.randomUUID().toString()

    private val porDefecto = ReglasCola(limiteSolicitudes = 3, ventanaLimiteMin = 15, antirepeticionMin = 60, duracionMaxMin = 8)

    private suspend fun Contexto.leer(token: String): HttpResponse = cliente.get(Rutas.CONFIGURACION) { bearerAuth(token) }

    private suspend fun Contexto.guardar(token: String, cambio: CambioConfiguracion): HttpResponse =
        cliente.put(Rutas.CONFIGURACION) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cambio)
        }

    private suspend fun Contexto.pedir(sesionId: String, videoId: String, dispositivo: String): HttpResponse =
        cliente.post(Rutas.solicitudes(sesionId)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            contentType(ContentType.Application.Json)
            setBody(NuevaSolicitud(videoId))
        }

    @Test
    fun `el administrador ve el QR y cambia el nombre del establecimiento`() = conApp {
        val token = loginAdmin()
        val actual: ConfiguracionEstablecimiento = leer(token).also { assertEquals(HttpStatusCode.OK, it.status) }.body()
        assertEquals("Bar de pruebas", actual.nombre)
        assertEquals(codigoQr(), actual.codigoQr)
        assertTrue(actual.enlace.endsWith("/?e=${actual.codigoQr}"), actual.enlace)
        assertEquals(porDefecto, actual.reglas)

        val r = guardar(token, CambioConfiguracion("  Club   Neón  ", actual.reglas))
        assertEquals(HttpStatusCode.OK, r.status)
        val nuevo: ConfiguracionEstablecimiento = r.body()
        assertEquals("Club Neón", nuevo.nombre)
        // El código no cambia: el QR impreso sigue sirviendo
        assertEquals(actual.codigoQr, nuevo.codigoQr)
        assertEquals("Club Neón", leer(token).body<ConfiguracionEstablecimiento>().nombre)

        // El cliente ve el nombre nuevo al escanear el QR
        val publico: EstablecimientoPublico = cliente.get(Rutas.establecimiento(actual.codigoQr)) { header(HEADER_DISPOSITIVO, ana) }.body()
        assertEquals("Club Neón", publico.nombre)
    }

    @Test
    fun `las reglas nuevas aplican desde la siguiente solicitud`() = conApp {
        transaction(bd.database) {
            RepositorioCanciones.guardar(
                listOf(
                    video("cancion0001", "Canción uno", "Artista Uno - Topic"),
                    video("cancion0002", "Canción dos", "Artista Dos - Topic"),
                    video("seisminutos", "Canción larga", "Artista Largo - Topic", duracionSeg = 360),
                ),
                Instant.now(),
            )
        }
        val token = loginAdmin()
        val sesion = abrir(token).id
        val nombre = leer(token).body<ConfiguracionEstablecimiento>().nombre

        // Una sola solicitud cada 30 minutos y videos de hasta 5 minutos
        guardar(token, CambioConfiguracion(nombre, ReglasCola(1, 30, 60, 5))).also { assertEquals(HttpStatusCode.OK, it.status) }
        val primera: RespuestaSolicitud = pedir(sesion, "cancion0001", ana).also { assertEquals(HttpStatusCode.Created, it.status) }.body()
        error(pedir(sesion, "cancion0002", ana), HttpStatusCode.TooManyRequests, CodigosError.LIMITE_SOLICITUDES)
        val larga = error(pedir(sesion, "seisminutos", beto), HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO)
        assertEquals("DURACION_EXCEDIDA", larga.detalles["motivo"])

        // Al volver a 3 solicitudes y 8 minutos, las mismas solicitudes pasan
        guardar(token, CambioConfiguracion(nombre, porDefecto)).also { assertEquals(HttpStatusCode.OK, it.status) }
        assertEquals(HttpStatusCode.Created, pedir(sesion, "cancion0002", ana).status)
        assertEquals(HttpStatusCode.Created, pedir(sesion, "seisminutos", beto).status)

        // Anti-repetición: la canción sonó hace 1 minuto; con 60 minutos no se puede pedir, con 0 sí
        transaction(bd.database) {
            Solicitudes.update({ Solicitudes.id eq UUID.fromString(primera.solicitud.solicitudId) }) {
                it[estado] = "REPRODUCIDA"
                it[finReproduccion] = Instant.now().minus(Duration.ofMinutes(1))
            }
        }
        error(pedir(sesion, "cancion0001", caro), HttpStatusCode.Conflict, CodigosError.CANCION_RECIENTE)
        guardar(token, CambioConfiguracion(nombre, porDefecto.copy(antirepeticionMin = 0))).also { assertEquals(HttpStatusCode.OK, it.status) }
        assertEquals(HttpStatusCode.Created, pedir(sesion, "cancion0001", caro).status)
    }

    @Test
    fun `los valores fuera de rango se rechazan sin cambiar nada`() = conApp {
        val token = loginAdmin()
        val antes: ConfiguracionEstablecimiento = leer(token).body()
        val casos = listOf(
            "nombre" to CambioConfiguracion("ab", porDefecto),
            "nombre" to CambioConfiguracion("      ", porDefecto),
            "nombre" to CambioConfiguracion("x".repeat(61), porDefecto),
            "limiteSolicitudes" to CambioConfiguracion("Club Neón", porDefecto.copy(limiteSolicitudes = 0)),
            "ventanaLimiteMin" to CambioConfiguracion("Club Neón", porDefecto.copy(ventanaLimiteMin = 241)),
            "antirepeticionMin" to CambioConfiguracion("Club Neón", porDefecto.copy(antirepeticionMin = -1)),
            "duracionMaxMin" to CambioConfiguracion("Club Neón", porDefecto.copy(duracionMaxMin = 21)),
        )
        casos.forEach { (campo, cambio) ->
            val e = error(guardar(token, cambio), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
            assertEquals(campo, e.detalles["campo"])
        }
        assertEquals(antes, leer(token).body<ConfiguracionEstablecimiento>())
    }

    @Test
    fun `solo el administrador configura el establecimiento`() = conApp {
        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        val dj = login("dj@vicecity.test", "clave-dj-123")
        error(leer(dj), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(guardar(dj, CambioConfiguracion("Otro nombre", porDefecto)), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.get(Rutas.CONFIGURACION), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
        assertEquals("Bar de pruebas", leer(loginAdmin()).body<ConfiguracionEstablecimiento>().nombre)
    }
}