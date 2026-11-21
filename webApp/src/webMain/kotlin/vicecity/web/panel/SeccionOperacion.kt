package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vicecity.compartido.ConfiguracionOperacion
import vicecity.compartido.DiaSemana
import vicecity.compartido.EstadoEstablecimiento
import vicecity.compartido.EstadoOperacion
import vicecity.compartido.Horario
import vicecity.compartido.ResultadoLimpieza
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.BotonSecundario
import vicecity.web.componentes.IconoListo
import vicecity.web.datos.ErrorDeApi
import vicecity.web.datos.HoraColombia
import vicecity.web.tema.Colores

/** Horario de un día mientras se edita. */
private data class DiaEditable(val abre: Boolean, val apertura: String, val cierre: String)

private class ModeloOperacion(private val panel: ModeloPanel, private val alcance: CoroutineScope) {
    var estado by mutableStateOf<EstadoOperacion?>(null)
    var automatica by mutableStateOf(false)
    var dias by mutableStateOf<Map<DiaSemana, DiaEditable>>(emptyMap())
    var guardando by mutableStateOf(false)
    var cambiando by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var limpieza by mutableStateOf<ResultadoLimpieza?>(null)

    fun cargar() = alcance.launch {
        try {
            aplicar(panel.api.operacion())
        } catch (e: ErrorDeApi) {
            panel.alFallar(e)
        }
    }

    fun cambiarDia(dia: DiaSemana, nuevo: DiaEditable) {
        dias = dias + (dia to nuevo)
    }

    fun guardar() {
        val horarios = DiaSemana.entries.mapNotNull { dia ->
            dias[dia]?.takeIf { it.abre }?.let { Horario(dia, it.apertura.trim(), it.cierre.trim()) }
        }
        guardando = true
        error = null
        alcance.launch {
            try {
                aplicar(panel.api.configurarOperacion(ConfiguracionOperacion(automatica, horarios)))
                panel.aviso = AvisoPanel("Horario guardado.")
                panel.refrescar()
            } catch (e: ErrorDeApi) {
                error = e.message
            } finally {
                guardando = false
            }
        }
    }

    fun mantenimiento(activo: Boolean) {
        cambiando = true
        error = null
        limpieza = null
        alcance.launch {
            try {
                aplicar(panel.api.mantenimiento(activo))
                panel.aviso = AvisoPanel(if (activo) "Mantenimiento activado: el QR está desactivado." else "Mantenimiento terminado.")
                panel.refrescar()
            } catch (e: ErrorDeApi) {
                error = e.message
            } finally {
                cambiando = false
            }
        }
    }

    fun limpiar() {
        cambiando = true
        error = null
        alcance.launch {
            try {
                limpieza = panel.api.limpiar()
            } catch (e: ErrorDeApi) {
                error = e.message
            } finally {
                cambiando = false
            }
        }
    }

    private fun aplicar(nuevo: EstadoOperacion) {
        estado = nuevo
        automatica = nuevo.programacionAutomatica
        val guardados = nuevo.horarios.associateBy { it.dia }
        dias = DiaSemana.entries.associateWith { dia ->
            guardados[dia]?.let { DiaEditable(true, it.apertura, it.cierre) } ?: DiaEditable(false, "20:00", "02:00")
        }
    }
}

