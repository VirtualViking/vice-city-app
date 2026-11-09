package vicecity.web.busqueda

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.Sugerencia
import vicecity.compartido.TipoSugerencia
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.IconoBuscar
import vicecity.web.componentes.IconoCerrar
import vicecity.web.componentes.brilloNeon
import vicecity.web.tema.Colores
import vicecity.web.tema.Medidas

/**
 * Caja de búsqueda con autocompletado: mientras se escribe muestra sugerencias debajo;
 * al tocar una, al presionar "Buscar" o la tecla de búsqueda del teclado, se busca.
 */
@Composable
fun CajaBusqueda(
    texto: String,
    sugerencias: List<Sugerencia>,
    alCambiar: (String) -> Unit,
    alBuscar: (String) -> Unit,
    alLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TextFieldValue permite dejar el cursor al final cuando el texto cambia desde una sugerencia
    var campo by remember { mutableStateOf(TextFieldValue(texto)) }
    if (campo.text != texto) campo = TextFieldValue(texto, TextRange(texto.length))
    var enfocada by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(Medidas.Esquina)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Buscar canción", color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(Medidas.AltoControl)
                    .then(if (enfocada) Modifier.brilloNeon(Colores.Rosa.copy(alpha = 0.35f), radio = 18.dp, esquina = Medidas.Esquina) else Modifier)
                    .background(Colores.Tarjeta, forma)
                    .border(BorderStroke(2.dp, if (enfocada) Colores.Rosa else Colores.Borde), forma)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconoBuscar(Colores.TextoSecundario)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (campo.text.isEmpty()) Text("Canción o artista", color = Colores.Apagado, fontSize = 16.sp)
                    BasicTextField(
                        value = campo,
                        onValueChange = { nuevo ->
                            campo = nuevo
                            if (nuevo.text != texto) alCambiar(nuevo.text)
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Colores.Texto, fontSize = 16.sp),
                        cursorBrush = SolidColor(Colores.Rosa),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { alBuscar(campo.text) }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { enfocada = it.isFocused }
                            .semantics { contentDescription = "Buscar canción o artista" },
                    )
                }
                if (campo.text.isNotEmpty()) {
                    IconButton(onClick = alLimpiar, modifier = Modifier.size(44.dp).semantics { contentDescription = "Borrar búsqueda" }) {
                        IconoCerrar(Colores.TextoSecundario)
                    }
                }
            }
            // Botón visible: no depende de que el teclado del celular muestre la tecla de búsqueda
            BotonNeon("Buscar", alPresionar = { alBuscar(campo.text) })
        }

        // Se muestran aunque el campo pierda el foco, para que el toque sobre una sugerencia no se pierda
        if (sugerencias.isNotEmpty()) {
            ListaSugerencias(sugerencias, alElegir = alBuscar)
        } else {
            Text("Solo aparecen videos musicales disponibles para reproducir aquí.", color = Colores.TextoSecundario, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ListaSugerencias(sugerencias: List<Sugerencia>, alElegir: (String) -> Unit) {
    val forma = RoundedCornerShape(Medidas.Esquina)
    Column(
        Modifier.fillMaxWidth().background(Colores.Tarjeta, forma).border(1.dp, Colores.Borde, forma).padding(vertical = 4.dp),
    ) {
        sugerencias.forEach { sugerencia ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { alElegir(sugerencia.texto) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconoBuscar(Colores.Apagado, tamano = 16.dp)
                Text(
                    sugerencia.texto, color = Colores.Texto, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(etiqueta(sugerencia.tipo), color = Colores.TextoSecundario, fontSize = 12.sp)
            }
        }
    }
}

private fun etiqueta(tipo: TipoSugerencia) = when (tipo) {
    TipoSugerencia.BUSQUEDA -> "Buscado"
    TipoSugerencia.ARTISTA -> "Artista"
    TipoSugerencia.CANCION -> "Canción"
}