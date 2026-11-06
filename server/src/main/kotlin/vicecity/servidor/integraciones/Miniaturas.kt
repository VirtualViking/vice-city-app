package vicecity.servidor.integraciones

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Miniaturas de YouTube (i.ytimg.com) a través del backend.
 * El navegador no puede leer los bytes de una imagen de otro origen sin CORS, y Compose web los necesita
 * para dibujarla; el backend sí responde con CORS. Guarda en memoria las últimas [capacidad] miniaturas.
 */
class Miniaturas(
    private val http: HttpClient,
    private val urlBase: String = "https://i.ytimg.com/vi",
    private val capacidad: Int = 500,
) {
    private val cache = object : LinkedHashMap<String, ByteArray>(capacidad, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) = size > capacidad
    }

    /** JPEG de 320×180 del video, o null si el id no tiene un formato válido o YouTube no la tiene. */
    suspend fun de(videoId: String): ByteArray? {
        if (!FORMATO_VIDEO_ID.matches(videoId)) return null
        synchronized(cache) { cache[videoId] }?.let { return it }
        val bytes = try {
            val respuesta = http.get("$urlBase/$videoId/mqdefault.jpg")
            if (respuesta.status.isSuccess()) respuesta.readRawBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return bytes?.also { synchronized(cache) { cache[videoId] = it } }
    }

    private companion object {
        val FORMATO_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }
}