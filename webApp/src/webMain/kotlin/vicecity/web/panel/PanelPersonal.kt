package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vicecity.compartido.Rol
import vicecity.web.componentes.IconoListo
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ClientePersonal
import vicecity.web.tema.Colores

/**
 * Panel del personal (RF-14, RF-16 a RF-18): se abre en ".../#panel".
 * El DJ ve Sesión y Lista por defecto; el administrador, además, Operación.
 */
@Composable
fun PanelPersonal() {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloPanel(ClientePersonal(), alcance) }
    val miniaturas = remember { ClienteApi() }
    val cuenta = modelo.cuenta

    if (cuenta == null) {
        PantallaEntrada(modelo)
        return
    }
    DisposableEffect(cuenta.token) {
        modelo.iniciar()
        onDispose { modelo.detener() }
    }
    val aviso = modelo.aviso
    LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(4_000)
            modelo.aviso = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Colores.Fondo)) {
        val ancho = maxWidth >= 900.dp
        val secciones = buildList {
            add(SeccionPanel.SESION to "Sesión")
            add(SeccionPanel.LISTA_DEFECTO to "Lista por defecto")
            if (modelo.esAdmin) add(SeccionPanel.OPERACION to "Operación")
        }
        val nombreLocal = modelo.reproduccion?.establecimiento ?: "Panel del personal"
        val quien = "${cuenta.nombre} · ${if (cuenta.rol == Rol.ADMIN) "Administrador" else "DJ"}"

        val contenido: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (ancho) 32.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (modelo.sinConexion) TextoError("Sin conexión con el servidor. Seguimos intentando…")
                    when (modelo.seccion) {
                        SeccionPanel.SESION -> SeccionSesion(modelo, miniaturas, ancho)
                        SeccionPanel.LISTA_DEFECTO -> SeccionListaDefecto(modelo, miniaturas, ancho)
                        SeccionPanel.OPERACION -> SeccionOperacion(modelo)
                    }
                }
            }
        }

        if (ancho) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(250.dp).fillMaxHeight().background(Colores.Tarjeta).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(nombreLocal, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 23.sp)
                    Text(quien, color = Colores.TextoSecundario, fontSize = 13.sp, modifier = Modifier.padding(bottom = 18.dp))
                    secciones.forEach { (seccion, nombre) ->
                        ElementoMenu(nombre, elegido = modelo.seccion == seccion, alElegir = { modelo.seccion = seccion })
                    }
                    Box(Modifier.weight(1f))
                    ElementoMenu("Salir de la cuenta", elegido = false, alElegir = modelo::salir)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { contenido() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().background(Colores.Tarjeta).padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nombreLocal, color = Colores.Texto, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(quien, color = Colores.TextoSecundario, fontSize = 12.sp)
                        }
                        Text(
                            "Salir", color = Colores.TextoSecundario, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(role = Role.Button, onClick = modelo::salir).padding(10.dp),
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                        secciones.forEach { (seccion, nombre) ->
                            PestanaMenu(nombre, elegida = modelo.seccion == seccion, alElegir = { modelo.seccion = seccion })
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { contenido() }
            }
        }

        aviso?.let { MensajePanel(it, Modifier.align(Alignment.BottomCenter)) }
        HojasPanel(modelo)
    }
}

@Composable
private fun ElementoMenu(texto: String, elegido: Boolean, alElegir: () -> Unit) {
    Text(
        texto,
        color = if (elegido) Colores.Rosa else Colores.Texto,
        fontSize = 15.sp, fontWeight = if (elegido) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
            .background(if (elegido) Colores.TarjetaAlta else Colores.Tarjeta, RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClick = alElegir)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun PestanaMenu(texto: String, elegida: Boolean, alElegir: () -> Unit) {
    Column(
        Modifier.clickable(role = Role.Tab, onClick = alElegir).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            texto, color = if (elegida) Colores.Rosa else Colores.TextoSecundario, fontSize = 14.sp,
            fontWeight = if (elegida) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.heightIn(min = 40.dp).padding(top = 10.dp),
        )
        Box(Modifier.width(40.dp).padding(top = 2.dp).background(if (elegida) Colores.Rosa else Colores.Tarjeta).heightIn(min = 3.dp))
    }
}

@Composable
private fun MensajePanel(aviso: AvisoPanel, modifier: Modifier) {
    val fondo = if (aviso.esError) Colores.AvisoSuave else Colores.Cian
    val texto = if (aviso.esError) Colores.Aviso else Colores.TextoSobreNeon
    Row(
        modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth()
            .background(fondo, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!aviso.esError) IconoListo(texto)
        Text(aviso.texto, color = texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
    }
}