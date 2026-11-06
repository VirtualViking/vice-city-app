package vicecity.web.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.tema.Colores

/**
 * Cómo instalar la app en iPhone o iPad (Safari no ofrece un botón de instalación).
 * En Android, Chrome muestra su propio aviso de instalación, así que este aviso solo aparece en iOS.
 */
@Composable
fun AvisoInstalarIos(alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconoInstalar(Colores.Rosa)
        Text(
            buildAnnotatedString {
                append("Agrega la app a tu pantalla de inicio: toca ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Compartir") }
                append(" y luego ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Agregar a inicio") }
                append(".")
            },
            color = Colores.Texto, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = alCerrar, modifier = Modifier.size(44.dp).semantics { contentDescription = "Cerrar aviso" }) {
            Box { IconoCerrar(Colores.TextoSecundario) }
        }
    }
}