package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.CambioCuentaPersonal
import vicecity.compartido.CodigosError
import vicecity.compartido.CuentaPersonal
import vicecity.compartido.ListaPersonal
import vicecity.compartido.LoginSolicitud
import vicecity.compartido.NuevaClave
import vicecity.compartido.NuevaCuentaPersonal
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Personal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cuentas del personal administradas desde el panel (HU-18, RF-14). */
class PersonalTest : PruebaIntegracion() {

    private val dj = NuevaCuentaPersonal(nombre = "  DJ   Nocturno ", email = " DJ@Club.test ", rol = Rol.DJ, clave = "clave-dj-123")

    private suspend fun Contexto.listar(token: String): HttpResponse = cliente.get(Rutas.PERSONAL) { bearerAuth(token) }

    private suspend fun Contexto.crear(token: String, nueva: NuevaCuentaPersonal): HttpResponse =
        cliente.post(Rutas.PERSONAL) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(nueva)
        }

    private suspend fun Contexto.cambiar(token: String, id: String, cambio: CambioCuentaPersonal): HttpResponse =
        cliente.put(Rutas.cuentaPersonal(id)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cambio)
        }

    private suspend fun Contexto.cambiarClave(token: String, id: String, clave: String): HttpResponse =
        cliente.put(Rutas.clavePersonal(id)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(NuevaClave(clave))
        }

    private suspend fun Contexto.loginFalla(email: String, clave: String) =
        error(cliente.post(Rutas.LOGIN) { json(LoginSolicitud(email, clave)) }, HttpStatusCode.Unauthorized, CodigosError.CREDENCIALES_INVALIDAS)

    @Test
    fun `el administrador crea un DJ que entra con sus propios permisos`() = conApp {
        val admin = loginAdmin()
        val r = crear(admin, dj)
        assertEquals(HttpStatusCode.Created, r.status)
        val cuenta: CuentaPersonal = r.body()
        assertEquals("DJ Nocturno", cuenta.nombre)
        assertEquals("dj@club.test", cuenta.email)
        assertEquals(Rol.DJ, cuenta.rol)
        assertTrue(cuenta.activo)
        assertFalse(cuenta.esUsted)

        // La contraseña se guarda con bcrypt, nunca en claro
        val hash = transaction(bd.database) { Personal.selectAll().where { Personal.email eq "dj@club.test" }.single()[Personal.passwordHash] }
        assertTrue(hash.startsWith("$2"), hash)

        val lista: ListaPersonal = listar(admin).body()
        assertEquals(listOf(Rol.ADMIN, Rol.DJ), lista.cuentas.map { it.rol })
        assertEquals(listOf(true, false), lista.cuentas.map { it.esUsted })

        // El DJ entra (el email no distingue mayúsculas), abre la sesión y no puede administrar
        val tokenDj = login("Dj@Club.test", "clave-dj-123")
        abrir(tokenDj)
        error(listar(tokenDj), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(crear(tokenDj, dj.copy(email = "otro@club.test")), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.get(Rutas.CONFIGURACION) { bearerAuth(tokenDj) }, HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)

        // También se pueden crear otros administradores
        assertEquals(HttpStatusCode.Created, crear(admin, dj.copy(email = "socia@club.test", rol = Rol.ADMIN)).status)
        assertEquals(HttpStatusCode.OK, listar(login("socia@club.test", "clave-dj-123")).status)
    }

    @Test
    fun `los datos de la cuenta nueva se validan`() = conApp {
        val admin = loginAdmin()
        // El email ya existe aunque se escriba con otras mayúsculas
        error(crear(admin, dj.copy(email = "ADMIN@vicecity.test")), HttpStatusCode.Conflict, CodigosError.EMAIL_EN_USO)

        val casos = listOf(
            dj.copy(nombre = " a ") to "nombre",
            dj.copy(nombre = "x".repeat(61)) to "nombre",
            dj.copy(email = "sin-arroba") to "email",
            dj.copy(email = "dos@@club.test") to "email",
            dj.copy(email = "${"a".repeat(151)}@club.test") to "email",   // 161 caracteres
            dj.copy(clave = "corta") to "clave",
            dj.copy(clave = "        ") to "clave",
            // 73 bytes: bcrypt ignoraría lo que pasa de 72
            dj.copy(clave = "ñ".repeat(37)) to "clave",
        )
        casos.forEach { (nueva, campo) ->
            val e = error(crear(admin, nueva), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
            assertEquals(campo, e.detalles["campo"], "caso $nueva")
        }
        assertEquals(1, listar(admin).body<ListaPersonal>().cuentas.size)
    }

    @Test
    fun `desactivar corta el acceso al momento y cambiar el rol se aplica sin volver a entrar`() = conApp {
        val admin = loginAdmin()
        val cuenta: CuentaPersonal = crear(admin, dj).body()
        val tokenDj = login("dj@club.test", "clave-dj-123")

        val r = cambiar(admin, cuenta.id, CambioCuentaPersonal("DJ Nocturno", Rol.DJ, activo = false))
        assertEquals(HttpStatusCode.OK, r.status)
        assertFalse(r.body<CuentaPersonal>().activo)
        // El token que ya tenía deja de valer y no puede volver a entrar
        error(cliente.get(Rutas.SESION_ACTIVA) { bearerAuth(tokenDj) }, HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
        loginFalla("dj@club.test", "clave-dj-123")
        // Las inactivas van al final de la lista
        assertEquals(listOf(true, false), listar(admin).body<ListaPersonal>().cuentas.map { it.activo })

        // Reactivada: vuelve a entrar como DJ
        assertEquals(HttpStatusCode.OK, cambiar(admin, cuenta.id, CambioCuentaPersonal("DJ Nocturno", Rol.DJ, activo = true)).status)
        val tokenNuevo = login("dj@club.test", "clave-dj-123")
        error(listar(tokenNuevo), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)

        // Ascendida a administradora: el mismo token (emitido con rol DJ) ya tiene los permisos nuevos
        assertEquals(HttpStatusCode.OK, cambiar(admin, cuenta.id, CambioCuentaPersonal("Laura", Rol.ADMIN, activo = true)).status)
        assertEquals(HttpStatusCode.OK, listar(tokenNuevo).status)
        assertEquals("Laura", listar(admin).body<ListaPersonal>().cuentas.single { it.id == cuenta.id }.nombre)
    }

    @Test
    fun `el administrador no puede desactivarse ni quitarse el rol, pero si cambiar su nombre y su clave`() = conApp {
        val admin = loginAdmin()
        val yo = listar(admin).body<ListaPersonal>().cuentas.single { it.esUsted }

        val e1 = error(cambiar(admin, yo.id, CambioCuentaPersonal(yo.nombre, Rol.ADMIN, activo = false)), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        assertEquals("activo", e1.detalles["campo"])
        val e2 = error(cambiar(admin, yo.id, CambioCuentaPersonal(yo.nombre, Rol.DJ, activo = true)), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        assertEquals("rol", e2.detalles["campo"])

        assertEquals("Dueño", cambiar(admin, yo.id, CambioCuentaPersonal("Dueño", Rol.ADMIN, activo = true)).body<CuentaPersonal>().nombre)

        // Restablecer la contraseña de otra cuenta y la propia
        val cuenta: CuentaPersonal = crear(admin, dj).body()
        assertEquals(HttpStatusCode.OK, cambiarClave(admin, cuenta.id, "nueva-clave-dj").status)
        loginFalla("dj@club.test", "clave-dj-123")
        login("dj@club.test", "nueva-clave-dj")

        assertEquals(HttpStatusCode.OK, cambiarClave(admin, yo.id, "clave-admin-nueva").status)
        loginFalla("admin@vicecity.test", "clave-admin-123")
        login("admin@vicecity.test", "clave-admin-nueva")

        val e3 = error(cambiarClave(admin, cuenta.id, "corta"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        assertEquals("clave", e3.detalles["campo"])
    }

    @Test
    fun `las cuentas de otro establecimiento no se ven ni se pueden cambiar`() = conApp {
        val otroEstablecimiento = UUID.randomUUID()
        transaction(bd.database) {
            Establecimientos.insert {
                it[id] = otroEstablecimiento
                it[nombre] = "Otro bar"
                it[codigoQr] = "otrobar1"
                it[creadoEn] = Instant.now()
            }
        }
        crearPersonal("otro@vicecity.test", "clave-otro-123", Rol.DJ, otroEstablecimiento)
        val ajena = transaction(bd.database) { Personal.selectAll().where { Personal.email eq "otro@vicecity.test" }.single()[Personal.id].toString() }

        val admin = loginAdmin()
        assertEquals(1, listar(admin).body<ListaPersonal>().cuentas.size)
        error(cambiar(admin, ajena, CambioCuentaPersonal("Robada", Rol.DJ, activo = false)), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(cambiarClave(admin, ajena, "clave-robada-1"), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(cambiar(admin, "no-es-un-id", CambioCuentaPersonal("X y", Rol.DJ, activo = true)), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        // Sigue intacta
        login("otro@vicecity.test", "clave-otro-123")
    }
}