package vicecity.servidor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.Assume.assumeTrue
import vicecity.compartido.CierreSesion
import vicecity.compartido.CodigosError
import vicecity.compartido.ErrorApi
import vicecity.compartido.EstadoSesion
import vicecity.compartido.ListaSesiones
import vicecity.compartido.LoginRespuesta
import vicecity.compartido.LoginSolicitud
import vicecity.compartido.Modo
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionActiva
import vicecity.compartido.SesionDto
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.Personal
import vicecity.servidor.seguridad.Claves
import vicecity.servidor.servicios.Inicializador
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de integración de la entrega 1.1 (HU-02 y HU-03) contra un PostgreSQL real.
 * Se omiten si TEST_DATABASE_URL no está definida. Cada prueba BORRA la base y parte de cero,
 * por eso el nombre de la base debe terminar en _test (nunca la de desarrollo ni la de Neon).
 */
class AccesoSesionesTest {
    private val url = System.getenv("TEST_DATABASE_URL")?.takeIf { it.isNotBlank() }

    private val config = Configuracion(
        urlBaseDatos = url,
        origenesPermitidos = Configuracion.leerOrigenes("http://localhost:8080"),
        youtubeApiKey = null,
        jwtSecreto = "secreto-de-pruebas-con-longitud-suficiente",
        adminEmail = "Admin@ViceCity.test",
        adminPassword = "clave-admin-123",
        adminNombre = "Admin de pruebas",
        establecimientoNombre = "Bar de pruebas",
    )

    private lateinit var bd: BaseDatos

    @BeforeTest
    fun preparar() {
        assumeTrue("TEST_DATABASE_URL no está definida: se omiten las pruebas de integración", url != null)
        // Las pruebas borran la base completa: solo se permiten bases cuyo nombre termine en _test
        val nombreBase = url!!.substringAfterLast('/').substringBefore('?')
        check(nombreBase.endsWith("_test")) { "TEST_DATABASE_URL debe apuntar a una base de pruebas (nombre terminado en _test), no a '$nombreBase'" }
        bd = compartida ?: BaseDatos.conectar(url)!!.also { compartida = it }
        transaction(bd.database) {
            exec("DROP SCHEMA public CASCADE")
            exec("CREATE SCHEMA public")
        }
        bd.migrar()
        Inicializador.ejecutar(bd, config)
    }

    // ---------- Esquema y datos iniciales

    @Test
    fun `el inicializador crea establecimiento y admin una sola vez`() {
        Inicializador.ejecutar(bd, config)
        transaction(bd.database) {
            assertEquals(1, Establecimientos.selectAll().count())
            assertEquals(1, Personal.selectAll().count())
            val admin = Personal.selectAll().single()
            assertEquals("admin@vicecity.test", admin[Personal.email])
            assertEquals("ADMIN", admin[Personal.rol])
            val codigo = Establecimientos.selectAll().single()[Establecimientos.codigoQr]
            assertEquals(8, codigo.length)
        }
    }

    @Test
    fun `la tabla evento no admite actualizar ni borrar`() = conApp {
        abrir(loginAdmin())
        transaction(bd.database) {
            assertEquals(1, Eventos.selectAll().count())
        }
        assertFailsWith<Exception> {
            transaction(bd.database) { exec("UPDATE evento SET tipo = 'OTRO'") }
        }
        assertFailsWith<Exception> {
            transaction(bd.database) { exec("DELETE FROM evento") }
        }
    }

    // ---------- Login (HU-02)

    @Test
    fun `login correcto devuelve token y rol, sin importar mayusculas del email`() = conApp {
        val r = cliente.post(Rutas.LOGIN) { json(LoginSolicitud("  ADMIN@vicecity.TEST ", "clave-admin-123")) }
        assertEquals(HttpStatusCode.OK, r.status)
        val login = r.body<LoginRespuesta>()
        assertEquals(Rol.ADMIN, login.rol)
        assertEquals("Admin de pruebas", login.nombre)
        assertTrue(login.token.count { it == '.' } == 2)
        assertTrue(Instant.parse(login.expiraEn).isAfter(Instant.now().plusSeconds(11 * 3600)))
    }

