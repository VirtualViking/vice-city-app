package vicecity.servidor.rutas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import vicecity.compartido.CambioCuentaPersonal
import vicecity.compartido.NuevaClave
import vicecity.compartido.NuevaCuentaPersonal
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.UsuarioPersonal
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.PersonalService
import java.util.UUID

/** Cuentas del personal: listar, crear, cambiar y restablecer la contraseña. Solo el administrador (HU-18). */
fun Route.rutasPersonal(personal: () -> PersonalService) {
    authenticate(AUTH_PERSONAL) {
        get(Rutas.PERSONAL) {
            call.respond(personal().listar(call.administrador()))
        }

        post(Rutas.PERSONAL) {
            val usuario = call.administrador()
            call.respond(HttpStatusCode.Created, personal().crear(usuario, call.receive<NuevaCuentaPersonal>()))
        }

        put("${Rutas.PERSONAL}/{id}") {
            val usuario = call.administrador()
            call.respond(personal().cambiar(usuario, call.idCuenta(), call.receive<CambioCuentaPersonal>()))
        }

        put("${Rutas.PERSONAL}/{id}/clave") {
            val usuario = call.administrador()
            call.respond(personal().cambiarClave(usuario, call.idCuenta(), call.receive<NuevaClave>()))
        }
    }
}

private fun ApplicationCall.administrador(): UsuarioPersonal = usuario().also { it.exigirRol(Rol.ADMIN) }

private fun ApplicationCall.idCuenta(): UUID =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw noEncontrado("la cuenta")