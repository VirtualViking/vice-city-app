package vicecity.web.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.tema.Colores

/** Mensaje centrado con ícono, título, explicación y una acción opcional (estados vacíos, de carga o de error). */
@Composable
fun MensajeCentral(
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    icono: @Composable () -> Unit = { IconoMusica(Colores.Apagado) },
    accion: String? = null,
    alAccionar: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icono()
        Text(titulo, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(texto, color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        if (accion != null) {
            BotonNeon(accion, alPresionar = alAccionar, modifier = Modifier.padding(top = 14.dp))
        }
    }
}