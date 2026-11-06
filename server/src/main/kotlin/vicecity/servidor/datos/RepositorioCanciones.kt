package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import vicecity.compartido.ResultadoBusqueda
import vicecity.servidor.dominio.Artista
import vicecity.servidor.dominio.Textos
import vicecity.servidor.integraciones.VideoCatalogo
import java.time.Instant

/** Tabla CANCION: metadatos de los videos que aparecieron en búsquedas. Se usa dentro de una transacción. */
object RepositorioCanciones {

    /** Crea o actualiza cada canción, con su artista deducido y sus textos normalizados para el autocompletado. */
    fun guardar(videos: List<VideoCatalogo>, ahora: Instant) {
        videos.forEach { video ->
            val artista = Artista.deducir(video.titulo, video.canal)
            Canciones.upsert {
                it[videoId] = video.videoId
                it[titulo] = Textos.recortar(video.titulo, 200)
                it[canal] = Textos.recortar(video.canal, 200)
                it[duracionSeg] = video.duracionSeg
                it[miniaturaUrl] = Textos.recortar(video.miniaturaUrl, 300)
                it[actualizadoEn] = ahora
                it[Canciones.artista] = Textos.recortar(artista, 200)
                it[tituloNormalizado] = Textos.recortar(Textos.normalizar(video.titulo), 200)
                it[artistaNormalizado] = Textos.recortar(Textos.normalizar(artista), 200)
            }
            video.categorias.forEach { categoria ->
                CancionCategorias.insertIgnore {
                    it[videoId] = video.videoId
                    it[CancionCategorias.categoria] = Textos.recortar(categoria, 60)
                }
            }
        }
    }

    /** Canciones de [ids] en el mismo orden (el de relevancia que dio YouTube). */
    fun porIds(ids: List<String>): List<ResultRow> {
        if (ids.isEmpty()) return emptyList()
        val filas = Canciones.selectAll().where { Canciones.videoId inList ids }.associateBy { it[Canciones.videoId] }
        return ids.mapNotNull { filas[it] }
    }

    /** Búsqueda de respaldo, sin YouTube: canciones guardadas cuyo título o artista tenga una palabra que empiece por [termino]. */
    fun buscarLocal(termino: String, limite: Int): List<ResultRow> =
        Canciones.selectAll()
            .where {
                Coincidencias.algunaPalabraEmpiezaPor(Canciones.tituloNormalizado, termino) or
                        Coincidencias.algunaPalabraEmpiezaPor(Canciones.artistaNormalizado, termino)
            }
            .orderBy(Canciones.actualizadoEn, SortOrder.DESC)
            .limit(limite)
            .toList()

    fun aResultado(fila: ResultRow) = ResultadoBusqueda(
        videoId = fila[Canciones.videoId],
        titulo = fila[Canciones.titulo],
        artista = fila[Canciones.artista] ?: fila[Canciones.canal],
        canal = fila[Canciones.canal],
        miniaturaUrl = fila[Canciones.miniaturaUrl] ?: "https://i.ytimg.com/vi/${fila[Canciones.videoId]}/mqdefault.jpg",
        duracionSeg = fila[Canciones.duracionSeg],
    )
}