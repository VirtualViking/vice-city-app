package vicecity.web.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.tema.Colores

/**
 * Hoja que sube desde abajo sobre la pantalla oscurecida (confirmar una solicitud, avisos de límite).
 * Tocar fuera de la hoja la cierra. En pantallas anchas no pasa de 520 dp y queda centrada.
 */
@Composable
fun HojaInferior(alCerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Velo).clickable(MutableInteractionSource(), indication = null, onClick = alCerrar),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                // Evita que un toque dentro de la hoja la cierre
                .clickable(MutableInteractionSource(), indication = null, onClick = {})
                .background(Colores.Tarjeta, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(Colores.Borde, RoundedCornerShape(2.dp)))
            contenido()
        }
    }
}

/** Acción secundaria de una hoja: solo texto, debajo del botón principal. */
@Composable
fun AccionTexto(texto: String, alPresionar: () -> Unit) {
    Text(
        texto,
        color = Colores.TextoSecundario, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().height(44.dp).clickable(onClick = alPresionar).padding(top = 12.dp),
    )
}

/** Oscurece la pantalla detrás de la hoja. */
private val Velo = Color(0xB8120B24)