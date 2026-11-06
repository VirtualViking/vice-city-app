package vicecity.servidor.integraciones

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.time.Duration

/**
 * Catálogo sobre la YouTube Data API v3.
 * - search.list (100 unidades de cupo): solo videos de la categoría Música (10) que se pueden incrustar.
 * - videos.list (1 unidad): duración, estado, restricciones por país y temas, que search.list no trae.
 * La clave viaja en el header x-goog-api-key y no en la URL, para que nunca aparezca en logs ni en errores.
 */
class YouTubeCatalogo(
    private val apiKey: String,
    private val http: HttpClient,
    private val region: String = "CO",
    private val urlBase: String = "https://www.googleapis.com/youtube/v3",
) : CatalogoVideos {

    override suspend fun buscar(termino: String, maxResultados: Int): List<VideoCatalogo> {
        val ids = llamar<RespuestaSearch>("search") {
            parameter("part", "id")
            parameter("type", "video")
            parameter("videoCategoryId", CATEGORIA_MUSICA)
            parameter("videoEmbeddable", "true")
            parameter("regionCode", region)
            parameter("maxResults", maxResultados)
            parameter("q", termino)
        }.items.mapNotNull { it.id.videoId }.distinct()
        if (ids.isEmpty()) return emptyList()

        val detalles = llamar<RespuestaVideos>("videos") {
            parameter("part", "snippet,contentDetails,status,topicDetails")
            parameter("id", ids.joinToString(","))
        }.items.associateBy { it.id }

        // Se respeta el orden de relevancia de search.list
        return ids.mapNotNull { detalles[it] }.filter(::esReproducible).map(::aVideo)
    }

    /** Solo música que se puede incrustar, pública, que no sea transmisión en vivo y disponible en la región. */
    private fun esReproducible(video: VideoYT): Boolean {
        val restriccion = video.contentDetails.regionRestriction
        val disponibleEnRegion = restriccion == null ||
                (region !in restriccion.blocked && (restriccion.allowed == null || region in restriccion.allowed))
        return video.snippet.categoryId == CATEGORIA_MUSICA &&
                video.status.embeddable &&
                video.status.privacyStatus in PRIVACIDAD_PERMITIDA &&
                video.snippet.liveBroadcastContent == "none" &&
                duracionEnSegundos(video.contentDetails.duration) > 0 &&
                disponibleEnRegion
    }

    private fun aVideo(video: VideoYT) = VideoCatalogo(
        videoId = video.id,
        titulo = video.snippet.title,
        canal = video.snippet.channelTitle,
        miniaturaUrl = video.snippet.thumbnails.mejor()?.url ?: "https://i.ytimg.com/vi/${video.id}/mqdefault.jpg",
        duracionSeg = duracionEnSegundos(video.contentDetails.duration),
        categorias = video.topicDetails?.topicCategories.orEmpty().mapNotNull(::nombreDeTema).distinct().take(5),
    )

    private suspend inline fun <reified T> llamar(recurso: String, crossinline parametros: HttpRequestBuilder.() -> Unit): T {
        val respuesta: HttpResponse = try {
            http.get("$urlBase/$recurso") {
                header(HEADER_CLAVE, apiKey)
                parametros()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw CatalogoNoDisponible("YouTube no respondió (${e::class.simpleName})", e)
        }
        if (!respuesta.status.isSuccess()) {
            val motivo = runCatching { respuesta.body<RespuestaError>().error.errors.firstOrNull()?.reason }.getOrNull()
            throw CatalogoNoDisponible("YouTube respondió ${respuesta.status.value} (${motivo ?: "sin motivo"})")
        }
        return respuesta.body()
    }

    companion object {
        private const val CATEGORIA_MUSICA = "10"
        private const val HEADER_CLAVE = "x-goog-api-key"
        private val PRIVACIDAD_PERMITIDA = setOf("public", "unlisted")
        private const val PREFIJO_TEMA = "https://en.wikipedia.org/wiki/"

        /** Duración ISO 8601 de YouTube ("PT3M45S") en segundos; 0 si no se puede leer o es una transmisión ("P0D"). */
        fun duracionEnSegundos(iso: String?): Int =
            iso?.let { runCatching { Duration.parse(it).seconds.toInt() }.getOrNull() } ?: 0

        /** "https://en.wikipedia.org/wiki/Pop_music" → "Pop music". Se descarta el tema genérico "Music". */
        fun nombreDeTema(url: String): String? {
            if (!url.startsWith(PREFIJO_TEMA)) return null
            val nombre = URLDecoder.decode(url.removePrefix(PREFIJO_TEMA), Charsets.UTF_8).replace('_', ' ').trim()
            return nombre.takeIf { it.isNotEmpty() && it != "Music" && it.length <= 60 }
        }
    }
}

// ---------- Respuestas de la API (solo los campos que se usan)

@Serializable
private data class RespuestaSearch(val items: List<ItemSearch> = emptyList())

@Serializable
private data class ItemSearch(val id: IdSearch)

@Serializable
private data class IdSearch(val videoId: String? = null)

@Serializable
private data class RespuestaVideos(val items: List<VideoYT> = emptyList())

@Serializable
private data class VideoYT(
    val id: String,
    val snippet: SnippetYT,
    val contentDetails: DetallesYT,
    val status: EstadoYT,
    val topicDetails: TemasYT? = null,
)

@Serializable
private data class SnippetYT(
    val title: String,
    val channelTitle: String,
    val categoryId: String = "",
    val liveBroadcastContent: String = "none",
    val thumbnails: MiniaturasYT = MiniaturasYT(),
)

@Serializable
private data class MiniaturasYT(
    val medium: MiniaturaYT? = null,
    val high: MiniaturaYT? = null,
    val default: MiniaturaYT? = null,
) {
    /** "medium" (320×180) basta para las tarjetas y pesa poco en datos móviles. */
    fun mejor(): MiniaturaYT? = medium ?: high ?: default
}

@Serializable
private data class MiniaturaYT(val url: String)

@Serializable
private data class DetallesYT(
    val duration: String? = null,
    val regionRestriction: RestriccionYT? = null,
)

@Serializable
private data class RestriccionYT(
    val allowed: List<String>? = null,
    val blocked: List<String> = emptyList(),
)

@Serializable
private data class EstadoYT(
    val embeddable: Boolean = false,
    val privacyStatus: String = "",
)

@Serializable
private data class TemasYT(val topicCategories: List<String>? = null)

@Serializable
private data class RespuestaError(val error: DetalleError)

@Serializable
private data class DetalleError(val errors: List<MotivoError> = emptyList())

@Serializable
private data class MotivoError(val reason: String? = null)