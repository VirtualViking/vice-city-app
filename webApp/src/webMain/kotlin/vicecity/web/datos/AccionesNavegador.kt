package vicecity.web.datos

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Acciones del navegador que la app necesita: descargar un archivo y copiar texto. */
object AccionesNavegador {
    /**
     * Guarda [bytes] como un archivo PNG en las descargas del navegador.
     * Se usa un enlace "data:" porque la imagen ya está en memoria: la ruta del servidor pide el token
     * y un enlace normal no lo puede enviar.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun descargarPng(nombreArchivo: String, bytes: ByteArray) {
        val enlace = document.createElement("a") as HTMLAnchorElement
        enlace.href = "data:image/png;base64," + Base64.encode(bytes)
        enlace.download = nombreArchivo
        document.body?.appendChild(enlace)
        enlace.click()
        document.body?.removeChild(enlace)
    }

    /** Copia [texto] al portapapeles. Devuelve false si el navegador no tiene portapapeles (por ejemplo, una página sin https). */
    fun copiar(texto: String): Boolean = try {
        window.navigator.clipboard.writeText(texto)
        true
    } catch (e: Throwable) {
        false
    }
}