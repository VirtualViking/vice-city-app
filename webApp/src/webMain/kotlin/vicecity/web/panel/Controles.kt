package vicecity.web.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.tema.Colores
import vicecity.web.tema.Medidas

/** Tarjeta de una sección del panel, con título opcional. */
@Composable
fun Tarjeta(titulo: String? = null, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Colores.Tarjeta, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (titulo != null) Text(titulo, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        contenido()
    }
}

/** Campo de texto con etiqueta, del mismo estilo que la caja de búsqueda del cliente. */
@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    marcador: String = "",
    contrasena: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    alEnviar: (() -> Unit)? = null,
) {
    var enfocado by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(Medidas.Esquina)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (etiqueta.isNotEmpty()) Text(etiqueta, color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .background(Colores.Fondo, forma)
                .border(BorderStroke(2.dp, if (enfocado) Colores.Rosa else Colores.Borde), forma)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (valor.isEmpty() && marcador.isNotEmpty()) Text(marcador, color = Colores.Apagado, fontSize = 15.sp)
            BasicTextField(
                value = valor,
                onValueChange = alCambiar,
                singleLine = true,
                textStyle = TextStyle(color = Colores.Texto, fontSize = 15.sp),
                cursorBrush = SolidColor(Colores.Rosa),
                visualTransformation = if (contrasena) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (contrasena) KeyboardType.Password else teclado,
                    imeAction = if (alEnviar != null) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { alEnviar?.invoke() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused }
                    .semantics { contentDescription = etiqueta.ifEmpty { marcador } },
            )
        }
    }
}

/** Interruptor con título y explicación. */
@Composable
fun Interruptor(titulo: String, explicacion: String, activo: Boolean, alCambiar: (Boolean) -> Unit, habilitado: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(explicacion, color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = activo,
            onCheckedChange = alCambiar,
            enabled = habilitado,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Colores.TextoSobreNeon,
                checkedTrackColor = Colores.Cian,
                uncheckedThumbColor = Colores.Apagado,
                uncheckedTrackColor = Colores.Fondo,
                uncheckedBorderColor = Colores.Borde,
            ),
        )
    }
}

/** Etiqueta redonda con un punto de color, por ejemplo "Sesión abierta". */
@Composable
fun Etiqueta(texto: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(texto, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Botón pequeño con borde, para acciones de una fila (Eliminar, Agregar, Desbloquear). */
@Composable
fun BotonFila(texto: String, alPresionar: () -> Unit, color: Color = Colores.Texto, habilitado: Boolean = true) {
    val forma = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(38.dp).clip(forma)
            .border(1.5.dp, if (habilitado) Colores.Borde else Colores.Borde.copy(alpha = 0.4f), forma)
            .clickable(enabled = habilitado, role = Role.Button, onClick = alPresionar)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (habilitado) color else Colores.Apagado, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Botón cuadrado con un ícono dibujado (subir, bajar, quitar). [icono] recibe el color que le corresponde. */
@Composable
fun BotonIcono(descripcion: String, alPresionar: () -> Unit, habilitado: Boolean = true, icono: @Composable (Color) -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(38.dp).clip(forma).border(1.5.dp, Colores.Borde, forma)
            .clickable(enabled = habilitado, role = Role.Button, onClick = alPresionar)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        icono(if (habilitado) Colores.Texto else Colores.Apagado.copy(alpha = 0.5f))
    }
}

/** Texto de error dentro de una tarjeta. */
@Composable
fun TextoError(texto: String?) {
    if (texto == null) return
    Text(
        texto, color = Colores.Aviso, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().background(Colores.AvisoSuave, RoundedCornerShape(10.dp)).padding(10.dp),
    )
}