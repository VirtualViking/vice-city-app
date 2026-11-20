package vicecity.servidor.rutas

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import vicecity.compartido.API_PREFIJO
import vicecity.compartido.EliminacionSolicitud
import vicecity.compartido.FinReproduccion
import vicecity.compartido.NuevaListaDefecto
import vicecity.compartido.ReproduccionFallida
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.datosInvalidos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.UsuarioPersonal
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.BloqueoService
import vicecity.servidor.servicios.BusquedaService
import vicecity.servidor.servicios.ListaDefectoService
import vicecity.servidor.servicios.ReproductorService
import java.util.UUID

/**
 * Panel del personal y vista pública (RF-11, RF-17, RF-18, RF-25): reproductor, eliminación y bloqueos, lista por defecto.
 * Las usan el ADMIN y el DJ. El establecimiento siempre se toma del token.
 */
fun Route.rutasPanel(
    reproductor: () -> ReproductorService,
    listaDefecto: () -> ListaDefectoService,
    bloqueos: () -> BloqueoService,
    busqueda: () -> BusquedaService,
) {
    authenticate(AUTH_PERSONAL) {
        // Reproductor: el panel y la vista pública lo consultan cada pocos segundos
        get(Rutas.REPRODUCTOR) { call.respond(reproductor().estado(call.personal())) }
        post(Rutas.REPRODUCTOR_INICIAR) { call.respond(reproductor().iniciar(call.personal())) }
        post(Rutas.REPRODUCTOR_PAUSAR) { call.respond(reproductor().pausar(call.personal())) }
        post(Rutas.REPRODUCTOR_REANUDAR) { call.respond(reproductor().reanudar(call.personal())) }
        post(Rutas.REPRODUCTOR_SALTAR) { call.respond(reproductor().saltar(call.personal())) }
        post(Rutas.REPRODUCTOR_TERMINADA) {
            val usuario = call.personal()
            call.respond(reproductor().terminada(usuario, call.receive<FinReproduccion>()))
        }
        post(Rutas.REPRODUCTOR_FALLIDA) {
            val usuario = call.personal()
            call.respond(reproductor().fallida(usuario, call.receive<ReproduccionFallida>()))
        }
        post("$API_PREFIJO/admin/solicitudes/{solicitudId}/eliminar") {
            val usuario = call.personal()
            val solicitudId = call.parameters["solicitudId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw noEncontrado("la canción")
            call.respond(reproductor().eliminar(usuario, solicitudId, call.receive<EliminacionSolicitud>()))
        }

        // Lista por defecto
        get(Rutas.LISTA_DEFECTO) { call.respond(listaDefecto().obtener(call.personal())) }
        put(Rutas.LISTA_DEFECTO) {
            val usuario = call.personal()
            call.respond(listaDefecto().guardar(usuario, call.receive<NuevaListaDefecto>()))
        }
        get(Rutas.BUSQUEDA_PERSONAL) {
            val usuario = call.personal()
            val texto = call.request.queryParameters["q"] ?: throw datosInvalidos("q", "Falta el texto a buscar")
            call.respond(busqueda().buscarPersonal(usuario, texto))
        }

        // Bloqueos
        get(Rutas.BLOQUEOS) { call.respond(bloqueos().listar(call.personal())) }
        delete("${Rutas.BLOQUEOS}/{videoId}") {
            val usuario = call.personal()
            call.respond(bloqueos().desbloquear(usuario, call.parameters["videoId"]!!))
        }
    }
}

/** Usuario del personal con rol ADMIN o DJ. */
private fun ApplicationCall.personal(): UsuarioPersonal = usuario().also { it.exigirRol(Rol.ADMIN, Rol.DJ) }