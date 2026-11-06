package vicecity.web.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import vicecity.web.tema.Colores

/**
 * Título blanco con brillo rosa neón, usando la fuente normal.
 * Son tres capas del mismo texto: una sombra oscura para que se lea sobre el cielo claro,
 * un brillo rosa amplio y uno cercano. Solo la última la leen los lectores de pantalla.
 */
@Composable
fun TituloNeon(texto: String, tamano: TextUnit, modifier: Modifier = Modifier) {
    val densidad = LocalDensity.current
    val base = TextStyle(color = Color.White, fontSize = tamano, fontWeight = FontWeight.Black, lineHeight = tamano * 1.2f)
    val sombraOscura = with(densidad) { Shadow(SombraTitulo, Offset(0f, 2.dp.toPx()), 6.dp.toPx()) }
    val brilloAmplio = with(densidad) { Shadow(Colores.Rosa.copy(alpha = 0.6f), Offset.Zero, 16.dp.toPx()) }
    val brilloCercano = with(densidad) { Shadow(Colores.Rosa.copy(alpha = 0.95f), Offset.Zero, 6.dp.toPx()) }

    Box(modifier) {
        Text(texto, style = base.copy(shadow = sombraOscura), modifier = Modifier.clearAndSetSemantics { })
        Text(texto, style = base.copy(shadow = brilloAmplio), modifier = Modifier.clearAndSetSemantics { })
        Text(texto, style = base.copy(shadow = brilloCercano), modifier = Modifier.semantics { heading() })
    }
}

private val SombraTitulo = Color(0x732B184E)