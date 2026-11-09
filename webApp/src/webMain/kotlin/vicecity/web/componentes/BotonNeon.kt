package vicecity.web.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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

@Composable
fun BotonNeon(
    texto: String,
    alPresionar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: (@Composable () -> Unit)? = null,
) {
    val forma = RoundedCornerShape(Medidas.Esquina)
    Box(
        modifier
            .height(Medidas.AltoControl)
            .brilloNeon(Colores.Rosa.copy(alpha = 0.45f), radio = 14.dp, esquina = Medidas.Esquina)
            .clip(forma)
            .background(Colores.DegradadoNeon)
            .clickable(role = Role.Button, onClick = alPresionar)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            icono?.invoke()
            Text(texto, color = Colores.TextoSobreNeon, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}