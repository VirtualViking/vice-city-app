package vicecity.servidor.rutas

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import vicecity.compartido.LoginSolicitud
import vicecity.compartido.Rutas
import vicecity.servidor.servicios.AuthService

/** POST /api/v1/auth/login (HU-02) */
fun Route.rutasAuth(auth: () -> AuthService) {
    post(Rutas.LOGIN) {
        call.respond(auth().login(call.receive<LoginSolicitud>()))
    }
}