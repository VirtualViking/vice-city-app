package vicecity.web.cliente

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vicecity.compartido.CodigosError
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.ItemCola
import vicecity.compartido.ResultadoBusqueda
import vicecity.compartido.ResultadoSolicitud
import vicecity.web.busqueda.ModeloBusqueda
import vicecity.web.busqueda.PantallaBusqueda
import vicecity.web.cola.HojaConfirmar
import vicecity.web.cola.HojaLimiteSolicitudes
import vicecity.web.cola.HojaLimiteVotos
import vicecity.web.cola.ModeloCola
import vicecity.web.cola.PantallaCola
import vicecity.web.cola.ResultadoPedido
import vicecity.web.componentes.IconoBuscar
import vicecity.web.componentes.IconoCola
import vicecity.web.componentes.IconoListo
import vicecity.web.datos.ClienteApi
import vicecity.web.tema.Colores

private enum class Pestana { BUSCAR, COLA }

/** Hoja abierta sobre la pantalla. */
private sealed interface Hoja {
    data class Confirmar(val resultado: ResultadoBusqueda, val error: String? = null) : Hoja
    data class LimiteSolicitudes(val proximaEn: String?) : Hoja
    data object LimiteVotos : Hoja
}

/** Mensaje corto sobre las pestañas, que se oculta solo. */
private data class Aviso(val texto: String, val esError: Boolean = false)

/**
 * App del cliente ya dentro del establecimiento: pestañas Buscar y Cola, y las hojas para pedir canciones.
 * Con una sesión abierta la cola se consulta cada 5 s. Sin sesión, el establecimiento se consulta cada 20 s
 * para tomar la sesión en cuanto el DJ la abra; [alActualizar] hace esa consulta.
 */
