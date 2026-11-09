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
import vicecity.web.busqueda.PantallaBusqueda
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores
import vicecity.web.tema.TemaViceCity

/** Raíz de la app del cliente: primero resuelve el QR (acceso) y después muestra la búsqueda. */
@Composable
fun App() {
    val api = remember { ClienteApi() }
    val alcance = rememberCoroutineScope()
    val acceso = remember { ModeloAcceso(api, alcance) }
    LaunchedEffect(Unit) { acceso.iniciar() }

    TemaViceCity {
        Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
            when (val estado = acceso.estado) {
                is EstadoAcceso.Listo -> PantallaBusqueda(api, estado.codigoQr, estado.establecimiento)
                else -> PantallaAcceso(estado, alReintentar = acceso::reintentar)
            }
        }
    }
}