package vicecity.web.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.componentes.TituloNeon
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores
import org.jetbrains.skia.Image as ImagenSkia

/** Una opción del menú: la sección que abre, su nombre y su ícono. */
data class OpcionMenu(val seccion: SeccionPanel, val nombre: String, val icono: IconoPanel)

/** Separa las secciones del DJ (Sesión y Lista por defecto) de las que solo ve el administrador. */
private val SECCIONES_DJ = setOf(SeccionPanel.SESION, SeccionPanel.LISTA_DEFECTO)

/** Alto del menú sin la ilustración: debajo de esto la ilustración se recorta y, si queda muy baja, no se muestra. */
private val ALTO_MENU = 590.dp
private val ALTO_MINIMO_ILUSTRACION = 150.dp

/**
 * Menú lateral del panel en pantallas anchas: marca de la app, local y cuenta, secciones con ícono
 * y, al fondo, un atardecer con palmeras. La ilustración se dibuja con código, sin imágenes.
 */
@Composable
fun MenuLateral(
    api: ClienteApi,
    nombreLocal: String,
    quien: String,
    opciones: List<OpcionMenu>,
    elegida: SeccionPanel,
    alElegir: (SeccionPanel) -> Unit,
    alSalir: () -> Unit,
) {
    var iconoApp by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        iconoApp = api.iconoApp()?.let { bytes -> runCatching { ImagenSkia.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() }
    }

    BoxWithConstraints(Modifier.width(250.dp).fillMaxHeight().background(Colores.Tarjeta)) {
        // La escena mide 340 dp; en pantallas bajas se recorta el cielo para no tapar el menú
        val altoIlustracion = minOf(ALTO_ESCENA.dp, maxHeight - ALTO_MENU)
        if (altoIlustracion >= ALTO_MINIMO_ILUSTRACION) {
            AtardecerPanel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(altoIlustracion))
        }

        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val icono = iconoApp
                if (icono != null) {
                    Image(icono, contentDescription = "Ícono de Vice City App", modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)))
                } else {
                    Box(Modifier.size(44.dp).background(Colores.TarjetaAlta, RoundedCornerShape(11.dp)))
                }
                TituloNeon("Vice City", tamano = 22.sp)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                nombreLocal, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 23.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(quien, color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(22.dp))
            Linea(Modifier.fillMaxWidth())
            Spacer(Modifier.height(22.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                opciones.forEachIndexed { i, opcion ->
                    // Línea entre las secciones del DJ y las del administrador
                    val anterior = opciones.getOrNull(i - 1)
                    if (anterior != null && anterior.seccion in SECCIONES_DJ && opcion.seccion !in SECCIONES_DJ) {
                        Linea(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    ElementoMenu(opcion.nombre, opcion.icono, elegido = elegida == opcion.seccion, alElegir = { alElegir(opcion.seccion) })
                }
            }

            Spacer(Modifier.weight(1f))
            ElementoMenu("Salir de la cuenta", IconoPanel.SALIR, elegido = false, alElegir = alSalir, rol = Role.Button)
        }
    }
}

