package vicecity.servidor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import vicecity.compartido.ErrorApi
import vicecity.compartido.LoginRespuesta
import vicecity.compartido.LoginSolicitud
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionDto
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Personal
import vicecity.servidor.seguridad.Claves
import vicecity.servidor.servicios.Inicializador
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

/**
 * Base de las pruebas de integración contra un PostgreSQL real.
 * Se omiten si TEST_DATABASE_URL no está definida. Antes de cada prueba BORRA la base y parte de cero,
 * por eso el nombre de la base debe terminar en _test (nunca la de desarrollo ni la de Neon).
 */
abstract class PruebaIntegracion {
    protected open val config = Configuracion(
        urlBaseDatos = url,
        origenesPermitidos = Configuracion.leerOrigenes("http://localhost:8080"),
        youtubeApiKey = null,
        jwtSecreto = "secreto-de-pruebas-con-longitud-suficiente",
        dispositivoSecreto = "secreto-de-dispositivos-de-pruebas",
        adminEmail = "Admin@ViceCity.test",
        adminPassword = "clave-admin-123",
        adminNombre = "Admin de pruebas",
        establecimientoNombre = "Bar de pruebas",
    )

    protected lateinit var bd: BaseDatos

    /** YouTube simulado: ninguna prueba gasta cupo real. */
    protected val catalogo = CatalogoFalso()

    @BeforeTest
    fun prepararBase() {
        assumeTrue("TEST_DATABASE_URL no está definida: se omiten las pruebas de integración", url != null)
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

    // ---------- Aplicación y cliente HTTP

    protected class Contexto(val cliente: HttpClient)

    /** Levanta el servidor en memoria con la base de pruebas y el catálogo simulado, y ejecuta [prueba] con un cliente JSON. */
    protected fun conApp(prueba: suspend Contexto.() -> Unit) = testApplication {
        application { modulo(config, bd, catalogo) }
        Contexto(createClient { install(ContentNegotiation) { json() } }).prueba()
    }

    protected suspend fun Contexto.login(email: String, clave: String): String {
        val r = cliente.post(Rutas.LOGIN) { json(LoginSolicitud(email, clave)) }
        assertEquals(HttpStatusCode.OK, r.status)
        return r.body<LoginRespuesta>().token
    }

    protected suspend fun Contexto.loginAdmin() = login("admin@vicecity.test", "clave-admin-123")

    protected suspend fun Contexto.abrir(token: String): SesionDto {
        val r = cliente.post(Rutas.SESIONES) { bearerAuth(token) }
        assertEquals(HttpStatusCode.Created, r.status)
        return r.body()
    }

    /** Comprueba el estado HTTP y el código del contrato, y devuelve el error para más verificaciones. */
    protected suspend fun error(r: HttpResponse, estado: HttpStatusCode, codigo: String): ErrorApi {
        assertEquals(estado, r.status)
        val e = r.body<ErrorApi>()
        assertEquals(codigo, e.codigo)
        return e
    }

    protected fun HttpRequestBuilder.json(cuerpo: LoginSolicitud) {
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    // ---------- Datos de prueba

    protected fun establecimientoId(): UUID = transaction(bd.database) {
        Establecimientos.selectAll().first()[Establecimientos.id]
    }

    protected fun codigoQr(): String = transaction(bd.database) {
        Establecimientos.selectAll().first()[Establecimientos.codigoQr]
    }

    protected fun crearPersonal(email: String, clave: String, rol: Rol, establecimiento: UUID? = null) = transaction(bd.database) {
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

    private companion object {
        val url: String? = System.getenv("TEST_DATABASE_URL")?.takeIf { it.isNotBlank() }
        var compartida: BaseDatos? = null
    }
}