package vicecity.servidor.rutas

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import vicecity.compartido.API_PREFIJO
import vicecity.servidor.integraciones.Miniaturas
import vicecity.servidor.noEncontrado

private const val UNA_SEMANA_SEG = 7 * 24 * 60 * 60

/** GET /api/v1/miniaturas/{videoId}: imagen JPEG pública; el navegador la guarda una semana. */
fun Route.rutasMiniaturas(miniaturas: Miniaturas) {
    get("$API_PREFIJO/miniaturas/{videoId}") {
        val bytes = miniaturas.de(call.parameters["videoId"]!!) ?: throw noEncontrado("la miniatura")
        call.response.header(HttpHeaders.CacheControl, CacheControl.MaxAge(UNA_SEMANA_SEG, visibility = CacheControl.Visibility.Public).toString())
        call.respondBytes(bytes, ContentType.Image.JPEG)
    }
}