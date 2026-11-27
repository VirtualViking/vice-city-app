package vicecity.servidor.rutas

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import vicecity.compartido.CambioExclusion
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.UsuarioPersonal
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.AnaliticaService

/** Capa analítica: tablero, sesiones del periodo, ejecución del pipeline y calidad de datos. Solo el administrador (HU-23). */
fun Route.rutasAnalitica(analitica: () -> AnaliticaService) {
    authenticate(AUTH_PERSONAL) {
        get(Rutas.ANALITICA) {
            val usuario = call.administrador()
            val p = call.request.queryParameters
            call.respond(analitica().tablero(usuario, p["desde"], p["hasta"], p["sesion"]))
        }
        get(Rutas.ANALITICA_SESIONES) {
            val usuario = call.administrador()
            val p = call.request.queryParameters
            call.respond(analitica().sesiones(usuario, p["desde"], p["hasta"]))
        }
        post(Rutas.ANALITICA_PIPELINE) {
            call.administrador()
            call.respond(analitica().ejecutarPipeline())
        }
        get(Rutas.ANALITICA_CALIDAD) {
            call.respond(analitica().calidad(call.administrador()))
        }
    }
}

private fun ApplicationCall.administrador(): UsuarioPersonal = usuario().also { it.exigirRol(Rol.ADMIN) }