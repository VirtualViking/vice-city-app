package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vicecity.compartido.CambioCuentaPersonal
import vicecity.compartido.CodigosError
import vicecity.compartido.CuentaPersonal
import vicecity.compartido.LimitesPersonal
import vicecity.compartido.NuevaCuentaPersonal
import vicecity.compartido.Rol
import vicecity.web.componentes.BotonNeon
import vicecity.web.datos.ErrorDeApi
import vicecity.web.tema.Colores

/** Roles que se pueden asignar, en el orden de las pestañas. */
private val ROLES = listOf(Rol.DJ, Rol.ADMIN)

/** Lo que se edita dentro de la fila de una cuenta. */
private enum class Edicion { NOMBRE, CLAVE, DESACTIVAR }

private class ModeloSeccionPersonal(private val panel: ModeloPanel, private val alcance: CoroutineScope) {
    var cuentas by mutableStateOf<List<CuentaPersonal>?>(null)

    // Formulario de cuenta nueva
    var nombre by mutableStateOf("")
    var email by mutableStateOf("")
    var rol by mutableStateOf(0)
    var clave by mutableStateOf("")
    var creando by mutableStateOf(false)
    var errorCrear by mutableStateOf<String?>(null)

    // Edición dentro de una fila: una a la vez
    var editando by mutableStateOf<Pair<String, Edicion>?>(null)
    var textoEdicion by mutableStateOf("")
    var errorEdicion by mutableStateOf<String?>(null)
    /** Id de la cuenta con un cambio en curso. */
    var ocupada by mutableStateOf<String?>(null)

    fun cargar() = alcance.launch {
        try {
            cuentas = panel.api.personal().cuentas
        } catch (e: ErrorDeApi) {
            panel.alFallar(e)
        }
    }

    fun crear() {
        if (creando) return
        if (nombre.isBlank() || email.isBlank() || clave.isEmpty()) {
            errorCrear = "Completa el nombre, el email y la contraseña."
            return
        }
        creando = true
        errorCrear = null
        val elegido = ROLES.getOrElse(rol) { Rol.DJ }
        alcance.launch {
            try {
                val nueva = panel.api.crearCuenta(NuevaCuentaPersonal(nombre, email, elegido, clave))
                nombre = ""
                email = ""
                clave = ""
                rol = 0
                panel.aviso = AvisoPanel("Cuenta de ${nueva.nombre} creada: ya puede entrar al panel con ${nueva.email}.")
                cuentas = panel.api.personal().cuentas
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.DATOS_INVALIDOS || e.codigo == CodigosError.EMAIL_EN_USO) errorCrear = e.message else panel.alFallar(e)
            } finally {
                creando = false
            }
        }
    }

    fun empezar(cuenta: CuentaPersonal, edicion: Edicion) {
        if (ocupada != null) return
        editando = cuenta.id to edicion
        textoEdicion = if (edicion == Edicion.NOMBRE) cuenta.nombre else ""
        errorEdicion = null
    }

    fun cancelar() {
        editando = null
        textoEdicion = ""
        errorEdicion = null
    }

    fun guardarEdicion(cuenta: CuentaPersonal) {
        when (editando?.second) {
            Edicion.NOMBRE -> cambiar(cuenta, cuenta.cambio(nombre = textoEdicion), "Nombre guardado.")
            Edicion.CLAVE -> cambiarClave(cuenta)
            else -> Unit
        }
    }

    fun cambiarRol(cuenta: CuentaPersonal) {
        val nuevo = if (cuenta.rol == Rol.DJ) Rol.ADMIN else Rol.DJ
        cambiar(
            cuenta, cuenta.cambio(rol = nuevo),
            "${cuenta.nombre} ahora es ${nombreRol(nuevo)}. Si tiene el panel abierto, verá su menú nuevo cuando vuelva a entrar.",
        )
    }

    fun desactivar(cuenta: CuentaPersonal) =
        cambiar(cuenta, cuenta.cambio(activo = false), "Cuenta de ${cuenta.nombre} desactivada: ya no puede entrar al panel.")

    fun reactivar(cuenta: CuentaPersonal) =
        cambiar(cuenta, cuenta.cambio(activo = true), "Cuenta de ${cuenta.nombre} reactivada: puede volver a entrar con su contraseña.")

    private fun cambiar(cuenta: CuentaPersonal, cambio: CambioCuentaPersonal, exito: String) {
        if (ocupada != null) return
        ocupada = cuenta.id
        errorEdicion = null
        alcance.launch {
            try {
                val nueva = panel.api.cambiarCuenta(cuenta.id, cambio)
                if (nueva.esUsted) panel.renombrarCuenta(nueva.nombre)
                cancelar()
                panel.aviso = AvisoPanel(exito)
                cuentas = panel.api.personal().cuentas
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.DATOS_INVALIDOS && editando != null) errorEdicion = e.message else panel.alFallar(e)
            } finally {
                ocupada = null
            }
        }
    }

    private fun cambiarClave(cuenta: CuentaPersonal) {
        if (ocupada != null) return
        if (textoEdicion.isEmpty()) {
            errorEdicion = "Escribe la contraseña nueva."
            return
        }
        ocupada = cuenta.id
        errorEdicion = null
        alcance.launch {
            try {
                panel.api.cambiarClave(cuenta.id, textoEdicion)
                cancelar()
                panel.aviso = AvisoPanel(
                    if (cuenta.esUsted) "Tu contraseña cambió: úsala la próxima vez que entres." else "Contraseña de ${cuenta.nombre} cambiada.",
                )
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.DATOS_INVALIDOS) errorEdicion = e.message else panel.alFallar(e)
            } finally {
                ocupada = null
            }
        }
    }
}

