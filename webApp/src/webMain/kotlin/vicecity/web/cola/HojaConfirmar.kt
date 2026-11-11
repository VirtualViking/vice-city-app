package vicecity.web.cola

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.ResultadoBusqueda
import vicecity.web.busqueda.formatoDuracion
import vicecity.web.componentes.AccionTexto
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.HojaInferior
import vicecity.web.componentes.Miniatura
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores

/**
 * Confirmación antes de pedir una canción (RF-05): dónde entrará y cuántas solicitudes le quedan.
 * Si la canción ya está en la cola, pedirla suma el voto del cliente (y gasta uno de sus votos de la noche).
 * [estado] es null si no hay una sesión abierta. [error] es el mensaje del último intento, si falló.
 */
@Composable
fun HojaConfirmar(
    api: ClienteApi,
    resultado: ResultadoBusqueda,
    estado: EstadoColaCliente?,
    error: String?,
    alSolicitar: () -> Unit,
    alVerCola: () -> Unit,
    alCerrar: () -> Unit,
) {
    HojaInferior(alCerrar) {
        Miniatura(api, resultado.videoId, descripcion = "Miniatura de ${resultado.titulo}", modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                resultado.titulo, color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 25.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text("${resultado.artista} · ${formatoDuracion(resultado.duracionSeg)}", color = Colores.TextoSecundario, fontSize = 14.sp)
        }

        val actividad = estado?.actividad
        val enCola = estado?.enCola(resultado.videoId)
        val enviando = estado != null && resultado.videoId in estado.pendientes
        val accion = if (enviando) ({}) else alSolicitar
        when {
            estado == null || estado.sesionCerrada -> {
                Nota("Las solicitudes se habilitan cuando el DJ abra la sesión de esta noche. Mientras tanto puedes seguir buscando.")
                BotonNeon("Entendido", alPresionar = alCerrar, modifier = Modifier.fillMaxWidth())
            }
            enCola != null && (estado.esMia(enCola.solicitudId) || estado.yaVote(enCola.solicitudId)) -> {
                Datos(listOf("Ya está en la cola en" to "posición ${estado.posicion(enCola.solicitudId)}", "Votos" to "${enCola.votos}"))
                Nota("Esta canción ya tiene tu voto.")
                BotonNeon("Ver la cola", alPresionar = alVerCola, modifier = Modifier.fillMaxWidth())
                AccionTexto("Cerrar", alPresionar = alCerrar)
            }
            enCola != null -> {
                Datos(
                    listOf(
                        "Ya está en la cola en" to "posición ${estado.posicion(enCola.solicitudId)} · ${enCola.votos} ${if (enCola.votos == 1) "voto" else "votos"}",
                        "Votos que te quedan" to (actividad?.let { "${it.votosRestantes} de ${it.limiteVotos} esta noche" } ?: "…"),
                    ),
                )
                Nota("Alguien ya la pidió. Al pedirla sumas tu voto para que suene antes: usa 1 de tus votos de esta noche.")
                MensajeError(error)
                BotonNeon(if (enviando) "Enviando…" else "Votar por esta canción", alPresionar = accion, modifier = Modifier.fillMaxWidth())
                AccionTexto("Cancelar", alPresionar = alCerrar)
            }
            else -> {
                Datos(
                    listOf(
                        "Entrará en la cola en" to "posición ${estado.posicionNueva}",
                        "Solicitudes que te quedan" to (actividad?.let { "${it.solicitudesRestantes} de ${it.limiteSolicitudes} en ${it.ventanaLimiteMin} min" } ?: "…"),
                    ),
                )
                Nota("Tu solicitud cuenta como el primer voto. Mientras más votos tenga, antes sonará.")
                MensajeError(error)
                BotonNeon(if (enviando) "Enviando…" else "Solicitar canción", alPresionar = accion, modifier = Modifier.fillMaxWidth())
                AccionTexto("Cancelar", alPresionar = alCerrar)
            }
        }
    }
}

/** Cuadro con filas "dato: valor". */
@Composable
private fun Datos(filas: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filas.forEach { (dato, valor) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(dato, color = Colores.TextoSecundario, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(valor, color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Nota(texto: String) {
    Text(texto, color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun MensajeError(texto: String?) {
    if (texto == null) return
    Text(
        texto, color = Colores.Aviso, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().background(Colores.AvisoSuave, RoundedCornerShape(10.dp)).padding(10.dp),
    )
}