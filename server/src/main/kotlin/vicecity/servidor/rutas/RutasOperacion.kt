package vicecity.servidor.rutas

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import vicecity.compartido.CambioMantenimiento
import vicecity.compartido.ConfiguracionOperacion
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.OperacionService

/** Operación del establecimiento: horario, programación automática, mantenimiento y limpieza. Consultar: ADMIN y DJ. Cambiar: solo ADMIN. */
fun Route.rutasOperacion(operacion: () -> OperacionService) {
    authenticate(AUTH_PERSONAL) {
        get(Rutas.OPERACION) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN, Rol.DJ) }
            call.respond(operacion().estado(usuario))
        }

        put(Rutas.OPERACION) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            call.respond(operacion().configurar(usuario, call.receive<ConfiguracionOperacion>()))
        }

        put(Rutas.MANTENIMIENTO) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            call.respond(operacion().cambiarMantenimiento(usuario, call.receive<CambioMantenimiento>().activo))
        }

        post(Rutas.LIMPIEZA) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            call.respond(operacion().limpiar(usuario))
        }
    }
}