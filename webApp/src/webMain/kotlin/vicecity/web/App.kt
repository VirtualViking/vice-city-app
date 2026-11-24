package vicecity.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.browser.window
import org.w3c.dom.events.Event
import vicecity.web.acceso.EstadoAcceso
import vicecity.web.acceso.ModeloAcceso
import vicecity.web.acceso.PantallaAcceso
import vicecity.web.bienvenida.PantallaBienvenida
import vicecity.web.cliente.PantallaCliente
import vicecity.web.datos.ClienteApi
import vicecity.web.panel.PanelPersonal
import vicecity.web.pantalla.PantallaPublica
import vicecity.web.privacidad.PantallaPrivacidad
import vicecity.web.tema.Colores
import vicecity.web.tema.TemaViceCity

/**
 * Raíz de la web. La misma app sirve cuatro pantallas según la dirección:
 * - ".../#panel": panel del personal (DJ y administrador).
 * - ".../#pantalla": vista pública con el reproductor, para la pantalla del local.
 * - ".../#privacidad": política de privacidad para el cliente.
 * - cualquier otra: la app del cliente que abre el QR.
 * Si la dirección cambia (por ejemplo, al abrir la política y volver), la pantalla cambia sin recargar.
 */
@Composable
fun App() {
    var ruta by remember { mutableStateOf(rutaActual()) }
    DisposableEffect(Unit) {
        val alCambiar: (Event) -> Unit = { ruta = rutaActual() }
        window.addEventListener("hashchange", alCambiar)
        onDispose { window.removeEventListener("hashchange", alCambiar) }
    }
    when (ruta) {
        "panel" -> TemaViceCity { PanelPersonal() }
        "pantalla" -> TemaViceCity { PantallaPublica() }
        "privacidad" -> TemaViceCity { PantallaPrivacidad() }
        else -> AppCliente()
    }
}

private fun rutaActual(): String = window.location.hash.removePrefix("#")

/** App del cliente: primero resuelve el QR (acceso), luego la bienvenida si llegó por el QR, y después la búsqueda y la cola. */
@Composable
private fun AppCliente() {
    val api = remember { ClienteApi() }
    val alcance = rememberCoroutineScope()
    val acceso = remember { ModeloAcceso(api, alcance) }
    LaunchedEffect(Unit) { acceso.iniciar() }

    TemaViceCity {
        Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
            when (val estado = acceso.estado) {
                is EstadoAcceso.Listo -> if (estado.bienvenida) {
                    PantallaBienvenida(estado.establecimiento, alContinuar = acceso::continuar)
                } else {
                    PantallaCliente(api, estado.codigoQr, estado.establecimiento, alActualizar = acceso::actualizar)
                }
                else -> PantallaAcceso(estado, alReintentar = acceso::reintentar)
            }
        }
    }
}