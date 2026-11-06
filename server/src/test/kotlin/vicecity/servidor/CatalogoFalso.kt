package vicecity.servidor

import vicecity.servidor.integraciones.CatalogoNoDisponible
import vicecity.servidor.integraciones.CatalogoVideos
import vicecity.servidor.integraciones.VideoCatalogo

/** Catálogo simulado para las pruebas: no llama a YouTube y cuenta cuántas veces lo consultaron. */
class CatalogoFalso : CatalogoVideos {
    var consultas = 0
        private set
    var disponible = true
    private val respuestas = mutableMapOf<String, List<VideoCatalogo>>()

    /** Lo que responde para [termino], comparado sin mayúsculas. */
    fun responder(termino: String, vararg videos: VideoCatalogo) {
        respuestas[termino.lowercase()] = videos.toList()
    }

    override suspend fun buscar(termino: String, maxResultados: Int): List<VideoCatalogo> {
        if (!disponible) throw CatalogoNoDisponible("cuota agotada (simulado)")
        consultas++
        return respuestas[termino.lowercase()].orEmpty().take(maxResultados)
    }

    companion object {
        fun video(id: String, titulo: String, canal: String, duracionSeg: Int = 210, categorias: List<String> = emptyList()) =
            VideoCatalogo(id, titulo, canal, "https://i.ytimg.com/vi/$id/mqdefault.jpg", duracionSeg, categorias)
    }
}