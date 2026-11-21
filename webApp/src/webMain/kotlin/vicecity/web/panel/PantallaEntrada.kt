package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.TituloNeon
import vicecity.web.tema.Colores

/** Inicio de sesión del personal (HU-02). */
@Composable
fun PantallaEntrada(modelo: ModeloPanel) {
    var email by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    val entrar = { modelo.entrar(email, clave) }

    Box(Modifier.fillMaxSize().background(Colores.Fondo).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TituloNeon("Panel del personal", tamano = 30.sp)
            Text(
                "Entra con la cuenta que te dio el administrador para controlar la música de esta noche.",
                color = Colores.TextoSecundario, fontSize = 15.sp, lineHeight = 21.sp,
            )
            Column(
                Modifier.fillMaxWidth().background(Colores.Tarjeta, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CampoTexto(email, { email = it }, "Email", marcador = "nombre@correo.com", teclado = KeyboardType.Email)
                CampoTexto(clave, { clave = it }, "Contraseña", contrasena = true, alEnviar = entrar)
                TextoError(modelo.errorEntrada)
                BotonNeon(if (modelo.entrando) "Entrando…" else "Entrar", alPresionar = { if (!modelo.entrando) entrar() }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "Si el servidor estaba dormido, la primera entrada puede tardar hasta un minuto.",
                color = Colores.Apagado, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}