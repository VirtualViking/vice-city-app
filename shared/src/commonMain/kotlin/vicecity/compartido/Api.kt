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
    fun pausaSolicitudes(sesionId: String) = "$SESIONES/$sesionId/pausa"

    // Panel del personal y vista pública (con token)
    const val REPRODUCTOR = "$API_PREFIJO/admin/reproductor"
    const val REPRODUCTOR_INICIAR = "$REPRODUCTOR/iniciar"
    const val REPRODUCTOR_PAUSAR = "$REPRODUCTOR/pausar"
    const val REPRODUCTOR_REANUDAR = "$REPRODUCTOR/reanudar"
    const val REPRODUCTOR_SALTAR = "$REPRODUCTOR/saltar"
    const val REPRODUCTOR_TERMINADA = "$REPRODUCTOR/terminada"
    const val REPRODUCTOR_FALLIDA = "$REPRODUCTOR/fallida"
    fun eliminarSolicitud(solicitudId: String) = "$API_PREFIJO/admin/solicitudes/$solicitudId/eliminar"

    const val LISTA_DEFECTO = "$API_PREFIJO/admin/lista-defecto"
    /** Búsqueda en YouTube para armar la lista por defecto: ?q= */
    const val BUSQUEDA_PERSONAL = "$API_PREFIJO/admin/busqueda"
    const val BLOQUEOS = "$API_PREFIJO/admin/bloqueos"
    fun bloqueo(videoId: String) = "$BLOQUEOS/$videoId"

    const val OPERACION = "$API_PREFIJO/admin/operacion"
    const val MANTENIMIENTO = "$OPERACION/mantenimiento"
    const val LIMPIEZA = "$OPERACION/limpieza"

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