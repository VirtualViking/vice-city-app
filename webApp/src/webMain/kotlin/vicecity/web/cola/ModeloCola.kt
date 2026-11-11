package vicecity.web.cola

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vicecity.compartido.CodigosError
import vicecity.compartido.EstadoCola
import vicecity.compartido.ItemCola
import vicecity.compartido.MiActividad
import vicecity.compartido.ResultadoSolicitud
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ErrorDeApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Lo que el cliente sabe de la cola. Es inmutable: cada cambio crea una copia y Compose redibuja.
 * [cola] y [actividad] son null hasta la primera respuesta del servidor.
 */
data class EstadoColaCliente(
    val cola: EstadoCola? = null,
    val actividad: MiActividad? = null,
    val sinConexion: Boolean = false,
    val sesionCerrada: Boolean = false,
    /** videoId que se está pidiendo o solicitudId que se está votando: su botón espera la respuesta. */
    val pendientes: Set<String> = emptySet(),
) {
    val canciones: List<ItemCola> get() = cola?.cola.orEmpty()
    private val mias: Set<String> get() = actividad?.solicitudes.orEmpty().map { it.solicitudId }.toSet()
    private val votadas: Set<String> get() = actividad?.votos.orEmpty().toSet()

    fun esMia(solicitudId: String) = solicitudId in mias
    fun yaVote(solicitudId: String) = solicitudId in votadas
    fun enCola(videoId: String): ItemCola? = canciones.firstOrNull { it.videoId == videoId }
    fun posicion(solicitudId: String): Int? = canciones.indexOfFirst { it.solicitudId == solicitudId }.takeIf { it >= 0 }?.plus(1)

    /** Una canción nueva entra con 1 voto: queda detrás de todas las que ya están en la cola. */
    val posicionNueva: Int get() = canciones.size + 1
}

/** Resultado de pedir una canción, para que la pantalla muestre el aviso o el error. */
sealed interface ResultadoPedido {
    data class Hecho(val resultado: ResultadoSolicitud, val posicion: Int?) : ResultadoPedido
    data class Fallo(val error: ErrorDeApi) : ResultadoPedido
}

/**
 * Cola de la sesión abierta (RF-05 a RF-09): la consulta cada 5 segundos, y pide y vota canciones.
 * Lo propio del dispositivo (sus solicitudes, sus votos y lo que le queda) se consulta cada 30 segundos
 * y después de cada acción, porque solo cambia cuando el mismo dispositivo hace algo.
 * Si la red falla al pedir o votar, se reintenta con la misma Idempotency-Key: el servidor no duplica nada (RNF-18).
 */
class ModeloCola(
    private val api: ClienteApi,
    val sesionId: String,
    private val alcance: CoroutineScope,
) {
    var estado by mutableStateOf(EstadoColaCliente())
        private set
    private var sondeo: Job? = null

    fun iniciar() {
        if (sondeo?.isActive == true) return
        sondeo = alcance.launch {
            var vuelta = 0
            while (!estado.sesionCerrada) {
                actualizar(conActividad = vuelta % VUELTAS_POR_ACTIVIDAD == 0)
                vuelta++
                delay(INTERVALO_MS)
            }
        }
    }

    fun detener() {
        sondeo?.cancel()
        sondeo = null
    }

    suspend fun solicitar(videoId: String): ResultadoPedido = conPendiente(videoId) {
        try {
            val respuesta = conReintentos { clave -> api.solicitar(sesionId, videoId, clave) }
            actualizar(conActividad = true)
            ResultadoPedido.Hecho(respuesta.resultado, estado.posicion(respuesta.solicitud.solicitudId))
        } catch (e: ErrorDeApi) {
            alFallar(e)
            ResultadoPedido.Fallo(e)
        }
    }

    /** Devuelve null si el voto quedó registrado, o el error para mostrarlo. */
    suspend fun votar(solicitudId: String): ErrorDeApi? = conPendiente(solicitudId) {
        try {
            val respuesta = conReintentos { clave -> api.votar(solicitudId, clave) }
            // Se marca enseguida; la consulta que sigue trae el orden nuevo de la cola
            estado = estado.copy(
                cola = estado.cola?.let { c -> c.copy(cola = c.cola.map { if (it.solicitudId == solicitudId) it.copy(votos = respuesta.votos) else it }) },
                actividad = estado.actividad?.let { a -> a.copy(votos = a.votos + solicitudId, votosRestantes = (a.votosRestantes - 1).coerceAtLeast(0)) },
            )
            actualizar(conActividad = true)
            null
        } catch (e: ErrorDeApi) {
            alFallar(e)
            e
        }
    }

    /** Marca [id] como pendiente mientras dura [accion]: la pantalla no deja tocar dos veces el mismo botón. */
    private suspend fun <T> conPendiente(id: String, accion: suspend () -> T): T {
        estado = estado.copy(pendientes = estado.pendientes + id)
        return try {
            accion()
        } finally {
            estado = estado.copy(pendientes = estado.pendientes - id)
        }
    }

    private suspend fun actualizar(conActividad: Boolean) {
        try {
            coroutineScope {
                val actividad = if (conActividad || estado.actividad == null) async { api.miActividad(sesionId) } else null
                val cola = api.cola(sesionId)
                estado = estado.copy(cola = cola, actividad = actividad?.await() ?: estado.actividad, sinConexion = false)
            }
        } catch (e: ErrorDeApi) {
            // La sesión ya no existe: para la app es lo mismo que cerrada
            if (e.codigo == CodigosError.NO_ENCONTRADO) estado = estado.copy(sesionCerrada = true) else alFallar(e)
        }
    }

    private fun alFallar(e: ErrorDeApi) {
        when (e.codigo) {
            CodigosError.SESION_CERRADA -> estado = estado.copy(sesionCerrada = true)
            ClienteApi.SIN_CONEXION -> estado = estado.copy(sinConexion = true)
            // Un límite o un voto repetido puede venir de otra pestaña: se refresca lo propio
            CodigosError.LIMITE_SOLICITUDES, CodigosError.LIMITE_VOTOS, CodigosError.YA_VOTADO ->
                alcance.launch { actualizar(conActividad = true) }
        }
    }

    /** Hasta 3 intentos si no hay red, siempre con la misma clave; cualquier otro error se devuelve enseguida. */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun <T> conReintentos(accion: suspend (clave: String) -> T): T {
        val clave = Uuid.random().toString()
        var intento = 0
        while (true) {
            try {
                return accion(clave)
            } catch (e: ErrorDeApi) {
                if (e.codigo != ClienteApi.SIN_CONEXION || intento >= 2) throw e
                intento++
                delay(1_000L * intento)
            }
        }
    }

    private companion object {
        const val INTERVALO_MS = 5_000L
        const val VUELTAS_POR_ACTIVIDAD = 6
    }
}

/** Minutos que faltan para la fecha ISO [iso], redondeados hacia arriba (mínimo 1). */
@OptIn(ExperimentalTime::class)
fun minutosHasta(iso: String?): Int {
    if (iso == null) return 1
    val segundos = runCatching { (Instant.parse(iso) - Clock.System.now()).inWholeSeconds }.getOrDefault(60)
    return ((segundos + 59) / 60).toInt().coerceAtLeast(1)
}