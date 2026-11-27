package vicecity.web.pantalla

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vicecity.compartido.CodigosError
import vicecity.compartido.ElementoActual
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.FinReproduccion
import vicecity.compartido.ReproduccionFallida
import vicecity.web.datos.Almacen
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ClientePersonal
import vicecity.web.datos.ErrorDeApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.TimeSource
import org.jetbrains.skia.Image as ImagenSkia

/**
 * Vista pública (RF-10, RF-11, RF-25): reproduce lo que el servidor dice que suena y le avisa cuando termina o falla.
 * El servidor decide siempre qué sigue; esta pantalla solo obedece. Consulta el estado cada 2 segundos (HU-07),
 * así las acciones del DJ (pausar, saltar, eliminar) se ven aquí casi enseguida.
 * Los navegadores no dejan reproducir con sonido sin un toque: por eso hay que tocar "Iniciar" una vez.
 * También muestra el QR del establecimiento con el ícono de la app (RF-13, HU-17).
 */
class ModeloPantalla(private val api: ClientePersonal, private val recursos: ClienteApi, private val alcance: CoroutineScope) {
    var estado by mutableStateOf<EstadoReproduccion?>(null)
        private set
    var iniciada by mutableStateOf(false)
        private set
    var listo by mutableStateOf(false)
        private set
    var problema by mutableStateOf<String?>(null)
        private set
    var sinCuenta by mutableStateOf(Almacen.cuenta == null)
        private set
    /** QR del establecimiento y el ícono que va en su centro. Se cargan una vez: el código del QR no cambia. */
    var qr by mutableStateOf<ImageBitmap?>(null)
        private set
    var icono by mutableStateOf<ImageBitmap?>(null)
        private set

    /** "videoId|inicioEn" de lo que tiene cargado el reproductor de YouTube. */
    private var cargado: String? = null
    /** Cuándo se cargó [cargado] y si ya empezó a sonar (o se pausó), para detectar videos que no arrancan. */
    private var cargadoEn: TimeSource.Monotonic.ValueTimeMark? = null
    private var arranco = false
    private var avisando = false
    private var sondeo: Job? = null

    fun iniciarSondeo() {
        if (sondeo?.isActive == true) return
        sondeo = alcance.launch {
            var vuelta = 0
            while (!sinCuenta) {
                consultar()
                // El QR se pide al empezar y, si no se pudo, se reintenta cada 30 segundos
                if ((qr == null || icono == null) && vuelta % VUELTAS_REINTENTO_QR == 0) cargarQr()
                vuelta++
                delay(INTERVALO_MS)
            }
        }
    }

    fun detener() {
        sondeo?.cancel()
        sondeo = null
    }

    /** Eventos del reproductor de YouTube. */
    fun alEvento(tipo: String, detalle: String) {
        when (tipo) {
            "listo" -> {
                listo = true
                sincronizar()
            }
            "reproduciendo", "pausado" -> arranco = true
            "terminado" -> avisarFin()
            "error" -> avisarFalla(detalle.toIntOrNull() ?: 0)
            "sin-api" -> problema = "No se pudo cargar el reproductor de YouTube. Revisa la conexión a internet y recarga la página."
        }
    }

    /** El toque que habilita el sonido. Si la música estaba detenida, la enciende. */
    fun iniciar() {
        iniciada = true
        alcance.launch {
            try {
                if (estado?.sesion?.estadoReproductor == EstadoReproductor.DETENIDO) estado = api.iniciar()
            } catch (e: ErrorDeApi) {
                alFallar(e)
            }
            sincronizar()
        }
    }

    private suspend fun consultar() {
        try {
            estado = api.reproductor()
            problema = null
            sincronizar()
        } catch (e: ErrorDeApi) {
            alFallar(e)
        }
    }

    private suspend fun cargarQr() {
        if (qr == null) {
            try {
                qr = imagen(api.qr(TAMANO_QR))
            } catch (e: ErrorDeApi) {
                // Sin conexión o servidor dormido: se reintenta en la siguiente ronda
            }
        }
        if (icono == null) icono = recursos.iconoApp()?.let(::imagen)
    }

