package vicecity.compartido

/** Prefijo común de todos los endpoints REST (contrato de la API). */
const val API_PREFIJO = "/api/v1"

/** Header con el UUID anónimo que la PWA genera y guarda en el navegador (contrato, "Identificación del cliente"). */
const val HEADER_DISPOSITIVO = "X-Dispositivo-Id"

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
}