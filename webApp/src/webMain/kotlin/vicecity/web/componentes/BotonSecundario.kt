package vicecity.web.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.tema.Colores
import vicecity.web.tema.Medidas

/** Botón para la opción alternativa: fondo de tarjeta con borde, sin brillo, para no competir con el principal. */
@Composable
fun BotonSecundario(texto: String, alPresionar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(Medidas.Esquina)
    Box(
        modifier
            .height(Medidas.AltoControl)
            .clip(forma)
            .background(Colores.Tarjeta)
            .border(2.dp, Colores.Borde, forma)
            .clickable(role = Role.Button, onClick = alPresionar)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Colores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}