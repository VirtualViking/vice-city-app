package vicecity.servidor.integraciones

/** Video que devuelve el catálogo, ya validado como música reproducible en el establecimiento. */
data class VideoCatalogo(
    val videoId: String,
    val titulo: String,
    val canal: String,
    val miniaturaUrl: String,
    val duracionSeg: Int,
    val categorias: List<String> = emptyList(),   // géneros aproximados (temas de YouTube), para la analítica
)

/** El catálogo no respondió, se agotó su cupo o no está configurado. */
class CatalogoNoDisponible(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)

/**
 * Fuente de videos musicales. Los servicios dependen de esta interfaz y no de YouTube:
 * las pruebas usan un catálogo simulado y no gastan cupo.
 */
interface CatalogoVideos {
    /**
     * Videos musicales, incrustables y disponibles en la región para [termino], en orden de relevancia.
     * Lanza [CatalogoNoDisponible] si no puede responder.
     */
    suspend fun buscar(termino: String, maxResultados: Int): List<VideoCatalogo>
}

/** Se usa cuando falta YOUTUBE_API_KEY: la búsqueda responde con las canciones ya guardadas o con 503. */
class CatalogoSinConfigurar : CatalogoVideos {
    override suspend fun buscar(termino: String, maxResultados: Int): List<VideoCatalogo> =
        throw CatalogoNoDisponible("YOUTUBE_API_KEY no está definida")
}