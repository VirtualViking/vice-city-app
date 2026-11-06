package vicecity.web.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as ImagenSkia
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores

/**
 * Guarda en memoria las últimas miniaturas descargadas, para no pedirlas de nuevo al volver a buscar.
 * Las imágenes llegan del backend (que sí permite CORS) y se decodifican con Skia, el motor de dibujo de Compose web.
 */
object CacheMiniaturas {
    private const val CAPACIDAD = 120
    private val imagenes = LinkedHashMap<String, ImageBitmap>()

    fun guardada(videoId: String): ImageBitmap? = imagenes[videoId]

    suspend fun cargar(api: ClienteApi, videoId: String): ImageBitmap? {
        imagenes[videoId]?.let { return it }
        val bytes = api.miniatura(videoId) ?: return null
        val imagen = runCatching { ImagenSkia.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() ?: return null
        imagenes[videoId] = imagen
        if (imagenes.size > CAPACIDAD) imagenes.remove(imagenes.keys.first())
        return imagen
    }
}

/** Miniatura del video. Mientras carga, o si no se pudo cargar, se ve un recuadro del color de los bordes. */
@Composable
fun Miniatura(api: ClienteApi, videoId: String, descripcion: String, modifier: Modifier) {
    var imagen by remember(videoId) { mutableStateOf(CacheMiniaturas.guardada(videoId)) }
    LaunchedEffect(videoId) {
        if (imagen == null) imagen = CacheMiniaturas.cargar(api, videoId)
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Colores.Borde)) {
        imagen?.let {
            Image(it, contentDescription = descripcion, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}