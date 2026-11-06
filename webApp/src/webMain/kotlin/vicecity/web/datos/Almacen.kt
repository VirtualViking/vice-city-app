package vicecity.web.datos

import kotlinx.browser.localStorage
import kotlinx.browser.window

/**
 * Datos que la app guarda en el navegador (localStorage). Si el navegador no lo permite
 * (modo privado estricto), la app sigue funcionando: solo deja de recordar.
 */
object Almacen {
    private const val CLAVE_DISPOSITIVO = "vicecity.dispositivo"
    private const val CLAVE_ESTABLECIMIENTO = "vicecity.establecimiento"
    private const val CLAVE_AVISO_INSTALAR = "vicecity.avisoInstalarCerrado"

    var dispositivo: String?
        get() = leer(CLAVE_DISPOSITIVO)
        set(valor) = escribir(CLAVE_DISPOSITIVO, valor)

    /** Código del QR escaneado. Se guarda porque la app instalada abre sin el "?e=" de la URL. */
    var codigoEstablecimiento: String?
        get() = leer(CLAVE_ESTABLECIMIENTO)
        set(valor) = escribir(CLAVE_ESTABLECIMIENTO, valor)

    var avisoInstalarCerrado: Boolean
        get() = leer(CLAVE_AVISO_INSTALAR) == "si"
        set(valor) = escribir(CLAVE_AVISO_INSTALAR, if (valor) "si" else null)

    private fun leer(clave: String): String? = runCatching { localStorage.getItem(clave) }.getOrNull()

    private fun escribir(clave: String, valor: String?) {
        runCatching { if (valor == null) localStorage.removeItem(clave) else localStorage.setItem(clave, valor) }
    }
}

/** Información del navegador que la app necesita. */
object Navegador {
    /** Valor del parámetro [nombre] en la URL actual (por ejemplo "e" en "?e=pfbztzm9"). */
    fun parametro(nombre: String): String? =
        window.location.search.removePrefix("?").split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.first() == nombre }
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

    /** Quita los parámetros de la URL sin recargar, para que no queden en el historial ni al compartirla. */
    fun limpiarParametros() {
        runCatching { window.history.replaceState(null, "", window.location.pathname) }
    }

    /** iPhone o iPad: allí la instalación se hace desde "Compartir → Agregar a inicio". */
    val esIos: Boolean
        get() {
            val agente = window.navigator.userAgent
            return "iPhone" in agente || "iPad" in agente || ("Macintosh" in agente && window.navigator.maxTouchPoints > 1)
        }

    /** La app ya está instalada y abierta desde su ícono. */
    val estaInstalada: Boolean
        get() = runCatching { window.matchMedia("(display-mode: standalone)").matches }.getOrDefault(false)
}