package vicecity.web.privacidad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.browser.window
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.TituloNeon
import vicecity.web.tema.Colores

/** Política de privacidad visible para el cliente (RF-31, HU-19). Se abre en ".../#privacidad". */
@Composable
fun PantallaPrivacidad() {
    Box(Modifier.fillMaxSize().background(Colores.Fondo).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TituloNeon("Política de privacidad", tamano = 30.sp)
            Parrafo("Vice City App no te pide registro ni datos personales: no te pedimos nombre, número de teléfono, correo ni ubicación.")

            Seccion("Qué datos se recogen") {
                Punto("Un identificador aleatorio que tu navegador crea la primera vez que abres la app y que se guarda solo en él. No está ligado a tu nombre, a tu número ni a tu celular.")
                Punto("Las canciones que buscas, pides y votas, con la hora en que lo haces.")
            }
            Seccion("Para qué se usan") {
                Punto("Para operar la cola: aplicar el límite de solicitudes, contar un solo voto por canción y evitar que una canción se repita seguido.")
                Punto("Para generar estadísticas agregadas del establecimiento, como las canciones más pedidas o los horarios con más participación.")
            }
            Seccion("Cómo se protegen") {
                Punto("Nuestro servidor no guarda tu identificador original. Guarda una versión transformada con una clave secreta (HMAC-SHA256), que no permite recuperar el original.")
                Punto("Para las estadísticas, esa versión se reemplaza por un código que solo vale durante una noche. Cuando la sesión se cierra, la clave que generó ese código se borra y ya no se puede relacionar con tu navegador.")
                Punto("Solo el administrador del establecimiento ve las estadísticas, y siempre agregadas. El DJ no las ve. No compartimos estos datos con terceros.")
            }
            Seccion("Cuánto tiempo se guardan") {
                Punto("Tus solicitudes y votos: 90 días después del cierre de la sesión.")
                Punto("La versión transformada de tu identificador: 12 meses desde tu última visita.")
                Punto("El registro interno de lo que pasa en la app: 24 meses.")
                Punto("Las estadísticas anónimas se conservan, porque no permiten saber quién eres.")
            }
            Seccion("Cómo dejar de participar") {
                Parrafo("Borra los datos de este sitio en tu navegador. La próxima vez que entres tendrás un identificador nuevo, sin relación con el anterior.")
            }
            BotonNeon("Volver", alPresionar = ::volver, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** Enlace "Política de privacidad" para la bienvenida y la búsqueda. */
@Composable
fun EnlacePrivacidad(modifier: Modifier = Modifier) {
    Text(
        "Política de privacidad",
        color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button) { window.location.hash = RUTA }.padding(vertical = 10.dp),
    )
}

/** Vuelve a la pantalla anterior; si la política se abrió directo, va a la app. */
private fun volver() {
    if (window.history.length > 1) window.history.back() else window.location.hash = ""
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Colores.Tarjeta, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(titulo, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        contenido()
    }
}

@Composable
private fun Punto(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(Colores.Cian, CircleShape))
        Text(texto, color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Parrafo(texto: String) {
    Text(texto, color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 22.sp)
}

private const val RUTA = "privacidad"