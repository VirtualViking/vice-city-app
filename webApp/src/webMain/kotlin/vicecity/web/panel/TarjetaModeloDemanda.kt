package vicecity.web.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.MetodoPico
import vicecity.compartido.ModeloDemanda
import vicecity.web.tema.Colores
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Demanda de la noche: barras con las solicitudes reales por hora, la tendencia (el polinomio ajustado por mínimos
 * cuadrados) y la hora pico. Debajo, las solicitudes estimadas del intervalo que se elija; esa integral (Simpson)
 * se calcula aquí mismo con el código del módulo shared. Al administrador solo se le muestran los resultados.
 */
@Composable
fun TarjetaModeloDemanda(modelo: ModeloDemanda?, ancho: Boolean) {
    Tarjeta("Demanda de la noche") {
        if (modelo == null) {
            Text(
                "Se necesitan solicitudes en un rango de al menos 4 horas de la noche para estimar la demanda.",
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
            return@Tarjeta
        }
        val porNoche = if (modelo.noches > 1) " Promedio por noche de ${modelo.noches} sesiones." else ""
        Text(
            "Cómo se reparten las solicitudes durante la noche.$porNoche Elige un horario para ver cuántas solicitudes se esperan.",
            color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
        )

        // Intervalo: por defecto de 10 p. m. a 2 a. m., recortado a las horas con datos
        val inicioVentana = modelo.desde.roundToInt()
        val finVentana = modelo.hasta.roundToInt()
        val porDefecto = remember(modelo) { intervaloPorDefecto(inicioVentana, finVentana) }
        var desde by remember(modelo) { mutableStateOf(porDefecto.first) }
        var hasta by remember(modelo) { mutableStateOf(porDefecto.second) }

        CurvaDemanda(modelo, desde, hasta, if (ancho) 220 else 170)
        Row(Modifier.fillMaxWidth()) {
            (inicioVentana until finVentana).forEach { x ->
                Text(
                    "${hora12(horaReloj(x))}", color = Colores.TextoSecundario, fontSize = if (ancho) 12.sp else 10.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Leyenda("Solicitudes reales", Colores.Cian)
            Leyenda("Tendencia", Colores.Rosa)
            Leyenda("Hora pico", Colores.Durazno)
        }

        val pico = modelo.pico
        val intervalo = modelo.demanda(desde.toDouble(), hasta.toDouble())
        val unaNoche = modelo.noches == 1
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = if (ancho) 2 else 1,
        ) {
            Resultado(
                "Hora pico estimada",
                horaExacta(pico.x),
                when {
                    pico.metodo != MetodoPico.BORDE -> "El momento con más solicitudes: unas ${pico.solicitudes.roundToInt()} por hora."
                    pico.x <= modelo.desde -> "Las solicitudes fueron bajando desde el comienzo de la noche."
                    else -> "Las solicitudes siguieron subiendo hasta el final de la noche."
                },
                Modifier.weight(1f),
            )
            Resultado(
                "Solicitudes de ${horaCorta(horaReloj(desde))} a ${horaCorta(horaReloj(hasta))}",
                intervalo?.let { "Unas ${it.simpson.roundToInt()}" + if (unaNoche) "" else " por noche" } ?: "Sin datos",
                intervalo?.let {
                    if (unaNoche) "Hubo ${it.reales.roundToInt()} en ese horario."
                    else "En promedio hubo ${decimal(it.reales)} por noche en ese horario."
                }.orEmpty(),
                Modifier.weight(1f),
            )
        }

        Text("Desde", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (inicioVentana until finVentana).forEach { x ->
                Chip(horaCorta(horaReloj(x)), elegido = x == desde, alElegir = {
                    desde = x
                    if (hasta <= x) hasta = x + 1
                })
            }
        }
        Text("Hasta", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (inicioVentana + 1..finVentana).forEach { x ->
                Chip(horaCorta(horaReloj(x)), elegido = x == hasta, alElegir = {
                    hasta = x
                    if (desde >= x) desde = x - 1
                })
            }
        }
    }
}

/** Barras de las solicitudes reales, la curva ajustada (cada 5 minutos), el intervalo elegido sombreado y la hora pico. */
@Composable
private fun CurvaDemanda(modelo: ModeloDemanda, desde: Int, hasta: Int, alto: Int) {
    val muestras = remember(modelo) {
        val pasos = ((modelo.hasta - modelo.desde) * 12).roundToInt()
        (0..pasos).map { i -> modelo.desde + i / 12.0 }.map { x -> x to modelo.valor(x) }
    }
    val maximo = remember(modelo) {
        max(1.0, maxOf(modelo.puntos.maxOf { it.solicitudes }, muestras.maxOf { it.second }, modelo.pico.solicitudes) * 1.15)
    }
    val descripcion = "Curva de demanda por hora; hora pico estimada ${horaExacta(modelo.pico.x)}"
    Canvas(Modifier.fillMaxWidth().height(alto.dp).semantics { contentDescription = descripcion }) {
        val rango = modelo.hasta - modelo.desde
        fun px(x: Double) = ((x - modelo.desde) / rango * size.width).toFloat()
        fun py(y: Double) = (size.height - y / maximo * size.height).toFloat()
        val anchoHora = size.width / rango.toFloat()

        drawRect(Colores.Rosa.copy(alpha = 0.10f), topLeft = Offset(px(desde.toDouble()), 0f), size = Size(px(hasta.toDouble()) - px(desde.toDouble()), size.height))
        modelo.puntos.forEach { p ->
            val dentro = p.x > desde && p.x < hasta
            val arriba = py(p.solicitudes)
            drawRoundRect(
                Colores.Cian.copy(alpha = if (dentro) 0.75f else 0.30f),
                topLeft = Offset(px(p.x - 0.5) + anchoHora * 0.14f, arriba),
                size = Size(anchoHora * 0.72f, size.height - arriba),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        val curva = Path()
        muestras.forEachIndexed { i, (x, y) -> if (i == 0) curva.moveTo(px(x), py(y)) else curva.lineTo(px(x), py(y)) }
        drawPath(curva, Colores.Rosa, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val xPico = px(modelo.pico.x)
        drawLine(Colores.Durazno.copy(alpha = 0.7f), Offset(xPico, 0f), Offset(xPico, size.height), strokeWidth = 1.5.dp.toPx())
        drawCircle(Colores.Durazno, radius = 6.dp.toPx(), center = Offset(xPico, py(modelo.pico.solicitudes)))
    }
}

@Composable
private fun Resultado(titulo: String, valor: String, detalle: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, color = Colores.Apagado, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = Colores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(detalle, color = Colores.TextoSecundario, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** Horas desde el mediodía → hora del reloj (0 a 23). */
private fun horaReloj(x: Int): Int = (x + ModeloDemanda.MEDIODIA).mod(24)

/** 10,42 horas desde el mediodía → "10:25 p. m." */
private fun horaExacta(x: Double): String {
    val total = (ModeloDemanda.MEDIODIA * 60 + (x * 60).roundToInt()).mod(24 * 60)
    val hora = total / 60
    return "${hora12(hora)}:${(total % 60).toString().padStart(2, '0')} ${if (hora < 12) "a. m." else "p. m."}"
}

/** 34,041 → "34,0" (con [decimales] cifras y coma decimal). */
private fun decimal(valor: Double, decimales: Int = 1): String {
    var factor = 1L
    repeat(decimales) { factor *= 10 }
    val redondeado = (valor * factor).roundToLong()
    val signo = if (redondeado < 0) "-" else ""
    val entero = abs(redondeado) / factor
    if (decimales == 0) return "$signo$entero"
    return "$signo$entero,${(abs(redondeado) % factor).toString().padStart(decimales, '0')}"
}

/** De 10 p. m. a 2 a. m. (10 y 14 horas desde el mediodía), recortado a la ventana con datos; si no queda nada, toda la ventana. */
private fun intervaloPorDefecto(inicioVentana: Int, finVentana: Int): Pair<Int, Int> {
    val desde = max(DESDE_POR_DEFECTO, inicioVentana)
    val hasta = min(HASTA_POR_DEFECTO, finVentana)
    return if (hasta - desde >= 1) desde to hasta else inicioVentana to finVentana
}

private const val DESDE_POR_DEFECTO = 10
private const val HASTA_POR_DEFECTO = 14