@Composable
private fun ElementoMenu(texto: String, icono: IconoPanel, elegido: Boolean, alElegir: () -> Unit, rol: Role = Role.Tab) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (elegido) Colores.TarjetaAlta else Color.Transparent)
            .clickable(role = rol, onClick = alElegir)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconoTrazo(icono, if (elegido) Colores.Rosa else Colores.TextoSecundario)
        Text(
            texto, color = if (elegido) Colores.Rosa else Colores.Texto, fontSize = 15.sp, lineHeight = 22.sp,
            fontWeight = if (elegido) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Linea(modifier: Modifier) {
    Box(modifier.height(1.dp).background(Colores.Borde))
}

// ---------- Atardecer con palmeras (escena de 250 x 340 unidades, igual que el diseño)

private const val ANCHO_ESCENA = 250f
private const val ALTO_ESCENA = 340f
private val CentroSol = Offset(125f, 180f)
private const val RADIO_SOL = 72f
private val Suelo = Color(0xFF1A1330)
private val Palma = Color(0xFF120B24)

private val Tronco: Path by lazy { PathParser().parsePathString("M62 220 C64 170 70 110 75 54 L82 54 C78 110 73 170 72 220 Z").toPath() }
private val Hoja: Path by lazy {
    PathParser().parsePathString(
        "M0 0 C18 -10 44 -8 70 20 L62 14 L60 20 L52 10 L49 17 L42 7 L38 14 L31 5 L27 11 L20 3 L15 8 C10 4 5 3 0 4 Z",
    ).toPath()
}
private val Sol: Path by lazy { Path().apply { addOval(Rect(CentroSol, RADIO_SOL)) } }

/** Ángulos de las hojas de cada palmera; cada una se dibuja también reflejada. */
private val ANGULOS_HOJAS = listOf(-75f, -40f, -12f, 18f, 48f)

/**
 * El sol con franjas, el horizonte rosa con su reflejo y dos palmeras en silueta.
 * Se ancla abajo: si no cabe, se recorta arriba y el borde se funde con el fondo del panel.
 */
@Composable
private fun AtardecerPanel(modifier: Modifier) {
    Canvas(modifier.clipToBounds()) {
        val escala = size.width / ANCHO_ESCENA
        translate(top = size.height - ALTO_ESCENA * escala) {
            scale(escala, pivot = Offset.Zero) {
                escena()
            }
        }
        // Fundido superior: evita un corte recto cuando la escena se recorta en pantallas bajas
        val fundido = 70.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(Colores.Tarjeta, Colores.Tarjeta.copy(alpha = 0f)), startY = 0f, endY = fundido), size = Size(size.width, fundido))
    }
}

private fun DrawScope.escena() {
    // Brillo rosa alrededor del sol
    drawCircle(
        Brush.radialGradient(listOf(Colores.Rosa.copy(alpha = 0.4f), Colores.Rosa.copy(alpha = 0f)), center = CentroSol, radius = 138f),
        radius = 138f, center = CentroSol,
    )
    drawCircle(
        Brush.verticalGradient(listOf(Colores.Durazno, Colores.Coral, Colores.Rosa), startY = CentroSol.y - RADIO_SOL, endY = CentroSol.y + RADIO_SOL),
        radius = RADIO_SOL, center = CentroSol,
    )
    // Franjas del sol, del color del panel y solo dentro del círculo
    clipPath(Sol) {
        drawRect(Colores.Tarjeta, Offset(43.4f, 190.8f), Size(163.2f, 4.2f))
        drawRect(Colores.Tarjeta, Offset(43.4f, 205.2f), Size(163.2f, 6f))
        drawRect(Colores.Tarjeta, Offset(43.4f, 220.8f), Size(163.2f, 7.8f))
    }
    // Suelo, horizonte y reflejos
    drawRect(Suelo, Offset(0f, 240f), Size(ANCHO_ESCENA, 100f))
    drawRect(Colores.Rosa, Offset(0f, 238.8f), Size(ANCHO_ESCENA, 2.4f))
    drawRect(Colores.Rosa.copy(alpha = 0.55f), Offset(77f, 249.6f), Size(96f, 3f))
    drawRect(Colores.Rosa.copy(alpha = 0.4f), Offset(92f, 260.4f), Size(66f, 3f))
    drawRect(Colores.Rosa.copy(alpha = 0.25f), Offset(107f, 271.2f), Size(36f, 3f))
    // Palmera grande a la izquierda y una más pequeña, reflejada, a la derecha
    palmera(-22.6f, 55.2f, 1.11f, 1.11f)
    palmera(278.6f, 108f, -0.72f, 0.72f)
}

private fun DrawScope.palmera(x: Float, y: Float, escalaX: Float, escalaY: Float) {
    withTransform({
        translate(x, y)
        scale(escalaX, escalaY, pivot = Offset.Zero)
    }) {
        drawPath(Tronco, Palma)
        ANGULOS_HOJAS.forEach { angulo ->
            hoja(angulo, reflejada = false)
            hoja(angulo, reflejada = true)
        }
        // Cocos
        drawCircle(Palma, radius = 3f, center = Offset(76f, 57f))
        drawCircle(Palma, radius = 3f, center = Offset(81f, 58f))
    }
}

private fun DrawScope.hoja(angulo: Float, reflejada: Boolean) {
    withTransform({
        translate(78f, 52f)
        if (reflejada) scale(-1f, 1f, pivot = Offset.Zero)
        rotate(angulo, pivot = Offset.Zero)
    }) {
        drawPath(Hoja, Palma)
    }
}