package vicecity.web.busqueda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vicecity.compartido.FuenteBusqueda
import vicecity.compartido.ResultadoBusqueda
import vicecity.compartido.Sugerencia
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ErrorDeApi

/**
 * Estado de la pantalla de búsqueda. Es inmutable: cada cambio crea una copia y Compose redibuja.
 * [resultados] es null mientras no se ha buscado nada.
 */
data class EstadoBusqueda(
    val texto: String = "",
    val sugerencias: List<Sugerencia> = emptyList(),
    val cargando: Boolean = false,
    val resultados: List<ResultadoBusqueda>? = null,
    val fuente: FuenteBusqueda? = null,
    val buscado: String = "",
    val error: String? = null,
)

/**
 * Lógica de la búsqueda (RF-04): autocompletado mientras se escribe y búsqueda al confirmar.
 * Las pantallas solo leen [estado] y llaman a estas funciones.
 */
class ModeloBusqueda(
    private val api: ClienteApi,
    private val codigoQr: String,
    private val alcance: CoroutineScope,
) {
    var estado by mutableStateOf(EstadoBusqueda())
        private set
    private var trabajoSugerencias: Job? = null
    private var trabajoBusqueda: Job? = null

    /** Pide sugerencias 300 ms después de la última tecla; si llega otra tecla antes, cancela la anterior. */
    fun cambiarTexto(texto: String) {
        estado = estado.copy(texto = texto, error = null)
        trabajoSugerencias?.cancel()
        val consulta = texto.trim()
        if (consulta.length < LARGO_MINIMO) {
            estado = estado.copy(sugerencias = emptyList())
            return
        }
        trabajoSugerencias = alcance.launch {
            delay(ESPERA_SUGERENCIAS_MS)
            val respuesta = runCatching { api.sugerir(codigoQr, consulta) }.getOrNull() ?: return@launch
            estado = estado.copy(sugerencias = respuesta.sugerencias)
        }
    }

    fun buscar(texto: String = estado.texto) {
        val consulta = texto.trim()
        if (consulta.length < LARGO_MINIMO) {
            estado = estado.copy(error = "Escribe al menos $LARGO_MINIMO letras para buscar.")
            return
        }
        trabajoSugerencias?.cancel()
        trabajoBusqueda?.cancel()
        estado = estado.copy(texto = texto, sugerencias = emptyList(), cargando = true, error = null)
        trabajoBusqueda = alcance.launch {
            estado = try {
                val respuesta = api.buscar(codigoQr, consulta)
                estado.copy(cargando = false, resultados = respuesta.resultados, fuente = respuesta.fuente, buscado = consulta)
            } catch (e: ErrorDeApi) {
                estado.copy(cargando = false, resultados = null, fuente = null, error = e.message)
            }
        }
    }

    fun limpiar() {
        trabajoSugerencias?.cancel()
        trabajoBusqueda?.cancel()
        estado = EstadoBusqueda()
    }

    private companion object {
        const val LARGO_MINIMO = 2
        const val ESPERA_SUGERENCIAS_MS = 300L
    }
}

/** 225 → "3:45" */
fun formatoDuracion(segundos: Int): String {
    val minutos = segundos / 60
    val resto = (segundos % 60).toString().padStart(2, '0')
    return "$minutos:$resto"
}