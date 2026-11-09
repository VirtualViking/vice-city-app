package vicecity.web.acceso

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vicecity.compartido.CodigosError
import vicecity.compartido.EstablecimientoPublico
import vicecity.web.datos.Almacen
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ErrorDeApi
import vicecity.web.datos.Navegador

/** Lo que ve el cliente desde que escanea el QR hasta que puede buscar (RF-01). */
sealed interface EstadoAcceso {
    /** [lento]: pasaron unos segundos; seguramente el servidor gratuito está despertando. */
    data class Cargando(val lento: Boolean = false) : EstadoAcceso
    /** Se abrió la app sin código y no hay uno guardado: hay que escanear el QR. */
    data object SinCodigo : EstadoAcceso
    data object CodigoInvalido : EstadoAcceso
    data class Error(val mensaje: String) : EstadoAcceso
    /** [bienvenida]: llegó escaneando el QR en el navegador; antes de buscar se le ofrece instalar la app. */
    data class Listo(
        val codigoQr: String,
        val establecimiento: EstablecimientoPublico,
        val bienvenida: Boolean = false,
    ) : EstadoAcceso
}

/**
 * Resuelve el establecimiento a partir del código del QR. El código llega en la URL (?e=…) al escanear,
 * y se guarda para que la app instalada, que abre sin ese parámetro, siga funcionando.
 * Cada vez que se escanea el QR en el navegador se muestra primero la bienvenida; en la app instalada, no.
 */
class ModeloAcceso(private val api: ClienteApi, private val alcance: CoroutineScope) {
    var estado: EstadoAcceso by mutableStateOf(EstadoAcceso.Cargando())
        private set
    private var trabajo: Job? = null
    private var llegoPorQr = false

    fun iniciar() {
        val desdeUrl = Navegador.parametro(PARAMETRO_CODIGO)?.trim()?.lowercase()
        if (desdeUrl != null) {
            Almacen.codigoEstablecimiento = desdeUrl
            Navegador.limpiarParametros()
            llegoPorQr = true
        }
        val codigo = desdeUrl ?: Almacen.codigoEstablecimiento
        if (codigo == null) {
            estado = EstadoAcceso.SinCodigo
            return
        }
        conectar(codigo)
    }

    /** Vuelve a intentar con el código guardado (botón "Volver a intentar"). */
    fun reintentar() = iniciar()

    /** Deja la bienvenida y pasa a la búsqueda, en el navegador o después de abrir la instalación. */
    fun continuar() {
        llegoPorQr = false
        (estado as? EstadoAcceso.Listo)?.let { estado = it.copy(bienvenida = false) }
    }

    private fun conectar(codigo: String) {
        trabajo?.cancel()
        estado = EstadoAcceso.Cargando()
        trabajo = alcance.launch {
            val aviso = launch {
                delay(AVISO_LENTO_MS)
                estado = EstadoAcceso.Cargando(lento = true)
            }
            estado = try {
                EstadoAcceso.Listo(codigo, api.establecimiento(codigo), bienvenida = llegoPorQr && !Navegador.estaInstalada)
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.NO_ENCONTRADO) {
                    Almacen.codigoEstablecimiento = null
                    EstadoAcceso.CodigoInvalido
                } else {
                    EstadoAcceso.Error(e.message)
                }
            } finally {
                aviso.cancel()
            }
        }
    }

    private companion object {
        const val PARAMETRO_CODIGO = "e"
        const val AVISO_LENTO_MS = 3_000L
    }
}