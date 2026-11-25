package vicecity.web.panel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vicecity.compartido.CodigosError
import vicecity.compartido.EliminacionSolicitud
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.ListaBloqueos
import vicecity.compartido.LoginRespuesta
import vicecity.compartido.MotivoEliminacion
import vicecity.compartido.Rol
import vicecity.web.datos.Almacen
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ClientePersonal
import vicecity.web.datos.ErrorDeApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class SeccionPanel { SESION, LISTA_DEFECTO, OPERACION, CONFIGURACION, ANALITICA }

/** Mensaje corto que se muestra unos segundos. */
data class AvisoPanel(val texto: String, val esError: Boolean = false)

/**
 * Estado del panel del personal: la cuenta, la sección abierta y lo que suena.
 * Con la cuenta abierta, el estado del reproductor se consulta cada 3 segundos (la vista pública lo hace cada 2).
 * Si el token vence, el panel vuelve al inicio de sesión.
 */
class ModeloPanel(val api: ClientePersonal, private val alcance: CoroutineScope) {
    var cuenta by mutableStateOf(cuentaVigente())
        private set
    var entrando by mutableStateOf(false)
        private set
    var errorEntrada by mutableStateOf<String?>(null)
        private set
    var seccion by mutableStateOf(SeccionPanel.SESION)
    var reproduccion by mutableStateOf<EstadoReproduccion?>(null)
        private set
    var bloqueos by mutableStateOf<ListaBloqueos?>(null)
        private set
    var sinConexion by mutableStateOf(false)
        private set
    /** Acción en curso (por ejemplo "saltar"): su botón muestra que espera. */
    var ocupado by mutableStateOf<String?>(null)
        private set
    var aviso by mutableStateOf<AvisoPanel?>(null)
    var hoja by mutableStateOf<HojaPanel?>(null)

    val esAdmin: Boolean get() = cuenta?.rol == Rol.ADMIN
    private var sondeo: Job? = null

    fun entrar(email: String, clave: String) {
        if (email.isBlank() || clave.isBlank()) {
            errorEntrada = "Escribe tu email y tu contraseña"
            return
        }
        entrando = true
        errorEntrada = null
        alcance.launch {
            try {
                val nueva = api.entrar(email.trim(), clave)
                Almacen.cuenta = nueva
                cuenta = nueva
                seccion = SeccionPanel.SESION
            } catch (e: ErrorDeApi) {
                errorEntrada = if (e.codigo == CodigosError.CREDENCIALES_INVALIDAS) "Email o contraseña incorrectos" else e.message
            } finally {
                entrando = false
            }
        }
    }

    fun salir() {
        detener()
        Almacen.cuenta = null
        cuenta = null
        reproduccion = null
        bloqueos = null
    }

    fun iniciar() {
        if (sondeo?.isActive == true) return
        sondeo = alcance.launch {
            var vuelta = 0
            while (cuenta != null) {
                refrescar(conBloqueos = vuelta % 10 == 0)
                vuelta++
                delay(INTERVALO_MS)
            }
        }
    }

    fun detener() {
        sondeo?.cancel()
        sondeo = null
    }

    suspend fun refrescar(conBloqueos: Boolean = false) {
        try {
            reproduccion = api.reproductor()
            if (conBloqueos) bloqueos = api.bloqueos()
            sinConexion = false
        } catch (e: ErrorDeApi) {
            alFallar(e, silencioso = true)
        }
    }

    // ---------- Acciones de la sesión y del reproductor

    fun abrirSesion() = ejecutar("abrir", "Sesión abierta: los clientes ya pueden pedir canciones.") { api.abrirSesion() }

    fun cerrarSesion() {
        val sesionId = reproduccion?.sesion?.id ?: return
        ejecutar("cerrar", "Sesión cerrada.") { api.cerrarSesion(sesionId) }
    }

    fun pausarSolicitudes(pausadas: Boolean) {
        val sesionId = reproduccion?.sesion?.id ?: return
        val texto = if (pausadas) "Solicitudes en pausa: la música sigue sonando." else "Solicitudes reanudadas."
        ejecutar("pausaSolicitudes", texto) { api.pausarSolicitudes(sesionId, pausadas) }
    }

    fun iniciarMusica() = ejecutarReproductor("iniciar") { api.iniciar() }
    fun pausarMusica() = ejecutarReproductor("pausar") { api.pausar() }
    fun reanudarMusica() = ejecutarReproductor("reanudar") { api.reanudar() }
    fun saltar() = ejecutarReproductor("saltar", "Canción saltada.") { api.saltar() }

    fun eliminar(solicitudId: String, motivo: MotivoEliminacion, bloquear: Boolean) {
        val texto = if (bloquear) "Canción eliminada y bloqueada por 24 horas." else "Canción eliminada de la cola."
        ejecutarReproductor("eliminar", texto) { api.eliminar(solicitudId, EliminacionSolicitud(motivo, bloquear)) }
        if (bloquear) alcance.launch { delay(600); refrescar(conBloqueos = true) }
    }

    fun desbloquear(videoId: String) {
        ejecutar("desbloquear", "Canción desbloqueada: se puede volver a pedir.") { bloqueos = api.desbloquear(videoId) }
    }

    /** Ejecuta [accion], muestra [exito] y vuelve a consultar el estado. */
    private fun ejecutar(nombre: String, exito: String? = null, accion: suspend () -> Unit) {
        if (ocupado != null) return
        ocupado = nombre
        alcance.launch {
            try {
                accion()
                if (exito != null) aviso = AvisoPanel(exito)
                refrescar()
            } catch (e: ErrorDeApi) {
                alFallar(e)
            } finally {
                ocupado = null
            }
        }
    }

    /** Las acciones del reproductor ya devuelven el estado nuevo: no hace falta otra consulta. */
    private fun ejecutarReproductor(nombre: String, exito: String? = null, accion: suspend () -> EstadoReproduccion) {
        if (ocupado != null) return
        ocupado = nombre
        alcance.launch {
            try {
                reproduccion = accion()
                if (exito != null) aviso = AvisoPanel(exito)
            } catch (e: ErrorDeApi) {
                alFallar(e)
            } finally {
                ocupado = null
            }
        }
    }

    fun alFallar(e: ErrorDeApi, silencioso: Boolean = false) {
        when (e.codigo) {
            CodigosError.NO_AUTENTICADO -> {
                salir()
                errorEntrada = "Tu sesión en el panel venció. Vuelve a entrar."
            }
            ClienteApi.SIN_CONEXION -> sinConexion = true
            else -> if (!silencioso) aviso = AvisoPanel(e.message, esError = true)
        }
    }

    private companion object {
        const val INTERVALO_MS = 3_000L

        /** La cuenta guardada, si su token no ha vencido. */
        @OptIn(ExperimentalTime::class)
        fun cuentaVigente(): LoginRespuesta? {
            val guardada = Almacen.cuenta ?: return null
            val vence = runCatching { Instant.parse(guardada.expiraEn) }.getOrNull() ?: return null
            return guardada.takeIf { vence > Clock.System.now() }
        }
    }
}