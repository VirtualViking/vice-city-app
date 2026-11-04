package vicecity.servidor.rutas

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import vicecity.compartido.ListaSesiones
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionActiva
import vicecity.servidor.datosInvalidos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.SesionService
import java.time.Instant
import java.util.UUID

/** Sesiones de solicitudes (HU-03). Abrir, cerrar y ver la activa: ADMIN y DJ. El historial: solo ADMIN. */
fun Route.rutasSesiones(sesiones: () -> SesionService) {
    authenticate(AUTH_PERSONAL) {
        post(Rutas.SESIONES) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN, Rol.DJ) }
            call.respond(HttpStatusCode.Created, sesiones().abrir(usuario))
        }

        get(Rutas.SESION_ACTIVA) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN, Rol.DJ) }
            call.respond(SesionActiva(sesiones().activa(usuario)))
        }

        post("${Rutas.SESIONES}/{id}/cerrar") {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN, Rol.DJ) }
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw noEncontrado("la sesión")
            call.respond(sesiones().cerrar(usuario, id))
        }

        get(Rutas.SESIONES) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            val desde = call.request.queryParameters["desde"]?.let { fecha(it, "desde") }
            val hasta = call.request.queryParameters["hasta"]?.let { fecha(it, "hasta") }
            call.respond(ListaSesiones(sesiones().listar(usuario, desde, hasta)))
        }
    }
}

private fun fecha(valor: String, campo: String): Instant =
    runCatching { Instant.parse(valor) }.getOrElse { throw datosInvalidos(campo, "Fecha inválida en '$campo': usa ISO 8601, por ejemplo 2026-11-01T00:00:00Z") }