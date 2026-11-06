package vicecity.servidor.rutas

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import vicecity.compartido.API_PREFIJO
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.servidor.datosInvalidos
import vicecity.servidor.seguridad.HuellaDispositivo
import vicecity.servidor.servicios.AccesoService
import vicecity.servidor.servicios.BusquedaService
import vicecity.servidor.servicios.SugerenciaService
import java.util.UUID

private const val ESTABLECIMIENTO = "$API_PREFIJO/establecimientos/{codigoQr}"
private val FORMATO_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** Endpoints del cliente que abre la app desde el QR. Públicos, pero exigen X-Dispositivo-Id. */
fun Route.rutasCliente(
    huella: HuellaDispositivo,
    acceso: () -> AccesoService,
    busqueda: () -> BusquedaService,
    sugerencias: () -> SugerenciaService,
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
}

private fun ApplicationCall.codigoQr(): String = parameters["codigoQr"]!!

/** Lee el UUID del header y lo convierte en su hash. Sin header o con un formato inválido responde 400. */
private fun ApplicationCall.dispositivoHash(huella: HuellaDispositivo): String {
    val valor = request.headers[HEADER_DISPOSITIVO]?.trim()
    if (valor == null || !FORMATO_UUID.matches(valor)) {
        throw datosInvalidos(HEADER_DISPOSITIVO, "Falta el identificador del dispositivo o no es un UUID válido")
    }
    return huella.de(UUID.fromString(valor))
}