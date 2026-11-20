package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.ItemListaDefecto
import java.util.UUID

/** Tabla LISTA_DEFECTO_ITEM: lo que suena cuando la cola está vacía (RF-12, RF-18). Se usa dentro de una transacción. */
object RepositorioListaDefecto {

    /** Canciones de la lista con sus datos, en orden de posición. */
    fun canciones(establecimientoId: UUID): List<ResultRow> =
        ListaDefectoItems.join(Canciones, JoinType.INNER, ListaDefectoItems.videoId, Canciones.videoId)
            .selectAll()
            .where { ListaDefectoItems.establecimientoId eq establecimientoId }
            .orderBy(ListaDefectoItems.posicion, SortOrder.ASC)
            .toList()

    fun cantidad(establecimientoId: UUID): Int =
        ListaDefectoItems.selectAll().where { ListaDefectoItems.establecimientoId eq establecimientoId }.count().toInt()

    /** Reemplaza la lista completa: [videoIds] en el orden en que deben sonar (posiciones 1, 2, 3…). */
    fun reemplazar(establecimientoId: UUID, videoIds: List<String>) {
        ListaDefectoItems.deleteWhere { ListaDefectoItems.establecimientoId eq establecimientoId }
        ListaDefectoItems.batchInsert(videoIds.withIndex()) { (i, videoId) ->
            this[ListaDefectoItems.establecimientoId] = establecimientoId
            this[ListaDefectoItems.posicion] = i + 1
            this[ListaDefectoItems.videoId] = videoId
        }
    }

    fun aItem(fila: ResultRow) = ItemListaDefecto(
        posicion = fila[ListaDefectoItems.posicion],
        videoId = fila[Canciones.videoId],
        titulo = fila[Canciones.titulo],
        artista = fila[Canciones.artista] ?: fila[Canciones.canal],
        miniaturaUrl = fila[Canciones.miniaturaUrl] ?: "https://i.ytimg.com/vi/${fila[Canciones.videoId]}/mqdefault.jpg",
        duracionSeg = fila[Canciones.duracionSeg],
    )
}