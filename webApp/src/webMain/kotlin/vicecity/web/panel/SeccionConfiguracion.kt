package vicecity.web.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as ImagenSkia
import vicecity.compartido.CambioConfiguracion
import vicecity.compartido.CodigosError
import vicecity.compartido.ConfiguracionEstablecimiento
import vicecity.compartido.LimitesConfiguracion
import vicecity.compartido.ReglasCola
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.IconoDescargar
import vicecity.web.datos.AccionesNavegador
import vicecity.web.datos.ErrorDeApi
import vicecity.web.tema.Colores

/** Tamaño de descarga del QR: nombre, píxeles de lado y para qué sirve. */
private data class TamanoQr(val nombre: String, val pixeles: Int, val uso: String)

private val TAMANOS_QR = listOf(
    TamanoQr("Pequeño", 512, "Para compartir en pantallas o mensajes."),
    TamanoQr("Mediano", 1024, "Para imprimir en una hoja o en tarjetas para las mesas."),
    TamanoQr("Grande", 2048, "Para afiches y pendones."),
)

/** Lado de la vista previa del QR en la pantalla, en píxeles. */
private const val TAMANO_VISTA = 512

private class ModeloConfiguracion(private val panel: ModeloPanel, private val alcance: CoroutineScope) {
    var actual by mutableStateOf<ConfiguracionEstablecimiento?>(null)
    var nombre by mutableStateOf("")
    var limite by mutableStateOf("")
    var ventana by mutableStateOf("")
    var antirepeticion by mutableStateOf("")
    var duracion by mutableStateOf("")
    var qr by mutableStateOf<ImageBitmap?>(null)
    var tamano by mutableStateOf(1)
    var guardandoNombre by mutableStateOf(false)
    var guardandoReglas by mutableStateOf(false)
    var descargando by mutableStateOf(false)
    var errorNombre by mutableStateOf<String?>(null)
    var errorReglas by mutableStateOf<String?>(null)
    var errorQr by mutableStateOf<String?>(null)

    fun cargar() = alcance.launch {
        try {
            aplicar(panel.api.configuracion())
        } catch (e: ErrorDeApi) {
            panel.alFallar(e)
            return@launch
        }
        try {
            qr = ImagenSkia.makeFromEncoded(panel.api.qr(TAMANO_VISTA)).toComposeImageBitmap()
        } catch (e: ErrorDeApi) {
            errorQr = "No se pudo cargar la imagen del QR. ${e.message}"
        } catch (e: Throwable) {
            errorQr = "No se pudo mostrar la imagen del QR."
        }
    }

