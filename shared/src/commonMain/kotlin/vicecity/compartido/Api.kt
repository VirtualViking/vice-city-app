package vicecity.compartido

/** Prefijo común de todos los endpoints REST (contrato de la API). */
const val API_PREFIJO = "/api/v1"

/** Header con el UUID anónimo que la PWA genera y guarda en el navegador (contrato, "Identificación del cliente"). */
const val HEADER_DISPOSITIVO = "X-Dispositivo-Id"


/**
 * Header de los POST de solicitudes y votos (RNF-18). Si el cliente reintenta con la misma clave,
 * el servidor responde lo mismo que la primera vez sin duplicar la solicitud, el voto ni los eventos.
 */


const val HEADER_IDEMPOTENCIA = "Idempotency-Key"

/** Rutas usadas tanto por el servidor como por la web. */
object Rutas {
    const val SALUD = "$API_PREFIJO/salud"
    const val LOGIN = "$API_PREFIJO/auth/login"
    const val SESIONES = "$API_PREFIJO/admin/sesiones"
    const val SESION_ACTIVA = "$SESIONES/activa"
    const val ESTABLECIMIENTO_QR = "$API_PREFIJO/admin/establecimiento/qr"

    fun cerrarSesion(sesionId: String) = "$SESIONES/$sesionId/cerrar"

    // Cliente (públicos, con X-Dispositivo-Id)
    fun establecimiento(codigoQr: String) = "$API_PREFIJO/establecimientos/$codigoQr"
    fun busqueda(codigoQr: String) = "${establecimiento(codigoQr)}/busqueda"
    fun sugerencias(codigoQr: String) = "${establecimiento(codigoQr)}/sugerencias"

    fun cola(sesionId: String) = "$API_PREFIJO/sesiones/$sesionId/cola"
    fun miActividad(sesionId: String) = "$API_PREFIJO/sesiones/$sesionId/mi-actividad"
    fun solicitudes(sesionId: String) = "$API_PREFIJO/sesiones/$sesionId/solicitudes"
    fun votos(solicitudId: String) = "$API_PREFIJO/solicitudes/$solicitudId/votos"


    /** Miniatura del video servida por el backend: así la web puede dibujarla sin depender del CORS de YouTube. */
    fun miniatura(videoId: String) = "$API_PREFIJO/miniaturas/$videoId"
}