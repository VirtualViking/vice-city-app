package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.FranjaHoraria
import vicecity.web.tema.Colores

/* Gráficos y piezas del tablero analítico, dibujados con cajas (sin librerías de gráficos). */

/** Tarjeta de un indicador: título, valor grande y detalle. */
@Composable
fun Indicador(titulo: String, valor: String, detalle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Colores.Tarjeta, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titulo, color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = Colores.Texto, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(detalle, color = Colores.Apagado, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/**
 * Solicitudes por hora en el orden de la noche (de mediodía a mediodía). Siempre muestra de 6 p. m. a 3 a. m.,
 * y se amplía si hay solicitudes antes o después. La barra más alta va resaltada.
 * Debajo de cada barra va la hora en formato de 12 horas y, más abajo, una franja agrupa las horas p. m. y a. m.
 */
@Composable
fun BarrasFranjas(franjas: List<FranjaHoraria>, ancho: Boolean) {
    val enOrden = franjas.sortedBy { (it.hora - 12).mod(24) }
    val conDatos = enOrden.indices.filter { enOrden[it].solicitudes > 0 || enOrden[it].votos > 0 }
    if (conDatos.isEmpty()) {
        Text("Todavía no hay solicitudes en este periodo.", color = Colores.TextoSecundario, fontSize = 14.sp)
        return
    }
    // En el orden de la noche, la posición 6 es las 6 p. m. y la 15 es las 3 a. m.
    val visibles = enOrden.subList(minOf(6, conDatos.first()), maxOf(15, conDatos.last()) + 1)
    val maximo = visibles.maxOf { it.solicitudes }.coerceAtLeast(1)
    val altoMaximo = 150.dp
    val separacion = if (ancho) 8.dp else 3.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Ancho de cada barra, para que la franja de abajo quede alineada con sus horas
        val anchoBarra = (maxWidth - separacion * (visibles.size - 1)) / visibles.size
        Column {
            Row(Modifier.fillMaxWidth().height(altoMaximo + 64.dp), horizontalArrangement = Arrangement.spacedBy(separacion)) {
                visibles.forEach { f ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (f.solicitudes > 0) "${f.solicitudes}" else "", color = Colores.Texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.padding(top = 4.dp).fillMaxWidth()
                                .height((altoMaximo * (f.solicitudes.toFloat() / maximo)).coerceAtLeast(2.dp))
                                .background(if (f.solicitudes == maximo) Colores.Cian else Colores.Apagado.copy(alpha = 0.55f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        )
                        Text(
                            "${hora12(f.hora)}", color = Colores.TextoSecundario, fontSize = if (ancho) 12.sp else 10.sp,
                            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 6.dp).heightIn(min = 18.dp),
                        )
                    }
                }
            }
            // Franja p. m. / a. m.: de mediodía a medianoche es p. m.; de medianoche a mediodía, a. m.
            val horasPm = visibles.count { it.hora >= 12 }
            val horasAm = visibles.size - horasPm
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(separacion)) {
                if (horasPm > 0) {
                    GrupoHoras("p. m. · tarde y noche", "p. m.", Modifier.width(anchoBarra * horasPm + separacion * (horasPm - 1)))
                }
                if (horasAm > 0) {
                    GrupoHoras("a. m. · madrugada y mañana", "a. m.", Modifier.width(anchoBarra * horasAm + separacion * (horasAm - 1)))
                }
            }
        }
    }
}

/** Línea bajo un grupo de horas con su nombre centrado. Si el grupo es angosto, solo "a. m." o "p. m.". */
@Composable
private fun GrupoHoras(nombre: String, nombreCorto: String, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val texto = if (maxWidth >= 190.dp) nombre else nombreCorto
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Colores.Borde, RoundedCornerShape(50)))
            Text(
                texto, color = Colores.Apagado, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Hora en formato de 12 horas, sin a. m. ni p. m.: 0 y 12 son las 12. */
fun hora12(hora: Int): Int = if (hora % 12 == 0) 12 else hora % 12

/** "8 p. m." */
fun horaCorta(hora: Int): String = "${hora12(hora)} ${if (hora < 12) "a. m." else "p. m."}"

/** Fila de un ranking: nombre, valor a la derecha y barra proporcional. */
@Composable
fun BarraHorizontal(nombre: String, valor: String, fraccion: Float, color: Color = Colores.Cian) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nombre, color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(valor, color = Colores.TextoSecundario, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
        }
        BarraProporcion(fraccion, color)
    }
}

/** Barra de fondo apagado con la parte [fraccion] (0 a 1) de color. */
@Composable
fun BarraProporcion(fraccion: Float, color: Color, alto: Int = 8) {
    Row(Modifier.fillMaxWidth().height(alto.dp).background(Colores.TarjetaAlta, RoundedCornerShape(50))) {
        val f = fraccion.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(color, RoundedCornerShape(50)))
        if (f < 1f) Box(Modifier.weight(1f - f).fillMaxHeight())
    }
}

/** Barra dividida en dos partes, por ejemplo cola frente a lista por defecto. */
@Composable
fun BarraDividida(fraccionA: Float, colorA: Color, colorB: Color) {
    Row(Modifier.fillMaxWidth().height(28.dp).background(colorB, RoundedCornerShape(8.dp))) {
        val f = fraccionA.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(colorA, RoundedCornerShape(8.dp)))
        if (f < 1f) Box(Modifier.weight(1f - f).fillMaxHeight())
    }
}

/** Leyenda con un cuadrito de color. */
@Composable
fun Leyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(texto, color = Colores.Texto, fontSize = 14.sp)
    }
}

/** Selector de pestañas pequeño (por ejemplo Canción, Artista, Género). */
@Composable
fun Pestanas(opciones: List<String>, elegida: Int, alElegir: (Int) -> Unit) {
    Row(Modifier.background(Colores.Fondo, RoundedCornerShape(10.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEachIndexed { i, texto ->
            Text(
                texto,
                color = if (i == elegida) Colores.TextoSobreNeon else Colores.TextoSecundario,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(if (i == elegida) Colores.Cian else Colores.Fondo, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab) { alElegir(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Celda de texto de una tabla, con ancho fijo o flexible. */
@Composable
fun CeldaTabla(texto: String, modifier: Modifier, negrita: Boolean = false, color: Color = Colores.Texto) {
    Text(
        texto, color = color, fontSize = 13.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.padding(end = 8.dp),
    )
}

/** Fila de encabezado de una tabla. */
@Composable
fun EncabezadoTabla(columnas: List<Pair<String, Modifier>>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        columnas.forEach { (titulo, modifier) -> CeldaTabla(titulo, modifier, negrita = true, color = Colores.Apagado) }
    }
}

/** Separador entre filas. */
@Composable
fun Separador() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Colores.Borde))
}

/** 1158 -> "1.158" */
fun formatoNumero(n: Int): String {
    val texto = kotlin.math.abs(n).toString()
    val grupos = texto.reversed().chunked(3).joinToString(".").reversed()
    return if (n < 0) "-$grupos" else grupos
}

/** Ancho fijo de una columna. */
fun columna(ancho: Int): Modifier = Modifier.width(ancho.dp)