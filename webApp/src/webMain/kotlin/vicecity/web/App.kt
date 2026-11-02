package vicecity.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.compartido.EstadoBaseDatos

private val Fondo = Color(0xFF0D0E12)
private val Tarjeta = Color(0xFF17191F)
private val Acento = Color(0xFFC8F25A)
private val TextoSecundario = Color(0xFFA7ABB5)
private val Alerta = Color(0xFFFFB547)

/** Pantalla provisional del paso 5: comprueba la cadena web → servidor → base de datos. */
@Composable
fun App() {
    val api = remember { ClienteApi() }
    var intento by remember { mutableIntStateOf(0) }
    var servidor by remember { mutableStateOf("Consultando…") }
    var baseDatos by remember { mutableStateOf("—") }
    var colorBase by remember { mutableStateOf(TextoSecundario) }

    LaunchedEffect(intento) {
        servidor = "Consultando… (si el servidor estaba dormido puede tardar hasta un minuto)"
        baseDatos = "—"
        try {
            val salud = api.salud()
            servidor = "Responde (versión ${salud.version})"
            baseDatos = when (salud.baseDatos) {
                EstadoBaseDatos.CONECTADA -> "Conectada"
                EstadoBaseDatos.NO_CONFIGURADA -> "No configurada (falta DATABASE_URL)"
                EstadoBaseDatos.ERROR -> "Error de conexión"
            }
            colorBase = if (salud.baseDatos == EstadoBaseDatos.CONECTADA) Acento else Alerta
        } catch (e: Throwable) {
            servidor = "Sin respuesta: ${e.message ?: "error de red"}"
            colorBase = Alerta
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Acento, background = Fondo, surface = Tarjeta)) {
        Box(Modifier.fillMaxSize().background(Fondo), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("VICE CITY APP", color = Acento, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Estructura base lista", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Esta pantalla verifica que la web llega al servidor y el servidor a la base de datos. Se reemplaza en la iteración 1.", color = TextoSecundario, fontSize = 14.sp)

                Column(
                    Modifier.fillMaxWidth().background(Tarjeta, RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Fila("Versión web", plataforma, Color.White)
                    Fila("Servidor", servidor, Color.White)
                    Fila("Base de datos", baseDatos, colorBase)
                    Fila("API", api.urlBase, TextoSecundario)
                }

                Button(
                    onClick = { intento++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Fondo),
                ) { Text("Volver a consultar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(etiqueta, color = TextoSecundario, fontSize = 12.sp)
        Text(valor, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
