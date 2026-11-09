package vicecity.web.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.ceil
import kotlin.math.roundToInt
import vicecity.web.tema.Colores

/*
 * Atardecer del encabezado: cielo, sol, ciudad, agua y palmeras, dibujados con código.
 * Las figuras están pensadas sobre un lienzo de referencia de 240 de alto (horizonte en 196)
 * y se escalan con la altura real; a lo ancho, la ciudad se repite y las palmeras se pegan a los bordes.
 */

private const val ALTO_REFERENCIA = 240f
private const val HORIZONTE = 196f

private val CieloArriba = Color(0xFF6E78B4)
private val CieloLila = Color(0xFF9586C4)
private val CieloRosa = Color(0xFFD48BBB)
private val CieloCoral = Color(0xFFF6A08F)
private val SolCentro = Color(0xFFFFF0C2)
private val SolBorde = Color(0xFFFF9C7A)
private val Agua = Color(0xFFC68AB0)
private val Reflejo = Color(0xFFFFD3A0)
private val CiudadLejana = Color(0xFF8F7BBE)
private val CiudadCercana = Color(0xFF6E5DA0)
private val Palmera = Color(0xFF46397A)

/**
 * Encabezado ilustrado. La altura la decide quien lo usa; el dibujo se adapta.
 * [altoEscena] es el alto de la ciudad, el agua y las palmeras: si el encabezado es más alto,
 * el cielo se extiende hacia arriba (así se usa en la bienvenida). Sin indicarlo, la escena ocupa todo el alto.
 */
@Composable
fun FondoAtardecer(modifier: Modifier = Modifier, altoEscena: Dp = Dp.Unspecified) {
    // clipToBounds: los troncos de las palmeras no se salen del encabezado
    Canvas(modifier.clipToBounds()) {
        val alto = if (altoEscena.isSpecified) altoEscena.toPx().coerceAtMost(size.height) else size.height
        dibujarAtardecer(alto)
    }
}

private fun DrawScope.dibujarAtardecer(altoEscena: Float) {
    val e = altoEscena / ALTO_REFERENCIA
    // Cielo extra por encima de la escena (0 cuando la escena ocupa todo el alto)
    val arriba = size.height - altoEscena
    val horizonte = arriba + HORIZONTE * e
    val anchoReferencia = size.width / e
    // Ciudad: el bloque de referencia (x 30 a 360) se repite a lo ancho, alternando con su reflejo
    val bloques = if (anchoReferencia <= 420f) 1 else ceil(anchoReferencia / 320f).toInt()
    // El sol va sobre la parte baja del bloque más cercano al centro, para que se vea entre los edificios
    val bloqueCentral = ((anchoReferencia / 2f - 195f) / 320f).roundToInt().coerceIn(0, bloques - 1)
    val solX = (bloqueCentral * 320f + if (bloqueCentral % 2 == 0) 210f else 180f) * e

    // Cielo
    drawRect(
        Brush.verticalGradient(
            0f to CieloArriba, 0.35f to CieloLila, 0.6f to CieloRosa, 0.8f to CieloCoral, 1f to Colores.Durazno,
            startY = 0f, endY = horizonte,
        ),
        size = Size(size.width, horizonte),
    )

    // Sol, detrás de la ciudad
    drawCircle(
        Brush.radialGradient(0f to SolCentro, 0.6f to Colores.Durazno, 1f to SolBorde, center = Offset(solX, horizonte), radius = 40f * e),
        radius = 40f * e,
        center = Offset(solX, horizonte),
    )

    // Ciudad
    repeat(bloques) { i ->
        val inicio = i * 320f
        withTransform({
            if (i % 2 == 0) {
                translate(left = inicio * e, top = arriba)
                scale(e, e, pivot = Offset.Zero)
            } else {
                translate(left = (inicio + 390f) * e, top = arriba)
                scale(-e, e, pivot = Offset.Zero)
            }
        }) {
            drawPath(Formas.ciudadLejana, CiudadLejana)
            drawPath(Formas.ciudadCercana, CiudadCercana)
        }
    }

    // Agua y reflejos del sol
    drawRect(
        Brush.verticalGradient(listOf(Agua, Colores.Fondo), startY = horizonte, endY = size.height),
        topLeft = Offset(0f, horizonte),
        size = Size(size.width, size.height - horizonte),
    )
    drawRect(Reflejo.copy(alpha = 0.8f), topLeft = Offset(0f, horizonte - e), size = Size(size.width, 2f * e))
    listOf(Triple(60f, 6f, 0.6f), Triple(44f, 12f, 0.45f), Triple(28f, 18f, 0.3f)).forEach { (ancho, bajada, alfa) ->
        drawRect(
            Reflejo.copy(alpha = alfa),
            topLeft = Offset(solX - ancho * e / 2f, horizonte + bajada * e),
            size = Size(ancho * e, 2f * e),
        )
    }

    // Palmeras: a la derecha, medidas desde el borde derecho; a la izquierda, reflejadas
    palmera(x = size.width - 122f * e, y = arriba + 30f * e, escala = 0.95f * e)
    palmera(x = size.width - 72f * e, y = arriba + 92f * e, escala = 0.6f * e)
    palmera(x = 70f * e, y = arriba + 96f * e, escala = 0.75f * e, reflejada = true)
    if (anchoReferencia > 700f) {
        palmera(x = size.width - 200f * e, y = arriba + 118f * e, escala = 0.5f * e)
        palmera(x = 150f * e, y = arriba + 128f * e, escala = 0.55f * e, reflejada = true)
    }

    // Transición suave hacia el fondo de la pantalla
    val inicioTransicion = size.height - 26f * e
    drawRect(
        Brush.verticalGradient(listOf(Colores.Fondo.copy(alpha = 0f), Colores.Fondo), startY = inicioTransicion, endY = size.height),
        topLeft = Offset(0f, inicioTransicion),
        size = Size(size.width, size.height - inicioTransicion),
    )
}

