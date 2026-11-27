package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.ReproduccionFallida
import java.time.Instant

/**
 * Tabla VIDEO_NO_REPRODUCIBLE (migración V6): videos que YouTube no deja reproducir en la app.
 * Se usa dentro de una transacción.
 */
object RepositorioNoReproducibles {

    /**
     * Fallas que no cambian al reintentar: los códigos de YouTube 100 (el video no existe o es privado),
     * 101 y 150 (el dueño no permite verlo incrustado), y [ReproduccionFallida.SIN_ARRANQUE] (bloqueado sin aviso de YouTube).
     * Los demás (por ejemplo 5, una falla del navegador) pueden ser pasajeros y no se recuerdan.
     */
    val CODIGOS_PERMANENTES = setOf(100, 101, 150, ReproduccionFallida.SIN_ARRANQUE)

    /** Registra el video. Si ya estaba registrado, se conserva la primera detección. */
    fun registrar(videoId: String, codigo: Int, ahora: Instant) {
        VideosNoReproducibles.insertIgnore {
            it[VideosNoReproducibles.videoId] = videoId
            it[codigoError] = codigo.toShort()
            it[detectadoEn] = ahora
        }
    }

    fun contiene(videoId: String): Boolean =
        !VideosNoReproducibles.selectAll().where { VideosNoReproducibles.videoId eq videoId }.empty()

    /** Cuáles de [videoIds] no se pueden reproducir. */
    fun de(videoIds: Collection<String>): Set<String> =
        if (videoIds.isEmpty()) emptySet()
        else VideosNoReproducibles.select(VideosNoReproducibles.videoId)
            .where { VideosNoReproducibles.videoId inList videoIds }
            .map { it[VideosNoReproducibles.videoId] }
            .toSet()
}