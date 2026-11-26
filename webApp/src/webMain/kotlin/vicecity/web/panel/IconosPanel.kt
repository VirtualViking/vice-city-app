package vicecity.web.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Íconos de línea del menú del panel, dibujados en una cuadrícula de 24 x 24 con trazo de 2 y puntas redondeadas.
 * Cada uno es un trazo en formato de ruta SVG; los círculos y rectángulos redondeados se escriben como arcos.
 */
enum class IconoPanel(private val trazo: String) {
    /** Círculo con el triángulo de reproducir. */
    SESION("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M10 8.5L15.5 12L10 15.5Z"),

    /** Lista con una nota musical. */
    LISTA_DEFECTO("M4 6H14M4 11H14M4 16H10M14 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0M19 17V6L21.5 7"),

    /** Calendario: el horario de apertura y cierre automático. */
    OPERACION(
        "M5.5 5H18.5A2.5 2.5 0 0 1 21 7.5V18.5A2.5 2.5 0 0 1 18.5 21H5.5A2.5 2.5 0 0 1 3 18.5V7.5A2.5 2.5 0 0 1 5.5 5Z" +
                "M3 10H21M8 3V7M16 3V7M8 14.5H8.01M12 14.5H12.01M16 14.5H16.01M8 18H8.01M12 18H12.01",
    ),

    /** Tres controles deslizantes. */
    CONFIGURACION(
        "M4 7H7M11 7H20M4 12H13M17 12H20M4 17H5M9 17H20" +
                "M7 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M13 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M5 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    ),

    /** Dos personas. */
    PERSONAL(
        "M5.5 8a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0M3 20C3 16.7 5.7 14 9 14C12.3 14 15 16.7 15 20" +
                "M15.5 4.6C17.2 5 18.5 6.5 18.5 8.3C18.5 10.1 17.2 11.6 15.5 12M17.5 14.4C19.6 15.1 21 17.3 21 20",
    ),

    /** Gráfica de barras. */
    ANALITICA("M3 20H21M7 16V11M12 16V5M17 16V8"),

    /** Puerta con flecha hacia afuera. */
    SALIR("M10 4H6C4.9 4 4 4.9 4 6V18C4 19.1 4.9 20 6 20H10M10 12H20M16 8L20 12L16 16");

    /** La ruta se interpreta una sola vez y se reutiliza en cada dibujo. */
    val ruta: Path by lazy { PathParser().parsePathString(trazo).toPath() }
}

private const val CUADRICULA = 24f
private val TrazoIcono = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Dibuja [icono] del color indicado. El tamaño lo da el [modifier] (22 dp por defecto, como en el menú). */
@Composable
fun IconoTrazo(icono: IconoPanel, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        scale(size.width / CUADRICULA, pivot = Offset.Zero) {
            drawPath(icono.ruta, color, style = TrazoIcono)
        }
    }
}