@Composable
fun PantallaCliente(api: ClienteApi, codigoQr: String, establecimiento: EstablecimientoPublico, alActualizar: () -> Unit) {
    val alcance = rememberCoroutineScope()
    val busqueda = remember(codigoQr) { ModeloBusqueda(api, codigoQr, alcance) }
    val lista = rememberLazyGridState()
    val sesionId = establecimiento.sesionActiva?.id
    val cola = remember(sesionId) { sesionId?.let { ModeloCola(api, it, alcance) } }
    DisposableEffect(cola) {
        cola?.iniciar()
        onDispose { cola?.detener() }
    }
    val estadoCola = cola?.estado
    val sesionCerrada = estadoCola?.sesionCerrada == true
    LaunchedEffect(sesionId, sesionCerrada) {
        if (sesionId != null && !sesionCerrada) return@LaunchedEffect
        while (true) {
            alActualizar()
            delay(ESPERA_SIN_SESION_MS)
        }
    }

    var pestana by remember { mutableStateOf(Pestana.BUSCAR) }
    var hoja by remember { mutableStateOf<Hoja?>(null) }
    var aviso by remember { mutableStateOf<Aviso?>(null) }
    LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(DURACION_AVISO_MS)
            aviso = null
        }
    }

    // Al tocar un resultado: si ya no puede pedir ni votar, se le explica de una vez; si no, se confirma
    val elegir = { resultado: ResultadoBusqueda ->
        val actividad = estadoCola?.actividad
        val enCola = estadoCola?.enCola(resultado.videoId)
        val yaTieneSuVoto = enCola != null && (estadoCola.esMia(enCola.solicitudId) || estadoCola.yaVote(enCola.solicitudId))
        hoja = when {
            actividad == null || sesionCerrada -> Hoja.Confirmar(resultado)
            enCola == null && actividad.solicitudesRestantes == 0 -> Hoja.LimiteSolicitudes(actividad.proximaSolicitudEn)
            enCola != null && !yaTieneSuVoto && actividad.votosRestantes == 0 -> Hoja.LimiteVotos
            else -> Hoja.Confirmar(resultado)
        }
    }

    val pedir = pedir@{ resultado: ResultadoBusqueda ->
        val modelo = cola ?: return@pedir
        alcance.launch {
            when (val r = modelo.solicitar(resultado.videoId)) {
                is ResultadoPedido.Hecho -> {
                    hoja = null
                    pestana = Pestana.COLA
                    val posicion = r.posicion?.let { " Va en la posición $it." }.orEmpty()
                    aviso = Aviso(if (r.resultado == ResultadoSolicitud.SOLICITUD_CREADA) "Solicitud enviada.$posicion" else "Sumaste tu voto.$posicion")
                }
                is ResultadoPedido.Fallo -> hoja = when (r.error.codigo) {
                    CodigosError.LIMITE_SOLICITUDES -> Hoja.LimiteSolicitudes(r.error.detalles["reintentarEn"])
                    CodigosError.LIMITE_VOTOS -> Hoja.LimiteVotos
                    else -> Hoja.Confirmar(resultado, r.error.message)
                }
            }
        }
        Unit
    }

    val votar = votar@{ item: ItemCola ->
        val modelo = cola ?: return@votar
        if (modelo.estado.actividad?.votosRestantes == 0) {
            hoja = Hoja.LimiteVotos
            return@votar
        }
        alcance.launch {
            val error = modelo.votar(item.solicitudId)
            when (error?.codigo) {
                null -> {
                    val quedan = modelo.estado.actividad?.votosRestantes
                    aviso = Aviso(if (quedan == null) "Voto registrado." else "Voto registrado. Te quedan $quedan ${if (quedan == 1) "voto" else "votos"}.")
                }
                CodigosError.LIMITE_VOTOS -> hoja = Hoja.LimiteVotos
                else -> aviso = Aviso(error.message, esError = true)
            }
        }
        Unit
    }

    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (pestana) {
                    Pestana.BUSCAR -> PantallaBusqueda(api, busqueda, lista, establecimiento, alElegir = elegir)
                    Pestana.COLA -> PantallaCola(
                        api, estadoCola?.takeUnless { it.sesionCerrada },
                        alVotar = votar, alPedir = { pestana = Pestana.BUSCAR }, alReintentar = alActualizar,
                    )
                }
            }
            BarraNavegacion(pestana, enCola = estadoCola?.cola?.cola?.size, alElegir = { pestana = it })
        }

        // Encima de las pestañas, para no tapar los títulos
        aviso?.let { MensajeAviso(it, Modifier.align(Alignment.BottomCenter).padding(bottom = ALTO_PESTANAS)) }

        when (val h = hoja) {
            is Hoja.Confirmar -> HojaConfirmar(
                api, h.resultado, estadoCola, h.error,
                alSolicitar = { pedir(h.resultado) },
                alVerCola = {
                    hoja = null
                    pestana = Pestana.COLA
                },
                alCerrar = { hoja = null },
            )
            is Hoja.LimiteSolicitudes -> HojaLimiteSolicitudes(
                estadoCola?.actividad, h.proximaEn ?: estadoCola?.actividad?.proximaSolicitudEn,
                alIrACola = {
                    hoja = null
                    pestana = Pestana.COLA
                },
                alCerrar = { hoja = null },
            )
            Hoja.LimiteVotos -> HojaLimiteVotos(estadoCola?.actividad, alCerrar = { hoja = null })
            null -> {}
        }
    }
}

/** Pestañas de abajo. [enCola]: cuántas canciones hay en la cola, o null si no se sabe. */
@Composable
private fun BarraNavegacion(actual: Pestana, enCola: Int?, alElegir: (Pestana) -> Unit) {
    Box(Modifier.fillMaxWidth().background(Colores.Tarjeta), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().height(ALTO_PESTANAS)) {
            listOf(Pestana.BUSCAR, Pestana.COLA).forEach { pestana ->
                val elegida = pestana == actual
                val color = if (elegida) Colores.Rosa else Colores.Apagado
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab, onClick = { alElegir(pestana) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    if (pestana == Pestana.BUSCAR) IconoBuscar(color, tamano = 22.dp) else IconoCola(color)
                    Text(
                        if (pestana == Pestana.BUSCAR) "Buscar" else if (enCola != null) "Cola ($enCola)" else "Cola",
                        color = color, fontSize = 12.sp, fontWeight = if (elegida) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MensajeAviso(aviso: Aviso, modifier: Modifier) {
    val fondo = if (aviso.esError) Colores.AvisoSuave else Colores.Cian
    val texto = if (aviso.esError) Colores.Aviso else Colores.TextoSobreNeon
    Row(
        modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth()
            .background(fondo, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!aviso.esError) IconoListo(texto)
        Text(aviso.texto, color = texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
    }
}

private val ALTO_PESTANAS = 64.dp
private const val ESPERA_SIN_SESION_MS = 20_000L
private const val DURACION_AVISO_MS = 4_000L