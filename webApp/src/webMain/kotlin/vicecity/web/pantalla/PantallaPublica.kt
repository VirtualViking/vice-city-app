package vicecity.web.pantalla

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLElement
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.ItemCola
import vicecity.compartido.Origen
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.MensajeCentral
import vicecity.web.componentes.Miniatura
import vicecity.web.componentes.TituloNeon
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ClientePersonal
import vicecity.web.tema.Colores

private const val ID_REPRODUCTOR = "vicecity-reproductor"

/**
 * Vista pública (Figuras 10 a 12 del diseño): se abre en ".../#pantalla" en el computador o TV conectado al sonido.
 * Muestra el video que suena, lo que sigue con sus votos y el estado de las solicitudes.
 */
@Composable
fun PantallaPublica() {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloPantalla(ClientePersonal(), alcance) }
    val miniaturas = remember { ClienteApi() }

    if (modelo.sinCuenta) {
        Box(Modifier.fillMaxSize().background(Colores.Fondo), contentAlignment = Alignment.Center) {
            MensajeCentral(
                titulo = "Entra primero al panel",
                texto = "La vista pública usa la cuenta del personal. Entra al panel en este mismo navegador y ábrela con \"Abrir vista pública\".",
                accion = "Ir al panel",
                alAccionar = {
                    window.location.hash = "panel"
                    window.location.reload()
                },
            )
        }
        return
    }

    DisposableEffect(Unit) {
        modelo.iniciarSondeo()
        onDispose { modelo.detener() }
    }
    // El reproductor se crea cuando su contenedor ya está en la página
    LaunchedEffect(Unit) {
        repeat(50) {
            if (document.getElementById(ID_REPRODUCTOR) != null) {
                ViceCityReproductor.crear(ID_REPRODUCTOR) { tipo, detalle -> modelo.alEvento(tipo, detalle) }
                return@LaunchedEffect
            }
            delay(100)
        }
    }

    val estado = modelo.estado
    BoxWithConstraints(Modifier.fillMaxSize().background(Colores.Fondo)) {
        val ancho = maxWidth >= 900.dp
        Column(
            Modifier.fillMaxSize().then(if (ancho) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(if (ancho) 40.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (ancho) 28.dp else 16.dp),
        ) {
            Encabezado(estado, ancho)
            if (ancho) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        // El video ocupa el alto que sobra, siempre en 16:9
                        Reproductor(Modifier.weight(1f, fill = false).aspectRatio(16f / 9f, matchHeightConstraintsFirst = true))
                        if (modelo.iniciada) SuenaAhora(modelo, ancho = true) else TarjetaInicio(modelo)
                        Problema(modelo)
                    }
                    Siguientes(estado?.cola.orEmpty(), miniaturas, Modifier.weight(1f), maximo = 6)
                }
            } else {
                Reproductor(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                if (modelo.iniciada) SuenaAhora(modelo, ancho = false) else TarjetaInicio(modelo)
                Problema(modelo)
                Siguientes(estado?.cola.orEmpty(), miniaturas, Modifier.fillMaxWidth(), maximo = 5)
            }
        }
    }
}

@Composable
private fun Encabezado(estado: EstadoReproduccion?, ancho: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        TituloNeon(estado?.establecimiento ?: "Vice City", tamano = if (ancho) 40.sp else 26.sp, modifier = Modifier.weight(1f, fill = false))
        val sesion = estado?.sesion
        val (texto, color) = when {
            sesion == null -> "Solicitudes cerradas" to Colores.Apagado
            sesion.solicitudesPausadas -> "Solicitudes en pausa" to Colores.Aviso
            else -> "Solicitudes abiertas" to Colores.Cian
        }
        Row(
            Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(texto, color = color, fontSize = if (ancho) 20.sp else 14.sp, fontWeight = FontWeight.Bold)
        }
        if (ancho) {
            Box(Modifier.weight(1f))
            Text("${estado?.cola?.size ?: 0} canciones en cola", color = Colores.TextoSecundario, fontSize = 22.sp)
        }
    }
}

