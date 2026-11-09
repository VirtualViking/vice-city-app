package vicecity.web.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Íconos dibujados con trazos simples (los mismos de los wireframes), sin depender de una librería de íconos.
 * Se dibujan sobre una cuadrícula de 24×24, como los SVG originales.
 */

@Composable
private fun Icono(tamano: Dp, color: Color, grosor: Float, trazos: DrawScope.(escala: Float, estilo: Stroke) -> Unit) {
    Canvas(Modifier.size(tamano)) {
        val escala = size.minDimension / 24f
        trazos(escala, Stroke(width = grosor * escala, cap = StrokeCap.Round))
    }
}

@Composable
fun IconoBuscar(color: Color, tamano: Dp = 20.dp) = Icono(tamano, color, 2f) { e, estilo ->
    drawCircle(color, radius = 7f * e, center = Offset(11f * e, 11f * e), style = estilo)
    drawLine(color, Offset(20f * e, 20f * e), Offset(16f * e, 16f * e), estilo.width, StrokeCap.Round)
}

@Composable
fun IconoCerrar(color: Color, tamano: Dp = 18.dp) = Icono(tamano, color, 2f) { e, estilo ->
    drawLine(color, Offset(6f * e, 6f * e), Offset(18f * e, 18f * e), estilo.width, StrokeCap.Round)
    drawLine(color, Offset(18f * e, 6f * e), Offset(6f * e, 18f * e), estilo.width, StrokeCap.Round)
}

@Composable
fun IconoMusica(color: Color, tamano: Dp = 40.dp) = Icono(tamano, color, 1.6f) { e, estilo ->
    val plica = Path().apply {
        moveTo(9f * e, 18f * e); lineTo(9f * e, 5f * e); lineTo(21f * e, 3f * e); lineTo(21f * e, 16f * e)
    }
    drawPath(plica, color, style = estilo)
    drawCircle(color, radius = 3f * e, center = Offset(6f * e, 18f * e), style = estilo)
    drawCircle(color, radius = 3f * e, center = Offset(18f * e, 16f * e), style = estilo)
}

@Composable
fun IconoInstalar(color: Color, tamano: Dp = 20.dp) = Icono(tamano, color, 2f) { e, estilo ->
    drawLine(color, Offset(12f * e, 3f * e), Offset(12f * e, 15f * e), estilo.width, StrokeCap.Round)
    drawPath(Path().apply { moveTo(7f * e, 8f * e); lineTo(12f * e, 3f * e); lineTo(17f * e, 8f * e) }, color, style = estilo)
    drawPath(
        Path().apply {
            moveTo(5f * e, 14f * e); lineTo(5f * e, 19f * e); lineTo(19f * e, 19f * e); lineTo(19f * e, 14f * e)
        },
        color, style = estilo,
    )
}

@Composable
fun IconoDescargar(color: Color, tamano: Dp = 20.dp) = Icono(tamano, color, 2f) { e, estilo ->
    drawLine(color, Offset(12f * e, 3f * e), Offset(12f * e, 15f * e), estilo.width, StrokeCap.Round)
    drawPath(Path().apply { moveTo(7f * e, 10f * e); lineTo(12f * e, 15f * e); lineTo(17f * e, 10f * e) }, color, style = estilo)
    drawPath(
        Path().apply {
            moveTo(5f * e, 14f * e); lineTo(5f * e, 19f * e); lineTo(19f * e, 19f * e); lineTo(19f * e, 14f * e)
        },
        color, style = estilo,
    )
}