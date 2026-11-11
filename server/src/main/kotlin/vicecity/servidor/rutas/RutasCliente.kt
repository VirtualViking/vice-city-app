package vicecity.servidor.rutas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import vicecity.compartido.API_PREFIJO
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.HEADER_IDEMPOTENCIA
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.ResultadoSolicitud
import vicecity.servidor.datosInvalidos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.HuellaDispositivo
import vicecity.servidor.servicios.AccesoService
import vicecity.servidor.servicios.BusquedaService
import vicecity.servidor.servicios.ColaService
import vicecity.servidor.servicios.SugerenciaService
import java.util.UUID

private const val ESTABLECIMIENTO = "$API_PREFIJO/establecimientos/{codigoQr}"
private const val SESION = "$API_PREFIJO/sesiones/{sesionId}"
private val FORMATO_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val FORMATO_VIDEO = Regex("^[A-Za-z0-9_-]{11}$")

/** Endpoints del cliente que abre la app desde el QR. Públicos, pero exigen X-Dispositivo-Id. */
fun Route.rutasCliente(
    huella: HuellaDispositivo,
    acceso: () -> AccesoService,
    busqueda: () -> BusquedaService,
    sugerencias: () -> SugerenciaService,
    cola: () -> ColaService,
) {
    get(ESTABLECIMIENTO) {
        val dispositivo = call.dispositivoHash(huella)
        call.respond(acceso().acceder(call.codigoQr(), dispositivo))
    }

    get("$ESTABLECIMIENTO/busqueda") {
        val dispositivo = call.dispositivoHash(huella)
        val texto = call.request.queryParameters["q"] ?: throw datosInvalidos("q", "Falta el texto a buscar")
        call.respond(busqueda().buscar(call.codigoQr(), texto, dispositivo))
    }

    // Se llama mientras el cliente escribe: valida el dispositivo pero no escribe en la base
    get("$ESTABLECIMIENTO/sugerencias") {
        call.dispositivoHash(huella)
        call.respond(sugerencias().sugerir(call.codigoQr(), call.request.queryParameters["q"].orEmpty()))
    }

    // La app la consulta cada pocos segundos: valida el dispositivo pero no escribe en la base
    get("$SESION/cola") {
        call.dispositivoHash(huella)
        call.respond(cola().estado(call.uuid("sesionId", "la sesión")))
    }

    get("$SESION/mi-actividad") {
        val dispositivo = call.dispositivoHash(huella)
        call.respond(cola().miActividad(call.uuid("sesionId", "la sesión"), dispositivo))
    }

    // 201 si se creó la solicitud; 200 si la canción ya estaba en la cola y se registró un voto
    post("$SESION/solicitudes") {
        val dispositivo = call.dispositivoHash(huella)
        val sesionId = call.uuid("sesionId", "la sesión")
        val videoId = call.receive<NuevaSolicitud>().videoId.trim()
        if (!FORMATO_VIDEO.matches(videoId)) throw datosInvalidos("videoId", "El identificador del video no es válido")
        val respuesta = cola().solicitar(sesionId, videoId, dispositivo, call.claveIdempotencia())
        val estado = if (respuesta.resultado == ResultadoSolicitud.SOLICITUD_CREADA) HttpStatusCode.Created else HttpStatusCode.OK
        call.respond(estado, respuesta)
    }

    post("$API_PREFIJO/solicitudes/{solicitudId}/votos") {
        val dispositivo = call.dispositivoHash(huella)
        val solicitudId = call.uuid("solicitudId", "la canción")
        call.respond(HttpStatusCode.Created, cola().votar(solicitudId, dispositivo, call.claveIdempotencia()))
    }
}

private fun ApplicationCall.codigoQr(): String = parameters["codigoQr"]!!

/** Un identificador mal formado se trata como inexistente (404), igual que en las rutas del personal. */
private fun ApplicationCall.uuid(parametro: String, que: String): UUID =
    parameters[parametro]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw noEncontrado(que)

/** Idempotency-Key opcional (RNF-18). La web manda un UUID nuevo por cada toque y lo repite solo al reintentar. */
private fun ApplicationCall.claveIdempotencia(): String? {
    val valor = request.headers[HEADER_IDEMPOTENCIA]?.trim() ?: return null
    if (valor.isEmpty() || valor.length > 100) throw datosInvalidos(HEADER_IDEMPOTENCIA, "El header $HEADER_IDEMPOTENCIA debe tener entre 1 y 100 caracteres")
    return valor
}

/** Lee el UUID del header y lo convierte en su hash. Sin header o con un formato inválido responde 400. */
private fun ApplicationCall.dispositivoHash(huella: HuellaDispositivo): String {
    val valor = request.headers[HEADER_DISPOSITIVO]?.trim()
    if (valor == null || !FORMATO_UUID.matches(valor)) {
        throw datosInvalidos(HEADER_DISPOSITIVO, "Falta el identificador del dispositivo o no es un UUID válido")
    }
    return huella.de(UUID.fromString(valor))
}