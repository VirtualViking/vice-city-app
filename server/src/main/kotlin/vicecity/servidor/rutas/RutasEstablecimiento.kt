package vicecity.servidor.rutas

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.servidor.datosInvalidos
import vicecity.servidor.seguridad.AUTH_PERSONAL
import vicecity.servidor.seguridad.exigirRol
import vicecity.servidor.seguridad.usuario
import vicecity.servidor.servicios.QrService

private const val TAMANO_POR_DEFECTO = 1024
private val TAMANOS_PERMITIDOS = 256..2048

/** Datos del establecimiento para el personal. El establecimiento siempre se toma del token. */
fun Route.rutasEstablecimiento(qr: () -> QrService) {
    authenticate(AUTH_PERSONAL) {
        // GET /api/v1/admin/establecimiento/qr?tamano=1024 → image/png para descargar e imprimir
        get(Rutas.ESTABLECIMIENTO_QR) {
            val usuario = call.usuario().also { it.exigirRol(Rol.ADMIN) }
            val tamano = call.request.queryParameters["tamano"]?.let { valor ->
                valor.toIntOrNull()?.takeIf { it in TAMANOS_PERMITIDOS }
                    ?: throw datosInvalidos("tamano", "El tamaño debe estar entre ${TAMANOS_PERMITIDOS.first} y ${TAMANOS_PERMITIDOS.last} píxeles")
            } ?: TAMANO_POR_DEFECTO

            val codigo = qr().deEstablecimiento(usuario.establecimientoId, tamano)
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "qr-${codigo.codigoQr}.png").toString(),
            )
            call.respondBytes(codigo.png, ContentType.Image.PNG)
        }
    }
}