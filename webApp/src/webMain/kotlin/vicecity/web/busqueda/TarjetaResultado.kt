package vicecity.web.busqueda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.ResultadoBusqueda
import vicecity.web.componentes.Miniatura
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores
import vicecity.web.tema.Medidas

/** Un video de los resultados: miniatura, título, artista y duración (RF-04). Al tocarlo se puede pedir (RF-05). */
@Composable
fun TarjetaResultado(api: ClienteApi, resultado: ResultadoBusqueda, alPresionar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(Medidas.Esquina)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clip(forma)
            .background(Colores.Tarjeta)
            .clickable(role = Role.Button, onClick = alPresionar)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Miniatura(
            api, resultado.videoId, descripcion = "Miniatura de ${resultado.titulo}",
            modifier = Modifier.width(96.dp).aspectRatio(16f / 9f),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                resultado.titulo, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${resultado.artista} · ${formatoDuracion(resultado.duracionSeg)}",
                color = Colores.TextoSecundario, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}