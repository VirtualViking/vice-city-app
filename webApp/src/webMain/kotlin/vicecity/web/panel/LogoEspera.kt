package vicecity.web.panel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores
import org.jetbrains.skia.Image as ImagenSkia

/**
 * Logo de Vice City App en el espacio libre de la sección Sesión. Es decorativo: los lectores de pantalla lo ignoran.
 * Se centra en el alto que le den y, si no cabe, no se dibuja. Los bordes del ícono se funden con el fondo del panel.
 */
@Composable
fun LogoEspera(api: ClienteApi, modifier: Modifier = Modifier) {
    var icono by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        icono = api.iconoApp(512)?.let { bytes -> runCatching { ImagenSkia.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() }
    }
    // Aparece con un desvanecimiento suave cuando el ícono termina de cargar
    val opacidad by animateFloatAsState(if (icono != null) 1f else 0f, tween(600))

    BoxWithConstraints(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val lado = minOf(maxHeight * 0.8f, maxWidth * 0.55f, 380.dp)
        val imagen = icono
        if (imagen == null || lado < LADO_MINIMO) return@BoxWithConstraints

        Image(
            imagen,
            contentDescription = null,
            modifier = Modifier
                .size(lado)
                .graphicsLayer { alpha = opacidad }
                // Brillo rosa detrás del sol
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            0f to Colores.Rosa.copy(alpha = 0.16f), 1f to Color.Transparent,
                            center = center, radius = size.minDimension * 0.75f,
                        ),
                        radius = size.minDimension * 0.75f,
                    )
                }
                // Los bordes se desvanecen: el ícono no se ve como un cuadrado sobre el fondo
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent,
                            center = center, radius = size.minDimension / 2,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

/** Por debajo de este tamaño el logo no se dibuja: el espacio libre es muy poco. */
private val LADO_MINIMO = 150.dp