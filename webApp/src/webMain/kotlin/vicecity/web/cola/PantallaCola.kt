package vicecity.web.cola

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import vicecity.compartido.ElementoActual
import vicecity.compartido.ItemCola
import vicecity.web.componentes.IconoCola
import vicecity.web.componentes.IconoVoto
import vicecity.web.componentes.MensajeCentral
import vicecity.web.componentes.Miniatura
import vicecity.web.componentes.brilloNeon
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores
import vicecity.web.tema.Medidas

/**
 * Cola de esta noche (RF-07): lo que suena, las canciones que siguen ordenadas por votos y los votos que le quedan al cliente.
 * [estado] es null si no hay una sesión abierta.
 */
@Composable
fun PantallaCola(
    api: ClienteApi,
    estado: EstadoColaCliente?,
    alVotar: (ItemCola) -> Unit,
    alPedir: () -> Unit,
    alReintentar: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Colores.Fondo)) {
        val lateral = max(16.dp, (maxWidth - AnchoCola) / 2)
        when {
            estado == null || estado.sesionCerrada -> MensajeCentral(
                titulo = "Las solicitudes no están abiertas",
                texto = "Cuando el DJ abra la sesión de esta noche podrás pedir y votar canciones desde aquí.",
                accion = "Volver a intentar", alAccionar = alReintentar,
                modifier = Modifier.align(Alignment.Center),
            )
            estado.cola == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (estado.sinConexion) {
                    MensajeCentral(titulo = "Sin conexión", texto = "No pudimos cargar la cola. Seguimos intentando…")
                } else {
                    CircularProgressIndicator(color = Colores.Rosa)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = lateral, end = lateral, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Encabezado(estado) }
                estado.cola.actual?.let { actual -> item { SuenaAhora(api, actual) } }
                lista(api, estado, alVotar, alPedir)
            }
        }
    }
}

@Composable
private fun Encabezado(estado: EstadoColaCliente) {
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cola de esta noche", color = Colores.Texto, fontSize = 24.sp, fontWeight = FontWeight.Black)
        estado.actividad?.let { actividad ->
            val quedan = actividad.votosRestantes
            Row(
                Modifier.border(1.5.dp, if (quedan > 0) Colores.Cian else Colores.Borde, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconoVoto(if (quedan > 0) Colores.Cian else Colores.Apagado, tamano = 16.dp)
                Text(
                    if (quedan > 0) "Te quedan $quedan de ${actividad.limiteVotos} votos" else "Ya usaste tus ${actividad.limiteVotos} votos",
                    color = if (quedan > 0) Colores.Cian else Colores.Apagado, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Vota las canciones que pidieron otros para que suenen antes. Las que tú pides ya llevan tu voto.",
                color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        if (estado.sinConexion) {
            Text(
                "Sin conexión: la cola puede no estar al día.",
                color = Colores.Aviso, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(Colores.AvisoSuave, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        }
    }
}

@Composable
private fun SuenaAhora(api: ClienteApi, actual: ElementoActual) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .background(Colores.TarjetaAlta, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Miniatura(api, actual.videoId, descripcion = "Miniatura de ${actual.titulo}", modifier = Modifier.width(80.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SUENA AHORA", color = Colores.Cian, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(actual.titulo, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(actual.artista, color = Colores.TextoSecundario, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun LazyListScope.lista(api: ClienteApi, estado: EstadoColaCliente, alVotar: (ItemCola) -> Unit, alPedir: () -> Unit) {
    val canciones = estado.canciones
    if (canciones.isEmpty()) {
        item {
            MensajeCentral(
                titulo = "Todavía no hay solicitudes",
                texto = "Sé el primero en pedir una canción esta noche.",
                icono = { IconoCola(Colores.Apagado, tamano = 40.dp) },
                accion = "Pedir una canción", alAccionar = alPedir,
            )
        }
        return
    }
    item {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SIGUIENTES · ${canciones.size}", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("Ordenadas por votos", color = Colores.Apagado, fontSize = 13.sp)
        }
    }
    val sinVotos = (estado.actividad?.votosRestantes ?: 1) == 0
    itemsIndexed(canciones, key = { _, item -> item.solicitudId }) { i, item ->
        FilaCola(
            api, posicion = i + 1, item = item,
            mia = estado.esMia(item.solicitudId),
            votada = estado.yaVote(item.solicitudId),
            sinVotos = sinVotos,
            pendiente = item.solicitudId in estado.pendientes,
            alVotar = { alVotar(item) },
        )
    }
}

@Composable
private fun FilaCola(
    api: ClienteApi, posicion: Int, item: ItemCola,
    mia: Boolean, votada: Boolean, sinVotos: Boolean, pendiente: Boolean, alVotar: () -> Unit,
) {
    val forma = RoundedCornerShape(Medidas.Esquina)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .background(Colores.Tarjeta, forma)
            .then(if (mia) Modifier.border(1.5.dp, Colores.Rosa, forma) else Modifier)
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$posicion", color = if (mia) Colores.Rosa else Colores.Apagado, fontSize = 15.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.width(26.dp), textAlign = TextAlign.Center,
        )
        Miniatura(api, item.videoId, descripcion = "Miniatura de ${item.titulo}", modifier = Modifier.width(64.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.titulo, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (mia) {
                Text("Tu solicitud", color = Colores.Rosa, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(item.artista, color = Colores.TextoSecundario, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        BotonVoto(votos = item.votos, votada = votada || mia, apagado = sinVotos, pendiente = pendiente, alPresionar = alVotar)
    }
}

/**
 * Cuadro con la flecha y los votos. Relleno con el degradado si el cliente ya votó (o es su solicitud);
 * con borde si aún puede votar; apagado si ya no le quedan votos (al tocarlo se explica por qué).
 * La cadena de modificadores es siempre la misma y solo cambian sus valores: así Compose no rehace el dibujo del brillo.
 */
@Composable
private fun BotonVoto(votos: Int, votada: Boolean, apagado: Boolean, pendiente: Boolean, alPresionar: () -> Unit) {
    val forma = RoundedCornerShape(Medidas.Esquina)
    val texto = when {
        votada -> Colores.TextoSobreNeon
        apagado || pendiente -> Colores.Apagado
        else -> Colores.Texto
    }
    val descripcion = (if (votada) "Ya votaste. " else "Votar. ") + "$votos ${if (votos == 1) "voto" else "votos"}"
    Column(
        Modifier.size(52.dp)
            .brilloNeon(if (votada) Colores.Rosa.copy(alpha = 0.35f) else Color.Transparent, radio = 10.dp, esquina = Medidas.Esquina)
            .clip(forma)
            .background(if (votada) Colores.DegradadoNeon else SolidColor(Color.Transparent))
            .border(2.dp, if (votada) Color.Transparent else Colores.Borde, forma)
            .clickable(enabled = !votada && !pendiente, role = Role.Button, onClick = alPresionar)
            .semantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconoVoto(texto)
        Text("$votos", color = texto, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

/** La cola se lee mejor como una sola columna; en tableta no pasa de este ancho. */
private val AnchoCola = 720.dp