/** Operación del establecimiento (solo administrador): horario, programación automática, mantenimiento y limpieza. */
@Composable
fun SeccionOperacion(panel: ModeloPanel) {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloOperacion(panel, alcance) }
    LaunchedEffect(Unit) { modelo.cargar() }
    val estado = modelo.estado

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Operación", color = Colores.Texto, fontSize = 30.sp, fontWeight = FontWeight.Black)
        if (estado != null) {
            val (texto, color) = when (estado.estado) {
                EstadoEstablecimiento.ABIERTO -> "QR activo: sesión abierta" to Colores.Cian
                EstadoEstablecimiento.SIN_SESION -> "QR activo: la sesión aún no se abre" to Colores.Durazno
                EstadoEstablecimiento.FUERA_DE_HORARIO -> "QR desactivado: fuera de horario" to Colores.Apagado
                EstadoEstablecimiento.MANTENIMIENTO -> "QR desactivado: en mantenimiento" to Colores.Aviso
            }
            Etiqueta(texto, color)
            val proxima = estado.proximaApertura
            if (proxima != null && estado.estado != EstadoEstablecimiento.ABIERTO) {
                Text("Próxima apertura: ${HoraColombia.diaYHora(proxima)}", color = Colores.TextoSecundario, fontSize = 14.sp)
            } else if (estado.estado == EstadoEstablecimiento.ABIERTO && estado.proximoCierre != null && estado.programacionAutomatica) {
                Text("Cierre automático: ${HoraColombia.diaYHora(estado.proximoCierre)}", color = Colores.TextoSecundario, fontSize = 14.sp)
            }
        }
    }
    if (estado == null) {
        Text("Cargando…", color = Colores.TextoSecundario, fontSize = 15.sp)
        return
    }
    TextoError(modelo.error)

    Tarjeta("Horario y programación automática") {
        Interruptor(
            titulo = "Programación automática",
            explicacion = "Abre la sesión a la hora de apertura y la cierra a la hora de cierre. El DJ puede cerrarla antes o abrir una fuera del horario.",
            activo = modelo.automatica,
            alCambiar = { modelo.automatica = it },
        )
        Text(
            "Horario en hora de Colombia (HH:mm). Si el cierre es anterior a la apertura, cierra al día siguiente. Fuera del horario el QR queda desactivado.",
            color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
        )
        DiaSemana.entries.forEach { dia ->
            val valor = modelo.dias[dia] ?: return@forEach
            FilaDia(dia, valor, alCambiar = { modelo.cambiarDia(dia, it) })
        }
        BotonNeon(if (modelo.guardando) "Guardando…" else "Guardar horario", alPresionar = { if (!modelo.guardando) modelo.guardar() })
    }

    Tarjeta("Mantenimiento") {
        Interruptor(
            titulo = if (estado.mantenimiento) "Mantenimiento activo" else "Mantenimiento inactivo",
            explicacion = "Entre sesiones: desactiva el QR y no deja abrir sesión, ni a mano ni automáticamente. Para activarlo, cierra antes la sesión.",
            activo = estado.mantenimiento,
            alCambiar = { modelo.mantenimiento(it) },
            habilitado = !modelo.cambiando,
        )
        if (estado.mantenimiento) {
            Text(
                "Limpiar cierra las solicitudes que hayan quedado pendientes, borra las búsquedas vencidas, quita los bloqueos vencidos " +
                        "y hace que la lista por defecto vuelva a empezar por la primera canción.",
                color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
            )
            BotonSecundario(if (modelo.cambiando) "Limpiando…" else "Limpiar", alPresionar = { if (!modelo.cambiando) modelo.limpiar() })
            modelo.limpieza?.let { ResumenLimpieza(it) }
        }
    }
}

@Composable
private fun FilaDia(dia: DiaSemana, valor: DiaEditable, alCambiar: (DiaEditable) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(150.dp).clickable(role = Role.Checkbox) { alCambiar(valor.copy(abre = !valor.abre)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Casilla(valor.abre)
            Text(nombreDia(dia), color = if (valor.abre) Colores.Texto else Colores.Apagado, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (valor.abre) {
            CampoTexto(valor.apertura, { alCambiar(valor.copy(apertura = it)) }, "", Modifier.width(110.dp), marcador = "20:00")
            Text("a", color = Colores.TextoSecundario, fontSize = 14.sp)
            CampoTexto(valor.cierre, { alCambiar(valor.copy(cierre = it)) }, "", Modifier.width(110.dp), marcador = "02:00")
        } else {
            Text("No abre", color = Colores.Apagado, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Casilla(marcada: Boolean) {
    Box(
        Modifier.size(22.dp)
            .background(if (marcada) Colores.Cian else Colores.Fondo, RoundedCornerShape(6.dp))
            .border(2.dp, if (marcada) Colores.Cian else Colores.Borde, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) IconoListo(Colores.TextoSobreNeon, tamano = 16.dp)
    }
}

@Composable
private fun ResumenLimpieza(r: ResultadoLimpieza) {
    Column(
        Modifier.fillMaxWidth().background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Limpieza terminada", color = Colores.Cian, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Solicitudes pendientes cerradas: ${r.solicitudesCerradas}", color = Colores.Texto, fontSize = 14.sp)
        Text("Búsquedas vencidas borradas: ${r.busquedasVencidas}", color = Colores.Texto, fontSize = 14.sp)
        Text("Bloqueos vencidos quitados: ${r.bloqueosVencidos}", color = Colores.Texto, fontSize = 14.sp)
        Text(
            if (r.rotacionReiniciada) "La lista por defecto vuelve a empezar por la primera canción." else "La lista por defecto ya empezaba por la primera canción.",
            color = Colores.Texto, fontSize = 14.sp,
        )
    }
}

private fun nombreDia(dia: DiaSemana) = when (dia) {
    DiaSemana.LUNES -> "Lunes"
    DiaSemana.MARTES -> "Martes"
    DiaSemana.MIERCOLES -> "Miércoles"
    DiaSemana.JUEVES -> "Jueves"
    DiaSemana.VIERNES -> "Viernes"
    DiaSemana.SABADO -> "Sábado"
    DiaSemana.DOMINGO -> "Domingo"
}