private fun CuentaPersonal.cambio(nombre: String = this.nombre, rol: Rol = this.rol, activo: Boolean = this.activo) =
    CambioCuentaPersonal(nombre, rol, activo)

private fun nombreRol(rol: Rol) = if (rol == Rol.ADMIN) "Administrador" else "DJ"

/** Cuentas del personal (solo administrador, HU-18): crear cuentas de DJ y de administrador, y gestionarlas. */
@Composable
fun SeccionPersonal(panel: ModeloPanel, ancho: Boolean) {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloSeccionPersonal(panel, alcance) }
    LaunchedEffect(Unit) { modelo.cargar() }

    Text("Personal", color = Colores.Texto, fontSize = if (ancho) 30.sp else 24.sp, fontWeight = FontWeight.Black)
    Ayuda(
        "Cada persona entra a este mismo panel con su email y su contraseña. " +
                "La dirección es la de la app terminada en #panel.",
    )

    Tarjeta("Crear cuenta") {
        if (ancho) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CampoTexto(modelo.nombre, { modelo.nombre = it }, "Nombre", Modifier.weight(1f), marcador = "Ej: DJ Nocturno")
                CampoTexto(modelo.email, { modelo.email = it }, "Email", Modifier.weight(1f), marcador = "dj@tubar.com", teclado = KeyboardType.Email)
            }
        } else {
            CampoTexto(modelo.nombre, { modelo.nombre = it }, "Nombre", Modifier.fillMaxWidth(), marcador = "Ej: DJ Nocturno")
            CampoTexto(modelo.email, { modelo.email = it }, "Email", Modifier.fillMaxWidth(), marcador = "dj@tubar.com", teclado = KeyboardType.Email)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rol", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Pestanas(ROLES.map { nombreRol(it) }, modelo.rol, alElegir = { modelo.rol = it })
        }
        Ayuda(
            if (ROLES.getOrElse(modelo.rol) { Rol.DJ } == Rol.DJ) {
                "El DJ abre y cierra la sesión, maneja el reproductor y la cola, la lista por defecto y los bloqueos."
            } else {
                "El administrador puede hacer todo lo del DJ y además ve Operación, Configuración, Personal y Analítica."
            },
        )
        CampoClave(
            modelo.clave, { modelo.clave = it }, "Contraseña inicial",
            Modifier.widthIn(max = 460.dp).fillMaxWidth(), alEnviar = modelo::crear,
        )
        Ayuda(
            "Entre ${LimitesPersonal.CLAVE.first} y ${LimitesPersonal.CLAVE.last} caracteres. Entrégasela en persona o por un mensaje privado, " +
                    "no en un grupo. Si la olvida, aquí mismo le asignas otra.",
        )
        TextoError(modelo.errorCrear)
        BotonNeon(if (modelo.creando) "Creando…" else "Crear cuenta", alPresionar = modelo::crear)
    }

    Tarjeta("Cuentas") {
        val cuentas = modelo.cuentas
        if (cuentas == null) {
            Text("Cargando…", color = Colores.TextoSecundario, fontSize = 15.sp)
        } else {
            cuentas.forEach { FilaCuenta(modelo, it) }
        }
    }
}