/** Palmera de referencia (140 × 220, copa en 78, 52), trasladada a [x], [y] y escalada. */
private fun DrawScope.palmera(x: Float, y: Float, escala: Float, reflejada: Boolean = false) {
    withTransform({
        translate(left = x, top = y)
        scale(if (reflejada) -escala else escala, escala, pivot = Offset.Zero)
    }) {
        drawPath(Formas.tronco, Palmera)
        ANGULOS_FRONDAS.forEach { angulo ->
            fronda(angulo, espejo = false)
            fronda(angulo, espejo = true)
        }
        drawCircle(Palmera, radius = 3f, center = Offset(76f, 57f))
        drawCircle(Palmera, radius = 3f, center = Offset(81f, 58f))
    }
}

private fun DrawScope.fronda(angulo: Float, espejo: Boolean) {
    withTransform({
        translate(left = 78f, top = 52f)
        if (espejo) scale(-1f, 1f, pivot = Offset.Zero)
        rotate(angulo, pivot = Offset.Zero)
    }) {
        drawPath(Formas.fronda, Palmera)
    }
}

private val ANGULOS_FRONDAS = listOf(-75f, -40f, -12f, 18f, 48f)

/** Figuras de referencia, creadas una sola vez. */
private object Formas {
    /** Hoja de palmera apuntando a la derecha y caída; el borde inferior dentado imita las hojuelas. */
    val fronda = Path().apply {
        moveTo(0f, 0f)
        cubicTo(18f, -10f, 44f, -8f, 70f, 20f)
        lineTo(62f, 14f); lineTo(60f, 20f); lineTo(52f, 10f); lineTo(49f, 17f); lineTo(42f, 7f)
        lineTo(38f, 14f); lineTo(31f, 5f); lineTo(27f, 11f); lineTo(20f, 3f); lineTo(15f, 8f)
        cubicTo(10f, 4f, 5f, 3f, 0f, 4f)
        close()
    }

    val tronco = Path().apply {
        moveTo(62f, 220f)
        cubicTo(64f, 170f, 70f, 110f, 75f, 54f)
        lineTo(82f, 54f)
        cubicTo(78f, 110f, 73f, 170f, 72f, 220f)
        close()
    }

    val ciudadLejana = silueta(
        40f, 198f,
        listOf(
            176f, 52f, 160f, 60f, 150f, 66f, 160f, 74f, 170f, 86f, 146f, 92f, 132f, 96f, 124f, 100f, 132f, 104f,
            146f, 112f, 168f, 122f, 156f, 134f, 176f, 146f, 164f, 156f, 184f, 170f, 190f, 250f, 178f, 262f,
            152f, 268f, 140f, 272f, 130f, 276f, 140f, 280f, 152f, 292f, 170f, 302f, 158f, 314f, 180f, 326f,
            168f, 340f, 186f, 360f, 198f,
        ),
    )

    val ciudadCercana = silueta(
        30f, 198f,
        listOf(
            186f, 44f, 176f, 58f, 188f, 72f, 180f, 88f, 190f, 104f, 178f, 118f, 192f, 136f, 184f, 150f,
            194f, 260f, 186f, 274f, 174f, 290f, 188f, 304f, 180f, 320f, 192f, 338f, 184f, 352f, 198f,
        ),
    )

    /**
     * Silueta escalonada de edificios: parte de ([x], [base]) y recibe alturas y posiciones alternadas
     * (sube o baja a una altura, avanza a una x, y así), como las órdenes V y H de un SVG.
     */
    private fun silueta(x: Float, base: Float, alternados: List<Float>) = Path().apply {
        moveTo(x, base)
        var actualX = x
        var actualY = base
        alternados.forEachIndexed { i, valor ->
            if (i % 2 == 0) actualY = valor else actualX = valor
            lineTo(actualX, actualY)
        }
        close()
    }
}