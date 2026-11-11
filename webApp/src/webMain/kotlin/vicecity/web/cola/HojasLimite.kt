package vicecity.web.cola

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.MiActividad
import vicecity.web.componentes.AccionTexto
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.HojaInferior
import vicecity.web.componentes.IconoReloj
import vicecity.web.tema.Colores

/**
 * El dispositivo ya pidió todas las canciones que permite la ventana (RF-09).
 * Si aún tiene votos, invita a ir a la cola a votar.
 */
@Composable
fun HojaLimiteSolicitudes(actividad: MiActividad?, proximaEn: String?, alIrACola: () -> Unit, alCerrar: () -> Unit) {
    val limite = actividad?.limiteSolicitudes ?: 3
    val ventana = actividad?.ventanaLimiteMin ?: 15
    val votos = actividad?.votosRestantes ?: 0
    HojaInferior(alCerrar) {
        IconoLimite()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ya pediste $limite canciones", color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(
                conResaltado("Puedes pedir hasta $limite canciones cada $ventana minutos. Podrás pedir otra en ", "${minutosHasta(proximaEn)} min", "."),
                color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
        if (votos > 0) {
            Text(
                "Mientras tanto, vota canciones de la cola: te quedan $votos de ${actividad?.limiteVotos ?: 3} votos esta noche.",
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
            BotonNeon("Ir a la cola a votar", alPresionar = alIrACola, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            AccionTexto("Entendido", alPresionar = alCerrar)
        } else {
            BotonNeon("Entendido", alPresionar = alCerrar, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

/** El dispositivo ya dio todos sus votos de la sesión a canciones de otros. */
@Composable
fun HojaLimiteVotos(actividad: MiActividad?, alCerrar: () -> Unit) {
    val limite = actividad?.limiteVotos ?: 3
    val solicitudes = actividad?.solicitudesRestantes ?: 0
    HojaInferior(alCerrar) {
        IconoLimite()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ya usaste tus $limite votos", color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(
                "Cada celular tiene $limite votos por noche para canciones que pidieron otros. Las canciones que tú pides ya llevan tu voto.",
                color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
        if (solicitudes > 0) {
            Text(
                "Todavía puedes pedir $solicitudes ${if (solicitudes == 1) "canción" else "canciones"}.",
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        BotonNeon("Entendido", alPresionar = alCerrar, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun IconoLimite() {
    Box(Modifier.size(56.dp).background(Colores.AvisoSuave, CircleShape), contentAlignment = Alignment.Center) {
        IconoReloj(Colores.Aviso)
    }
}

/** Texto con la parte del medio en negrita y color claro, como el tiempo de espera. */
private fun conResaltado(antes: String, resaltado: String, despues: String): AnnotatedString = buildAnnotatedString {
    append(antes)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Colores.Texto)) { append(resaltado) }
    append(despues)
}