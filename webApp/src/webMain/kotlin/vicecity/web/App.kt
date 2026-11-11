package vicecity.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import vicecity.web.acceso.EstadoAcceso
import vicecity.web.acceso.ModeloAcceso
import vicecity.web.acceso.PantallaAcceso
import vicecity.web.bienvenida.PantallaBienvenida
import vicecity.web.cliente.PantallaCliente
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores
import vicecity.web.tema.TemaViceCity

/** Raíz de la app del cliente: primero resuelve el QR (acceso), luego la bienvenida si llegó por el QR, y después la búsqueda y la cola. */
@Composable
fun App() {
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