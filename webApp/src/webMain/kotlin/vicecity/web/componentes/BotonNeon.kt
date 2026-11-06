package vicecity.web.componentes

import androidx.compose.foundation.background
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

/** Botón principal: degradado rosa → coral con brillo neón y texto oscuro para que se lea bien. */
@Composable
fun BotonNeon(texto: String, alPresionar: () -> Unit, modifier: Modifier = Modifier) {
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
        Text(texto, color = Colores.TextoSobreNeon, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}