    private fun imagen(bytes: ByteArray): ImageBitmap? =
        runCatching { ImagenSkia.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    /** Lleva el reproductor de YouTube a lo que dice el servidor: qué video y si suena o está en pausa. */
    private fun sincronizar() {
        if (!iniciada || !listo) return
        val actual = estado?.actual
        val reproductor = estado?.sesion?.estadoReproductor ?: EstadoReproductor.DETENIDO
        if (actual == null) {
            if (cargado != null) {
                ViceCityReproductor.detener()
                cargado = null
            }
            // Sin canción, el recuadro queda oscuro: no se queda a la vista el último mensaje de YouTube
            ViceCityReproductor.mostrar(false)
            return
        }
        val clave = "${actual.videoId}|${actual.inicioEn}"
        if (clave != cargado) {
            val transcurridos = segundosDesde(actual.inicioEn)
            // Si la pantalla se abrió cuando la canción ya debía haber terminado, se pasa a la siguiente
            if (transcurridos > actual.duracionSeg + MARGEN_SEG) {
                cargado = clave
                arranco = true   // no se cargó nada que vigilar
                avisarFin()
                return
            }
            cargado = clave
            cargadoEn = TimeSource.Monotonic.markNow()
            arranco = false
            ViceCityReproductor.mostrar(true)
            ViceCityReproductor.cargar(actual.videoId, if (transcurridos < MARGEN_SEG) 0 else transcurridos)
        }
        when (reproductor) {
            EstadoReproductor.REPRODUCIENDO -> if (ViceCityReproductor.estado() == PAUSADO_YT) ViceCityReproductor.reproducir()
            else -> if (ViceCityReproductor.estado() == REPRODUCIENDO_YT) ViceCityReproductor.pausar()
        }
        vigilarArranque(reproductor)
    }

    /**
     * Algunos videos bloqueados muestran el mensaje de YouTube sin disparar su evento de error.
     * Si el video debía sonar y a los 10 segundos sigue sin empezar y sin duración, se avisa como falla (SIN_ARRANQUE):
     * el servidor lo salta y lo recuerda. Con internet lento no pasa: el reproductor queda "cargando" y ya conoce la duración.
     */
    private fun vigilarArranque(reproductor: EstadoReproductor) {
        if (arranco || reproductor != EstadoReproductor.REPRODUCIENDO) return
        val desde = cargadoEn ?: return
        if (desde.elapsedNow() < ESPERA_ARRANQUE) return
        val estadoYt = ViceCityReproductor.estado()
        if (estadoYt != SIN_EMPEZAR_YT && estadoYt != EN_ESPERA_YT) return
        if (ViceCityReproductor.duracion() > 0.0) return
        avisarFalla(ReproduccionFallida.SIN_ARRANQUE)
    }

    private fun avisarFin() {
        val actual = estado?.actual ?: return
        avisar(actual) { api.terminada(FinReproduccion(actual.videoId, actual.inicioEn)) }
    }

    /** RF-25: el video no se pudo reproducir (por ejemplo, su dueño no permite verlo incrustado). */
    private fun avisarFalla(codigo: Int) {
        val actual = estado?.actual ?: return
        avisar(actual) { api.fallida(ReproduccionFallida(actual.videoId, actual.inicioEn, codigo)) }
    }

    private fun avisar(actual: ElementoActual, aviso: suspend () -> EstadoReproduccion) {
        if (avisando) return
        avisando = true
        alcance.launch {
            try {
                estado = aviso()
            } catch (e: ErrorDeApi) {
                alFallar(e)
            } finally {
                avisando = false
            }
            sincronizar()
        }
    }

    private fun alFallar(e: ErrorDeApi) {
        when (e.codigo) {
            CodigosError.NO_AUTENTICADO -> {
                sinCuenta = true
                detener()
            }
            ClienteApi.SIN_CONEXION -> problema = "Sin conexión con el servidor. La música sigue; seguimos intentando…"
            else -> problema = e.message
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun segundosDesde(iso: String): Int {
        val inicio = runCatching { Instant.parse(iso) }.getOrNull() ?: return 0
        return (Clock.System.now() - inicio).inWholeSeconds.toInt().coerceAtLeast(0)
    }

    private companion object {
        const val INTERVALO_MS = 2_000L
        /** 15 rondas de 2 segundos: 30 segundos entre intentos de cargar el QR. */
        const val VUELTAS_REINTENTO_QR = 15
        /** Píxeles del PNG: se ve nítido incluso en pantallas de alta densidad. */
        const val TAMANO_QR = 600
        const val MARGEN_SEG = 5
        const val SIN_EMPEZAR_YT = -1
        const val REPRODUCIENDO_YT = 1
        const val PAUSADO_YT = 2
        const val EN_ESPERA_YT = 5
        /** Lo que espera la vista pública a que un video empiece antes de darlo por fallido. */
        val ESPERA_ARRANQUE = 10.seconds
    }
}