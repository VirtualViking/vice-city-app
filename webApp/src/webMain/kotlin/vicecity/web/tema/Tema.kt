package vicecity.web.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paleta "atardecer neón": cielo claro solo en el encabezado y el resto oscuro,
 * para que los resultados se lean bien dentro de un bar o una discoteca.
 */
object Colores {
    // Fondo y superficies
    val Fondo = Color(0xFF211936)
    val Tarjeta = Color(0xFF2E2450)
    val TarjetaAlta = Color(0xFF3A2D62)
    val Borde = Color(0xFF42356B)

    // Texto
    val Texto = Color(0xFFFFF6F0)
    val TextoSecundario = Color(0xFFD6C8E8)
    val Apagado = Color(0xFFA898C8)
    /** Texto oscuro sobre los botones con degradado (contraste suficiente sobre rosa y coral). */
    val TextoSobreNeon = Color(0xFF241433)

    // Acentos neón
    val Rosa = Color(0xFFFF5FA2)
    val Coral = Color(0xFFFF9466)
    val Durazno = Color(0xFFFFC68A)
    val Cian = Color(0xFF3EE8DC)

    // Avisos (por ejemplo, cuando YouTube no está disponible)
    val Aviso = Color(0xFFFFC68A)
    val AvisoSuave = Color(0xFF45293F)

    /** Degradado de los botones principales: de rosa a coral. */
    val DegradadoNeon = Brush.horizontalGradient(listOf(Rosa, Coral))
}

/** Medidas que se repiten en las pantallas. */
object Medidas {
    val Margen = 20.dp
    val Esquina = 12.dp
    val AltoControl = 52.dp
    val AnchoMaximo = 1100.dp
}

@Composable
fun TemaViceCity(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Colores.Rosa,
            onPrimary = Colores.TextoSobreNeon,
            background = Colores.Fondo,
            surface = Colores.Tarjeta,
            onSurface = Colores.Texto,
        ),
        content = contenido,
    )
}