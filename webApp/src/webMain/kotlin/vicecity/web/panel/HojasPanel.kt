package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.MotivoEliminacion
import vicecity.web.componentes.AccionTexto
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.HojaInferior
import vicecity.web.tema.Colores

/** Hoja abierta sobre el panel. Se dibuja fuera del contenido con desplazamiento, sobre toda la pantalla. */
sealed interface HojaPanel {
    /** Eliminar una solicitud de la cola o la que suena. */
    data class Eliminar(val solicitudId: String, val titulo: String) : HojaPanel
    data object CerrarSesion : HojaPanel
}

@Composable
fun HojasPanel(modelo: ModeloPanel) {
    when (val hoja = modelo.hoja) {
        is HojaPanel.Eliminar -> HojaEliminar(hoja, alConfirmar = { motivo, bloquear ->
            modelo.hoja = null
            modelo.eliminar(hoja.solicitudId, motivo, bloquear)
        }, alCerrar = { modelo.hoja = null })
        HojaPanel.CerrarSesion -> HojaInferior(alCerrar = { modelo.hoja = null }) {
            val enCola = modelo.reproduccion?.cola?.size ?: 0
            Text("¿Cerrar la sesión de esta noche?", color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(
                "Los clientes ya no podrán pedir ni votar. Las $enCola canciones que siguen en la cola quedarán como no reproducidas.",
                color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 21.sp,
            )
            BotonNeon("Cerrar sesión", alPresionar = {
                modelo.hoja = null
                modelo.cerrarSesion()
            }, modifier = Modifier.fillMaxWidth())
            AccionTexto("Cancelar", alPresionar = { modelo.hoja = null })
        }
        null -> {}
    }
}

/** Confirmación de HU-14: motivo obligatorio y bloqueo opcional por 24 horas. */
@Composable
private fun HojaEliminar(eleccion: HojaPanel.Eliminar, alConfirmar: (MotivoEliminacion, Boolean) -> Unit, alCerrar: () -> Unit) {
    var motivo by remember { mutableStateOf<MotivoEliminacion?>(null) }
    var bloquear by remember { mutableStateOf(true) }
    HojaInferior(alCerrar) {
        Text("Eliminar de la cola", color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(eleccion.titulo, color = Colores.TextoSecundario, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Motivo", color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MotivoEliminacion.INAPROPIADO, MotivoEliminacion.NO_MUSICAL, MotivoEliminacion.OTRO).forEach { opcion ->
                val elegido = motivo == opcion
                Text(
                    nombreMotivo(opcion),
                    color = if (elegido) Colores.TextoSobreNeon else Colores.Texto,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                        .background(if (elegido) Colores.Cian else Colores.TarjetaAlta, RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { motivo = opcion }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        Interruptor(
            titulo = "Bloquear por 24 horas",
            explicacion = "Nadie podrá pedirla ni la verá en las búsquedas hasta mañana a esta hora.",
            activo = bloquear,
            alCambiar = { bloquear = it },
        )
        val elegido = motivo
        Box(Modifier.fillMaxWidth()) {
            if (elegido == null) {
                Text("Elige un motivo para continuar.", color = Colores.Apagado, fontSize = 13.sp)
            } else {
                BotonNeon(if (bloquear) "Eliminar y bloquear" else "Eliminar", alPresionar = { alConfirmar(elegido, bloquear) }, modifier = Modifier.fillMaxWidth())
            }
        }
        AccionTexto("Cancelar", alPresionar = alCerrar)
    }
}