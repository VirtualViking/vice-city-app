package vicecity.web.pantalla

/**
 * Puente hacia `puente-youtube.js` (cargado en index.html): el reproductor IFrame de YouTube.
 * Lo mismo que se validó en la prueba de concepto, con eventos para avanzar y saltar videos fallidos.
 */
external object ViceCityReproductor {
    /** Crea el reproductor dentro del elemento [contenedorId]. [alEvento] recibe (tipo, detalle). */
    fun crear(contenedorId: String, alEvento: (String, String) -> Unit)
    /** Carga y reproduce [videoId] desde el segundo [desdeSeg]. */
    fun cargar(videoId: String, desdeSeg: Int)
    fun reproducir()
    fun pausar()
    fun detener()
    /** Estado de YouTube: 1 reproduciendo, 2 pausado; -2 si aún no está listo. */
    fun estado(): Int
}