@Composable
private fun FilaCuenta(modelo: ModeloSeccionPersonal, c: CuentaPersonal) {
    val edicion = modelo.editando?.takeIf { it.first == c.id }?.second
    val enCurso = modelo.ocupada == c.id
    val libre = modelo.ocupada == null
    Column(
        Modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (c.esUsted) "${c.nombre} (tú)" else c.nombre,
                color = if (c.activo) Colores.Texto else Colores.TextoSecundario, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Etiqueta(nombreRol(c.rol), if (c.rol == Rol.ADMIN) Colores.Rosa else Colores.Cian)
            if (!c.activo) Etiqueta("Desactivada", Colores.Apagado)
        }
        Text(c.email, color = Colores.TextoSecundario, fontSize = 14.sp)

        when (edicion) {
            null -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonFila("Cambiar nombre", alPresionar = { modelo.empezar(c, Edicion.NOMBRE) }, habilitado = libre)
                    BotonFila("Cambiar contraseña", alPresionar = { modelo.empezar(c, Edicion.CLAVE) }, habilitado = libre)
                    if (!c.esUsted) {
                        BotonFila(
                            if (enCurso) "Guardando…" else if (c.rol == Rol.DJ) "Hacer administrador" else "Hacer DJ",
                            alPresionar = { modelo.cambiarRol(c) }, habilitado = libre,
                        )
                        if (c.activo) {
                            BotonFila("Desactivar", alPresionar = { modelo.empezar(c, Edicion.DESACTIVAR) }, color = Colores.Aviso, habilitado = libre)
                        } else {
                            BotonFila("Reactivar", alPresionar = { modelo.reactivar(c) }, color = Colores.Cian, habilitado = libre)
                        }
                    }
                }
                if (c.esUsted) Ayuda("Es tu cuenta: no puedes desactivarla ni quitarte el rol de administrador.")
            }

            Edicion.NOMBRE, Edicion.CLAVE -> {
                if (edicion == Edicion.NOMBRE) {
                    CampoTexto(
                        modelo.textoEdicion, { modelo.textoEdicion = it }, "Nombre nuevo",
                        Modifier.widthIn(max = 420.dp).fillMaxWidth(), alEnviar = { modelo.guardarEdicion(c) },
                    )
                } else {
                    CampoClave(
                        modelo.textoEdicion, { modelo.textoEdicion = it }, "Contraseña nueva",
                        Modifier.widthIn(max = 420.dp).fillMaxWidth(), alEnviar = { modelo.guardarEdicion(c) },
                    )
                    Ayuda(
                        if (c.esUsted) "La usarás la próxima vez que entres. Tu sesión actual sigue abierta."
                        else "Entrégasela a ${c.nombre} en privado. Si tiene el panel abierto, no se le cierra: para eso, desactiva la cuenta.",
                    )
                }
                TextoError(modelo.errorEdicion)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonFila(if (enCurso) "Guardando…" else "Guardar", alPresionar = { modelo.guardarEdicion(c) }, color = Colores.Cian, habilitado = libre)
                    BotonFila("Cancelar", alPresionar = modelo::cancelar, habilitado = libre)
                }
            }

            Edicion.DESACTIVAR -> {
                Text(
                    "¿Desactivar la cuenta de ${c.nombre}? Si tiene el panel abierto, se le cierra al momento. Puedes reactivarla después.",
                    color = Colores.Texto, fontSize = 14.sp, lineHeight = 20.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonFila(if (enCurso) "Desactivando…" else "Sí, desactivar", alPresionar = { modelo.desactivar(c) }, color = Colores.Aviso, habilitado = libre)
                    BotonFila("Cancelar", alPresionar = modelo::cancelar, habilitado = libre)
                }
            }
        }
    }
}

/** Campo de contraseña con un botón para verla mientras se escribe. */
@Composable
private fun CampoClave(valor: String, alCambiar: (String) -> Unit, etiqueta: String, modifier: Modifier, alEnviar: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CampoTexto(valor, alCambiar, etiqueta, Modifier.weight(1f), contrasena = !visible, alEnviar = alEnviar)
        // 5 dp para centrar el botón (38 dp) con el campo (48 dp)
        Box(Modifier.padding(bottom = 5.dp)) {
            BotonFila(if (visible) "Ocultar" else "Ver", alPresionar = { visible = !visible })
        }
    }
}

@Composable
private fun Ayuda(texto: String) {
    Text(texto, color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp)
}