    /** Guarda el nombre; las reglas van como están guardadas, no como estén en edición. */
    fun guardarNombre() {
        val c = actual ?: return
        if (guardandoNombre) return
        guardandoNombre = true
        errorNombre = null
        alcance.launch {
            try {
                aplicarNombre(panel.api.guardarConfiguracion(CambioConfiguracion(nombre, c.reglas)))
                panel.aviso = AvisoPanel("Nombre guardado: los clientes lo verán la próxima vez que abran la app.")
                panel.refrescar()
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.DATOS_INVALIDOS) errorNombre = e.message else panel.alFallar(e)
            } finally {
                guardandoNombre = false
            }
        }
    }

    /** Guarda las reglas; el nombre va como está guardado. */
    fun guardarReglas() {
        val c = actual ?: return
        if (guardandoReglas) return
        val l = limite.trim().toIntOrNull()
        val v = ventana.trim().toIntOrNull()
        val a = antirepeticion.trim().toIntOrNull()
        val d = duracion.trim().toIntOrNull()
        if (l == null || v == null || a == null || d == null) {
            errorReglas = "Escribe un número entero en cada campo."
            return
        }
        guardandoReglas = true
        errorReglas = null
        alcance.launch {
            try {
                aplicarReglas(panel.api.guardarConfiguracion(CambioConfiguracion(c.nombre, ReglasCola(l, v, a, d))))
                panel.aviso = AvisoPanel("Reglas guardadas: aplican desde la siguiente solicitud.")
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.DATOS_INVALIDOS) errorReglas = e.message else panel.alFallar(e)
            } finally {
                guardandoReglas = false
            }
        }
    }

    fun descargar() {
        val c = actual ?: return
        if (descargando) return
        val elegido = TAMANOS_QR.getOrElse(tamano) { TAMANOS_QR.first() }
        descargando = true
        errorQr = null
        alcance.launch {
            try {
                val bytes = panel.api.qr(elegido.pixeles)
                AccionesNavegador.descargarPng("qr-${c.codigoQr}-${elegido.pixeles}px.png", bytes)
                panel.aviso = AvisoPanel("QR descargado en tamaño ${elegido.nombre.lowercase()} (${elegido.pixeles} px).")
            } catch (e: ErrorDeApi) {
                errorQr = e.message
            } finally {
                descargando = false
            }
        }
    }

    fun copiarEnlace() {
        val c = actual ?: return
        panel.aviso = if (AccionesNavegador.copiar(c.enlace)) {
            AvisoPanel("Enlace copiado.")
        } else {
            AvisoPanel("El navegador no dejó copiar el enlace. Selecciónalo y cópialo a mano.", esError = true)
        }
    }

    private fun aplicar(nueva: ConfiguracionEstablecimiento) {
        aplicarNombre(nueva)
        aplicarReglas(nueva)
    }

    private fun aplicarNombre(nueva: ConfiguracionEstablecimiento) {
        actual = nueva
        nombre = nueva.nombre
    }

    private fun aplicarReglas(nueva: ConfiguracionEstablecimiento) {
        actual = nueva
        limite = nueva.reglas.limiteSolicitudes.toString()
        ventana = nueva.reglas.ventanaLimiteMin.toString()
        antirepeticion = nueva.reglas.antirepeticionMin.toString()
        duracion = nueva.reglas.duracionMaxMin.toString()
    }
}

