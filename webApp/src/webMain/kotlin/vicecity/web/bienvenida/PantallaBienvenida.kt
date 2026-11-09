package vicecity.web.bienvenida

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.EstablecimientoPublico
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.BotonSecundario
import vicecity.web.componentes.FondoAtardecer
import vicecity.web.componentes.IconoDescargar
import vicecity.web.componentes.TituloNeon
import vicecity.web.datos.Instalacion
import vicecity.web.datos.Navegador
import vicecity.web.tema.Colores

/**
 * Bienvenida al escanear el QR en el navegador: ofrece instalar la app o seguir en el navegador.
 * Si el navegador no puede abrir su ventana de instalación (iPhone, otro navegador o app ya instalada),
 * "Descargar esta app" muestra los pasos para hacerlo desde el menú.
 */
@Composable
fun PantallaBienvenida(establecimiento: EstablecimientoPublico, alContinuar: () -> Unit) {
    var mostrarPasos by remember { mutableStateOf(false) }
    val descargar = {
        // Con la ventana del navegador abierta se pasa a la búsqueda: si instala, la app queda en la pantalla de inicio
        if (Instalacion.abrir()) alContinuar() else mostrarPasos = true
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Colores.Fondo)) {
        val celular = maxWidth < 600.dp
        val lateral = if (celular) 16.dp else 24.dp

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(if (celular) 400.dp else 440.dp)) {
                FondoAtardecer(Modifier.fillMaxSize(), altoEscena = if (celular) 240.dp else 270.dp)
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter).padding(start = lateral, end = lateral, top = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "BIENVENIDO A",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                        modifier = Modifier.background(CapsulaOscura, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    TituloNeon(establecimiento.nombre, tamano = 36.sp)
                }
            }
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(start = lateral, end = lateral, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pide y vota la música de esta noche", color = Colores.Texto, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                Text("Busca tus canciones desde el celular, sin registrarte.", color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(20.dp))
                BotonNeon(
                    "Descargar esta app", alPresionar = descargar, modifier = Modifier.fillMaxWidth(),
                    icono = { IconoDescargar(Colores.TextoSobreNeon) },
                )
                BotonSecundario("Continuar en el navegador", alPresionar = alContinuar, modifier = Modifier.fillMaxWidth())
                Text(
                    "Se instala desde el navegador, sin pasar por la tienda de apps, y abre directo en este establecimiento.",
                    color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        if (mostrarPasos) {
            HojaPasosInstalar(alCerrar = { mostrarPasos = false }, alContinuar = alContinuar)
        }
    }
}

/** Hoja inferior con los pasos para instalar desde el menú del navegador. Tocar fuera de la hoja la cierra. */
@Composable
private fun HojaPasosInstalar(alCerrar: () -> Unit, alContinuar: () -> Unit) {
    val ios = Navegador.esIos
    Box(
        Modifier.fillMaxSize().background(Velo).clickable(MutableInteractionSource(), indication = null, onClick = alCerrar),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                // Evita que un toque dentro de la hoja la cierre
                .clickable(MutableInteractionSource(), indication = null, onClick = {})
                .background(Colores.Tarjeta, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(Colores.Borde, RoundedCornerShape(2.dp)))
            Text(
                if (ios) "Instala la app desde Safari" else "Instala la app desde el menú",
                color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black,
            )
            Text(
                if (ios) "En iPhone la app se agrega a la pantalla de inicio así:" else "Tu navegador no abrió la ventana de instalación. Puedes hacerlo así:",
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
            if (ios) {
                Paso(1, conNegrita(listOf("Toca el botón ", "Compartir", " de Safari.")))
                Paso(2, conNegrita(listOf("Elige ", "Agregar a inicio", ".")))
            } else {
                Paso(1, conNegrita(listOf("Toca el ", "menú de Chrome", ": los tres puntos de arriba a la derecha.")))
                Paso(2, conNegrita(listOf("Elige ", "Instalar app", " o ", "Agregar a la pantalla principal", ".")))
                Text(
                    conNegrita(listOf("En iPhone: toca ", "Compartir", " y luego ", "Agregar a inicio", ".")),
                    color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            BotonNeon("Entendido", alPresionar = alCerrar, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Text(
                "Continuar en el navegador",
                color = Colores.TextoSecundario, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(44.dp).clickable(onClick = alContinuar).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Paso(numero: Int, texto: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(Colores.DegradadoNeon, CircleShape), contentAlignment = Alignment.Center) {
            Text("$numero", color = Colores.TextoSobreNeon, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Text(texto, color = Colores.Texto, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * Alterna texto normal y en negrita: conNegrita(listOf("Toca ", "Compartir", " y luego…")).
 * Las partes en posición impar (la 2.ª, la 4.ª…) van en negrita.
 */
private fun conNegrita(partes: List<String>): AnnotatedString = buildAnnotatedString {
    partes.forEachIndexed { i, parte ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Colores.Texto)) { append(parte) } else append(parte)
    }
}

/** Fondo semitransparente de la etiqueta, para que se lea sobre el cielo claro. */
private val CapsulaOscura = Color(0x8C211936)

/** Oscurece la pantalla detrás de la hoja de pasos. */
private val Velo = Color(0xB8120B24)