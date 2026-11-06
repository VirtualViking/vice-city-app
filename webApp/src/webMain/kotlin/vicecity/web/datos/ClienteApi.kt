package vicecity.web.datos

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import vicecity.compartido.CodigosError
import vicecity.compartido.ErrorApi
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.RespuestaBusqueda
import vicecity.compartido.RespuestaSugerencias
import vicecity.compartido.Rutas
import vicecity.web.ViceCityConfig

/** Error con el formato del contrato (ErrorApi) o de red, listo para mostrar. */
class ErrorDeApi(val codigo: String, override val message: String) : Exception(message)

/**
 * Cliente HTTP hacia el backend Ktor. La URL base viene de config.js; cada petición lleva el identificador del dispositivo.
 * Cualquier falla (sin red, respuesta de error o datos inesperados) se convierte en [ErrorDeApi], que las pantallas muestran.
 */
class ClienteApi(private val base: String = ViceCityConfig.apiBase.trimEnd('/')) {
    private val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Render tarda hasta un minuto en despertar el servicio gratuito
        install(HttpTimeout) { requestTimeoutMillis = 90_000 }
    }

    suspend fun establecimiento(codigoQr: String): EstablecimientoPublico =
        pedir(Rutas.establecimiento(codigoQr))

    suspend fun buscar(codigoQr: String, texto: String): RespuestaBusqueda =
        pedir(Rutas.busqueda(codigoQr)) { parameter("q", texto) }

    suspend fun sugerir(codigoQr: String, texto: String): RespuestaSugerencias =
        pedir(Rutas.sugerencias(codigoQr)) { parameter("q", texto) }

    /** Bytes de la miniatura del video, servida por el backend, o null si no se pudo descargar. */
    suspend fun miniatura(videoId: String): ByteArray? = try {
        http.get(base + Rutas.miniatura(videoId)).takeIf { it.status.isSuccess() }?.readRawBytes()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    /*
     * Se captura Throwable y no solo Exception: en el navegador, una falla de red llega como un error
     * de JavaScript (por ejemplo "Fail to fetch"), que no es una Exception de Kotlin.
     */
    private suspend inline fun <reified T> pedir(ruta: String, crossinline extra: HttpRequestBuilder.() -> Unit = {}): T {
        val respuesta: HttpResponse = try {
            http.get(base + ruta) {
                header(HEADER_DISPOSITIVO, Dispositivo.id)
                extra()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ErrorDeApi(SIN_CONEXION, "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.")
        }
        if (!respuesta.status.isSuccess()) {
            val error = try { respuesta.body<ErrorApi>() } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
            throw ErrorDeApi(error?.codigo ?: CodigosError.ERROR_INTERNO, error?.mensaje ?: MENSAJE_GENERICO)
        }
        return try {
            respuesta.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ErrorDeApi(CodigosError.ERROR_INTERNO, MENSAJE_GENERICO)
        }
    }

    companion object {
        const val SIN_CONEXION = "SIN_CONEXION"
        private const val MENSAJE_GENERICO = "Algo salió mal. Intenta de nuevo."
    }
}