/** Configuración del establecimiento (solo administrador, HU-15 y HU-13): nombre, código QR y reglas de la cola. */
@Composable
fun SeccionConfiguracion(panel: ModeloPanel, ancho: Boolean) {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloConfiguracion(panel, alcance) }
    LaunchedEffect(Unit) { modelo.cargar() }
    val c = modelo.actual

    Text("Configuración", color = Colores.Texto, fontSize = if (ancho) 30.sp else 24.sp, fontWeight = FontWeight.Black)
    if (c == null) {
        Text("Cargando…", color = Colores.TextoSecundario, fontSize = 15.sp)
        return
    }

    Tarjeta("Establecimiento") {
        CampoTexto(
            modelo.nombre, { modelo.nombre = it }, "Nombre del establecimiento",
            Modifier.widthIn(max = 460.dp).fillMaxWidth(), alEnviar = modelo::guardarNombre,
        )
        Ayuda(
            "Se ve en la bienvenida, en la búsqueda, en la vista pública y en este panel. " +
                    "Debe tener entre ${LimitesConfiguracion.NOMBRE.first} y ${LimitesConfiguracion.NOMBRE.last} caracteres.",
        )
        TextoError(modelo.errorNombre)
        BotonNeon(if (modelo.guardandoNombre) "Guardando…" else "Guardar nombre", alPresionar = modelo::guardarNombre)
    }

    Tarjeta("Código QR") {
        if (ancho) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                VistaQr(modelo.qr, c.nombre)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { DatosQr(modelo, c) }
            }
        } else {
            VistaQr(modelo.qr, c.nombre)
            DatosQr(modelo, c)
        }
    }

    Tarjeta("Reglas de la cola") {
        Ayuda("Se aplican desde la siguiente solicitud. Las canciones que ya están en la cola no cambian.")
        FilaRegla(
            explicacion = "Cuando un celular llega al límite, la app le dice cuánto le falta para volver a pedir. " +
                    "De ${rango(LimitesConfiguracion.LIMITE_SOLICITUDES)} solicitudes, cada ${rango(LimitesConfiguracion.VENTANA_LIMITE_MIN)} minutos.",
        ) {
            Text("Cada celular puede pedir", color = Colores.Texto, fontSize = 15.sp)
            CampoNumero(modelo.limite, { modelo.limite = it }, "Solicitudes por celular")
            Text("canciones cada", color = Colores.Texto, fontSize = 15.sp)
            CampoNumero(modelo.ventana, { modelo.ventana = it }, "Ventana del límite en minutos")
            Text("minutos", color = Colores.Texto, fontSize = 15.sp)
        }
        FilaRegla(
            explicacion = "Después de sonar, la canción no se puede volver a pedir durante ese tiempo. " +
                    "Con 0 se puede pedir enseguida. De ${rango(LimitesConfiguracion.ANTIREPETICION_MIN)} minutos.",
        ) {
            Text("Anti-repetición:", color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            CampoNumero(modelo.antirepeticion, { modelo.antirepeticion = it }, "Anti-repetición en minutos")
            Text("minutos", color = Colores.Texto, fontSize = 15.sp)
        }
        FilaRegla(
            explicacion = "Los videos más largos no aparecen en la búsqueda ni se pueden pedir. " +
                    "De ${rango(LimitesConfiguracion.DURACION_MAX_MIN)} minutos.",
        ) {
            Text("Duración máxima del video:", color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            CampoNumero(modelo.duracion, { modelo.duracion = it }, "Duración máxima en minutos")
            Text("minutos", color = Colores.Texto, fontSize = 15.sp)
        }
        TextoError(modelo.errorReglas)
        BotonNeon(if (modelo.guardandoReglas) "Guardando…" else "Guardar reglas", alPresionar = modelo::guardarReglas)
    }
}

/** Vista previa del QR sobre fondo blanco, como queda impreso. */
@Composable
private fun VistaQr(imagen: ImageBitmap?, nombre: String) {
    Box(
        Modifier.size(220.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (imagen != null) {
            Image(imagen, contentDescription = "Código QR de $nombre", modifier = Modifier.size(200.dp))
        } else {
            Text("Cargando QR…", color = Colores.Apagado, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DatosQr(modelo: ModeloConfiguracion, c: ConfiguracionEstablecimiento) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Enlace del QR", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(c.enlace, color = Colores.Texto, fontSize = 15.sp, lineHeight = 21.sp)
    }
    BotonFila("Copiar enlace", alPresionar = modelo::copiarEnlace)
    Text("Tamaño de la imagen", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Pestanas(TAMANOS_QR.map { it.nombre }, modelo.tamano, alElegir = { modelo.tamano = it })
    val elegido = TAMANOS_QR.getOrElse(modelo.tamano) { TAMANOS_QR.first() }
    Ayuda("Imagen de ${elegido.pixeles} px de lado. ${elegido.uso}")
    TextoError(modelo.errorQr)
    BotonNeon(
        if (modelo.descargando) "Descargando…" else "Descargar PNG",
        alPresionar = modelo::descargar,
        icono = { IconoDescargar(Colores.TextoSobreNeon) },
    )
    Ayuda("El código ${c.codigoQr} no cambia: los QR que ya imprimiste siguen funcionando.")
}

/** Una regla: sus campos en una fila y la explicación debajo. */
@Composable
private fun FilaRegla(explicacion: String, campos: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { campos() }
        Ayuda(explicacion)
    }
}

/** Campo corto que solo acepta dígitos. */
@Composable
private fun CampoNumero(valor: String, alCambiar: (String) -> Unit, descripcion: String) {
    CampoTexto(
        valor, { nuevo -> alCambiar(nuevo.filter { it.isDigit() }.take(3)) }, "",
        Modifier.width(84.dp).semantics { contentDescription = descripcion }, teclado = KeyboardType.Number,
    )
}

@Composable
private fun Ayuda(texto: String) {
    Text(texto, color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp)
}

/** "1 a 20" */
private fun rango(r: IntRange) = "${r.first} a ${r.last}"