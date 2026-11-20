package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import vicecity.compartido.Bloqueo
import vicecity.compartido.MotivoEliminacion
import java.time.Instant
import java.util.UUID

/** Tabla BLOQUEO_VIDEO: canciones que no se pueden pedir en el establecimiento por un tiempo. Se usa dentro de una transacción. */
object RepositorioBloqueos {

    /** Hasta cuándo está bloqueada la canción, o null si no lo está. */
    fun vigente(establecimientoId: UUID, videoId: String, ahora: Instant): Instant? =
        BloqueosVideo.select(BloqueosVideo.hasta)
            .where {
                (BloqueosVideo.establecimientoId eq establecimientoId) and (BloqueosVideo.videoId eq videoId) and
                        (BloqueosVideo.hasta greater ahora)
            }
            .singleOrNull()
            ?.get(BloqueosVideo.hasta)

    /** videoId bloqueados ahora en el establecimiento. */
    fun bloqueados(establecimientoId: UUID, ahora: Instant): Set<String> =
        BloqueosVideo.select(BloqueosVideo.videoId)
            .where { (BloqueosVideo.establecimientoId eq establecimientoId) and (BloqueosVideo.hasta greater ahora) }
            .map { it[BloqueosVideo.videoId] }
            .toSet()

    /** Bloqueos vigentes con los datos de la canción, el más reciente primero. */
    fun vigentes(establecimientoId: UUID, ahora: Instant): List<ResultRow> =
        BloqueosVideo.join(Canciones, JoinType.INNER, BloqueosVideo.videoId, Canciones.videoId)
            .selectAll()
            .where { (BloqueosVideo.establecimientoId eq establecimientoId) and (BloqueosVideo.hasta greater ahora) }
            .orderBy(BloqueosVideo.creadoEn, SortOrder.DESC)
            .toList()

    /** Bloquea la canción hasta [hasta]; si ya estaba bloqueada, se renueva. */
    fun bloquear(establecimientoId: UUID, videoId: String, motivo: MotivoEliminacion, personalId: UUID, ahora: Instant, hasta: Instant) {
        BloqueosVideo.upsert {
            it[BloqueosVideo.establecimientoId] = establecimientoId
            it[BloqueosVideo.videoId] = videoId
            it[BloqueosVideo.motivo] = motivo.name
            it[BloqueosVideo.personalId] = personalId
            it[creadoEn] = ahora
            it[BloqueosVideo.hasta] = hasta
        }
    }

    /** Quita el bloqueo. Devuelve false si la canción no estaba bloqueada. */
    fun desbloquear(establecimientoId: UUID, videoId: String): Boolean =
        BloqueosVideo.deleteWhere { (BloqueosVideo.establecimientoId eq establecimientoId) and (BloqueosVideo.videoId eq videoId) } > 0

    /** Borra los bloqueos que ya vencieron y devuelve cuántos eran. */
    fun borrarVencidos(establecimientoId: UUID, ahora: Instant): Int =
        BloqueosVideo.deleteWhere { (BloqueosVideo.establecimientoId eq establecimientoId) and (BloqueosVideo.hasta lessEq ahora) }

    fun aBloqueo(fila: ResultRow) = Bloqueo(
        videoId = fila[Canciones.videoId],
        titulo = fila[Canciones.titulo],
        artista = fila[Canciones.artista] ?: fila[Canciones.canal],
        miniaturaUrl = fila[Canciones.miniaturaUrl] ?: "https://i.ytimg.com/vi/${fila[Canciones.videoId]}/mqdefault.jpg",
        motivo = MotivoEliminacion.valueOf(fila[BloqueosVideo.motivo]),
        creadoEn = fila[BloqueosVideo.creadoEn].toString(),
        hasta = fila[BloqueosVideo.hasta].toString(),
    )
}