/** Recuadro con el reproductor de YouTube. Siempre está en la página: si se quitara, YouTube perdería el reproductor. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Reproductor(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        HtmlElementView<HTMLElement>(
            factory = {
                val contenedor = document.createElement("div") as HTMLElement
                contenedor.className = "yt-contenedor"
                val destino = document.createElement("div")
                destino.id = ID_REPRODUCTOR
                contenedor.appendChild(destino)
                contenedor
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Antes del primer toque: el botón que habilita el sonido. */
@Composable
private fun TarjetaInicio(modelo: ModeloPantalla) {
    Column(
        Modifier.fillMaxWidth().background(Colores.Tarjeta, RoundedCornerShape(16.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Toca para iniciar la música", color = Colores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "El navegador necesita un toque para reproducir con sonido. Deja esta pantalla abierta toda la noche.",
            color = Colores.TextoSecundario, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        BotonNeon(if (modelo.listo) "Iniciar reproducción" else "Cargando reproductor…", alPresionar = { if (modelo.listo) modelo.iniciar() })
    }
}

@Composable
private fun Problema(modelo: ModeloPantalla) {
    modelo.problema?.let {
        Text(it, color = Colores.Aviso, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().background(Colores.AvisoSuave, RoundedCornerShape(10.dp)).padding(12.dp))
    }
}

@Composable
private fun SuenaAhora(modelo: ModeloPantalla, ancho: Boolean) {
    val estado = modelo.estado
    val actual = estado?.actual
    val pausado = estado?.sesion?.estadoReproductor == EstadoReproductor.PAUSADO
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (actual == null) {
            Text(
                when {
                    estado?.sesion == null -> "La sesión de esta noche no está abierta."
                    else -> "Esperando la primera canción. Escanea el QR y pide la tuya."
                },
                color = Colores.TextoSecundario, fontSize = if (ancho) 22.sp else 16.sp,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (pausado) "EN PAUSA" else "SUENA AHORA",
                color = if (pausado) Colores.Aviso else Colores.Cian,
                fontSize = if (ancho) 18.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
            Text(
                if (actual.origen == Origen.LISTA_DEFECTO) "Lista del establecimiento" else "Pedida por el público",
                color = Colores.TextoSecundario, fontSize = if (ancho) 16.sp else 12.sp,
            )
        }
        Text(
            actual.titulo, color = Colores.Texto, fontSize = if (ancho) 36.sp else 22.sp, fontWeight = FontWeight.Black,
            lineHeight = if (ancho) 42.sp else 27.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(actual.artista, color = Colores.TextoSecundario, fontSize = if (ancho) 24.sp else 16.sp)
    }
}

@Composable
private fun Siguientes(cola: List<ItemCola>, miniaturas: ClienteApi, modifier: Modifier, maximo: Int) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SIGUIENTES", color = Colores.TextoSecundario, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Text("Ordenadas por votos", color = Colores.Apagado, fontSize = 14.sp)
        }
        if (cola.isEmpty()) {
            Text(
                "Todavía no hay solicitudes. Mientras tanto suena la lista del establecimiento.",
                color = Colores.TextoSecundario, fontSize = 16.sp, lineHeight = 22.sp,
            )
        }
        cola.take(maximo).forEachIndexed { i, item ->
            Row(
                Modifier.fillMaxWidth().background(Colores.Tarjeta, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("${i + 1}", color = if (i == 0) Colores.Rosa else Colores.Apagado, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
                Miniatura(miniaturas, item.videoId, "Miniatura de ${item.titulo}", Modifier.width(88.dp).aspectRatio(16f / 9f))
                Column(Modifier.weight(1f)) {
                    Text(item.titulo, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artista, color = Colores.TextoSecundario, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${item.votos}", color = Colores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        if (cola.size > maximo) Text("y ${cola.size - maximo} más", color = Colores.TextoSecundario, fontSize = 16.sp)
    }
}