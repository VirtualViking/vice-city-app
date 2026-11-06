package vicecity.web.componentes

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.unit.Dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/**
 * Brillo tipo neón alrededor del elemento: un rectángulo redondeado desenfocado que se dibuja detrás.
 * Va antes de `clip` en la cadena de modificadores, porque el brillo se sale del borde.
 */
fun Modifier.brilloNeon(color: Color, radio: Dp, esquina: Dp): Modifier = drawBehind {
    val esquinaPx = esquina.toPx()
    val pintura = Paint().apply { this.color = color }
    // El desenfoque lo hace Skia, el motor de dibujo de Compose en la web
    pintura.skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, radio.toPx() / 2f)
    drawIntoCanvas { lienzo ->
        lienzo.drawRoundRect(0f, 0f, size.width, size.height, esquinaPx, esquinaPx, pintura)
    }
}