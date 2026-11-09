package vicecity.web.acceso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import vicecity.web.componentes.MensajeCentral
import vicecity.web.tema.Colores

/** Pantallas previas a la búsqueda: cargando, sin código, código inválido o error de conexión. */
@Composable
fun PantallaAcceso(estado: EstadoAcceso, alReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (estado) {
            is EstadoAcceso.Cargando -> MensajeCentral(
                titulo = "Conectando con el establecimiento",
                texto = if (estado.lento) {
                    "El servidor se está despertando. La primera vez puede tardar hasta un minuto."
                } else {
                    "Un momento…"
                },
                icono = { CircularProgressIndicator(color = Colores.Rosa) },
            )
            EstadoAcceso.SinCodigo -> MensajeCentral(
                titulo = "Escanea el código QR",
                texto = "Para pedir música, escanea con la cámara el código QR que está en el establecimiento.",
            )
            EstadoAcceso.CodigoInvalido -> MensajeCentral(
                titulo = "Este código no es válido",
                texto = "El código QR no corresponde a ningún establecimiento. Escanea de nuevo el código que está en el lugar.",
            )
            is EstadoAcceso.Error -> MensajeCentral(
                titulo = "No pudimos conectarnos",
                texto = estado.mensaje,
                accion = "Volver a intentar",
                alAccionar = alReintentar,
            )
            is EstadoAcceso.Listo -> Unit
        }
    }
}