    @Test
    fun `login con clave incorrecta, email desconocido o cuenta inactiva responde 401`() = conApp {
        error(cliente.post(Rutas.LOGIN) { json(LoginSolicitud("admin@vicecity.test", "otra")) }, HttpStatusCode.Unauthorized, CodigosError.CREDENCIALES_INVALIDAS)
        error(cliente.post(Rutas.LOGIN) { json(LoginSolicitud("nadie@vicecity.test", "clave-admin-123")) }, HttpStatusCode.Unauthorized, CodigosError.CREDENCIALES_INVALIDAS)

        transaction(bd.database) { Personal.update({ Personal.email eq "admin@vicecity.test" }) { it[activo] = false } }
        error(cliente.post(Rutas.LOGIN) { json(LoginSolicitud("admin@vicecity.test", "clave-admin-123")) }, HttpStatusCode.Unauthorized, CodigosError.CREDENCIALES_INVALIDAS)
    }

    @Test
    fun `cuerpo mal formado responde 400`() = conApp {
        val r = cliente.post(Rutas.LOGIN) { contentType(ContentType.Application.Json); setBody("""{"email": 3""") }
        error(r, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
    }

    @Test
    fun `sin token o con token alterado responde 401`() = conApp {
        error(cliente.post(Rutas.SESIONES), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
        val token = loginAdmin().dropLast(2) + "xx"
        error(cliente.post(Rutas.SESIONES) { bearerAuth(token) }, HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
    }

    // ---------- Sesiones (HU-03)

    @Test
    fun `abrir sesion, impedir una segunda y consultarla como activa`() = conApp {
        val token = loginAdmin()
        val sesion = abrir(token)
        assertEquals(EstadoSesion.ABIERTA, sesion.estado)
        assertEquals(Modo.COLA, sesion.modo)
        assertNull(sesion.fin)

        val r = cliente.post(Rutas.SESIONES) { bearerAuth(token) }
        val e = error(r, HttpStatusCode.Conflict, CodigosError.SESION_YA_ABIERTA)
        assertEquals(sesion.id, e.detalles["sesionId"])

        val activa = cliente.get(Rutas.SESION_ACTIVA) { bearerAuth(token) }.body<SesionActiva>()
        assertEquals(sesion.id, activa.sesion?.id)
    }

    @Test
    fun `cerrar sesion marca las solicitudes pendientes y registra los eventos`() = conApp {
        val token = loginAdmin()
        val sesion = abrir(token)
        val sesionId = UUID.fromString(sesion.id)
        crearSolicitud(sesionId, "video000001", "EN_COLA")
        crearSolicitud(sesionId, "video000002", "EN_COLA")
        crearSolicitud(sesionId, "video000003", "REPRODUCIENDO")

        val r = cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, r.status)
        val cierre = r.body<CierreSesion>()
        assertEquals(2, cierre.noReproducidas)
        assertEquals(EstadoSesion.CERRADA, cierre.sesion.estado)
        assertNotNull(cierre.sesion.fin)

        transaction(bd.database) {
            val estados = exec("SELECT estado, count(*) FROM solicitud GROUP BY estado ORDER BY estado") { rs ->
                buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
            }
            assertEquals(mapOf("NO_REPRODUCIDA" to 2, "REPRODUCIDA" to 1), estados)
            val tipos = Eventos.selectAll().map { it[Eventos.tipo] }.groupingBy { it }.eachCount()
            assertEquals(mapOf("SESION_ABIERTA" to 1, "SOLICITUD_NO_REPRODUCIDA" to 2, "SESION_CERRADA" to 1), tipos)
            val cerrada = Eventos.selectAll().where { Eventos.tipo eq "SESION_CERRADA" }.single()
            assertEquals("2", cerrada[Eventos.datos]["no_reproducidas"].toString())
        }

        error(cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }, HttpStatusCode.Conflict, CodigosError.SESION_CERRADA)
        assertNull(cliente.get(Rutas.SESION_ACTIVA) { bearerAuth(token) }.body<SesionActiva>().sesion)

        // Tras cerrar, se puede abrir la sesión de la noche siguiente
        abrir(token)
        val lista = cliente.get(Rutas.SESIONES) { bearerAuth(token) }.body<ListaSesiones>()
        assertEquals(2, lista.sesiones.size)
        assertEquals(EstadoSesion.ABIERTA, lista.sesiones.first().estado)
    }

    @Test
    fun `el DJ abre y cierra sesiones pero no ve el historial`() = conApp {
        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        val token = login("dj@vicecity.test", "clave-dj-123")
        val sesion = abrir(token)
        assertEquals(HttpStatusCode.OK, cliente.get(Rutas.SESION_ACTIVA) { bearerAuth(token) }.status)
        error(cliente.get(Rutas.SESIONES) { bearerAuth(token) }, HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        assertEquals(HttpStatusCode.OK, cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }.status)
    }

    @Test
    fun `no se puede cerrar la sesion de otro establecimiento`() = conApp {
        val sesionAjena = abrir(loginAdmin())
        val otroEstablecimiento = UUID.randomUUID()
        transaction(bd.database) {
            Establecimientos.insert {
                it[id] = otroEstablecimiento
                it[nombre] = "Otro bar"
                it[codigoQr] = "otrobar1"
                it[creadoEn] = Instant.now()
            }
        }
        crearPersonal("otro@vicecity.test", "clave-otro-123", Rol.ADMIN, otroEstablecimiento)
        val tokenOtro = login("otro@vicecity.test", "clave-otro-123")

        error(cliente.post(Rutas.cerrarSesion(sesionAjena.id)) { bearerAuth(tokenOtro) }, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(cliente.post(Rutas.cerrarSesion("no-es-un-uuid")) { bearerAuth(tokenOtro) }, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        assertTrue(cliente.get(Rutas.SESIONES) { bearerAuth(tokenOtro) }.body<ListaSesiones>().sesiones.isEmpty())
        // Cada establecimiento tiene su propia sesión abierta
        abrir(tokenOtro)
    }

    @Test
    fun `listar valida las fechas del filtro`() = conApp {
        val token = loginAdmin()
        abrir(token)
        val r = cliente.get("${Rutas.SESIONES}?desde=ayer") { bearerAuth(token) }
        error(r, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        val futuro = cliente.get("${Rutas.SESIONES}?desde=2999-01-01T00:00:00Z") { bearerAuth(token) }.body<ListaSesiones>()
        assertTrue(futuro.sesiones.isEmpty())
    }

    // ---------- Ayudas

    private class Contexto(val cliente: HttpClient)

    private fun conApp(prueba: suspend Contexto.() -> Unit) = testApplication {
        application { modulo(config, bd) }
        Contexto(clienteJson()).prueba()
    }

    private fun ApplicationTestBuilder.clienteJson() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun Contexto.login(email: String, clave: String): String {
        val r = cliente.post(Rutas.LOGIN) { json(LoginSolicitud(email, clave)) }
        assertEquals(HttpStatusCode.OK, r.status)
        return r.body<LoginRespuesta>().token
    }

    private suspend fun Contexto.loginAdmin() = login("admin@vicecity.test", "clave-admin-123")

    private suspend fun Contexto.abrir(token: String): SesionDto {
        val r = cliente.post(Rutas.SESIONES) { bearerAuth(token) }
        assertEquals(HttpStatusCode.Created, r.status)
        return r.body()
    }

    private suspend fun error(r: HttpResponse, estado: HttpStatusCode, codigo: String): ErrorApi {
        assertEquals(estado, r.status)
        val e = r.body<ErrorApi>()
        assertEquals(codigo, e.codigo)
        return e
    }

    private fun HttpRequestBuilder.json(cuerpo: LoginSolicitud) {
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    private fun crearPersonal(email: String, clave: String, rol: Rol, establecimiento: UUID? = null) = transaction(bd.database) {
        val est = establecimiento ?: Establecimientos.selectAll().first()[Establecimientos.id]
        Personal.insert {
            it[id] = UUID.randomUUID()
            it[establecimientoId] = est
            it[nombre] = email.substringBefore('@')
            it[Personal.email] = email
            it[passwordHash] = Claves.hash(clave)
            it[Personal.rol] = rol.name
            it[activo] = true
            it[creadoEn] = Instant.now()
        }
    }

    /** Solicitud de prueba insertada directamente (los endpoints de solicitudes llegan en la entrega 1.2). */
    private fun crearSolicitud(sesionId: UUID, videoId: String, estado: String) = transaction(bd.database) {
        val hash = "a".repeat(64)
        exec("INSERT INTO dispositivo (hash) VALUES ('$hash') ON CONFLICT DO NOTHING")
        exec("INSERT INTO cancion (video_id, titulo, canal, duracion_seg) VALUES ('$videoId', 'Canción $videoId', 'Canal', 200)")
        exec(
            "INSERT INTO solicitud (id, sesion_id, video_id, dispositivo_hash, estado, solicitada_en) " +
                    "VALUES ('${UUID.randomUUID()}', '$sesionId', '$videoId', '$hash', '$estado', now() - interval '10 minutes')"
        )
    }

    private companion object {
        var compartida: BaseDatos? = null
    }
}