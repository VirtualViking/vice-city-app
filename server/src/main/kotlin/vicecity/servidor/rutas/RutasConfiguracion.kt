package vicecity.servidor.rutas

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import vicecity.compartido.CambioConfiguracion
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.ConfiguracionService

/** Configuración del establecimiento: nombre, código QR y reglas de la cola. Solo el administrador (HU-15, HU-13). */
fun Route.rutasConfiguracion(configuracion: () -> ConfiguracionService) {
    authenticate(AUTH_PERSONAL) {
        get(Rutas.CONFIGURACION) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            call.respond(configuracion().leer(usuario))
        }

        put(Rutas.CONFIGURACION) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            call.respond(configuracion().guardar(usuario, call.receive<CambioConfiguracion>()))
        }
    }
}