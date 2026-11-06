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
import java.util.UUID

private const val ESTABLECIMIENTO = "$API_PREFIJO/establecimientos/{codigoQr}"
private val FORMATO_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** Endpoints del cliente que abre la app desde el QR. Públicos, pero exigen X-Dispositivo-Id. */
fun Route.rutasCliente(huella: HuellaDispositivo, acceso: () -> AccesoService) {
    get(ESTABLECIMIENTO) {
        val dispositivo = call.dispositivoHash(huella)
        call.respond(acceso().acceder(call.codigoQr(), dispositivo))
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