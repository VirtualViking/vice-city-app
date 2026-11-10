package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.ElementoActual
import vicecity.compartido.EstadoSolicitud
import vicecity.compartido.ItemCola
import vicecity.compartido.Origen
import java.time.Instant
import java.util.UUID

/** Tablas SOLICITUD y VOTO: la cola de cada sesión y los votos de los dispositivos. Se usa dentro de una transacción. */
object RepositorioCola {
    private val ACTIVAS = listOf(EstadoSolicitud.EN_COLA.name, EstadoSolicitud.REPRODUCIENDO.name)

    fun solicitud(id: UUID): ResultRow? =
        Solicitudes.selectAll().where { Solicitudes.id eq id }.singleOrNull()

    /** La solicitud EN_COLA o REPRODUCIENDO de ese video en la sesión (el índice ux_solicitud_activa impide que haya dos). */
    fun activaDeVideo(sesionId: UUID, videoId: String): ResultRow? =
        Solicitudes.selectAll()
            .where { (Solicitudes.sesionId eq sesionId) and (Solicitudes.videoId eq videoId) and (Solicitudes.estado inList ACTIVAS) }
            .singleOrNull()

    /** Cuándo terminó de sonar por última vez ese video en la sesión, o null si no ha sonado. */
    fun ultimaReproduccion(sesionId: UUID, videoId: String): Instant? {
        val fin = Solicitudes.finReproduccion.max()
        return Solicitudes.select(fin)
            .where {
                (Solicitudes.sesionId eq sesionId) and (Solicitudes.videoId eq videoId) and
                        (Solicitudes.estado eq EstadoSolicitud.REPRODUCIDA.name)
            }
            .single()[fin]
    }

    /** Canciones EN_COLA con sus votos: más votos primero y, con empate, la que se pidió antes. */
    fun cola(sesionId: UUID): List<ItemCola> = itemsDonde(Solicitudes.sesionId eq sesionId, enCola = true)

    /**
     * La solicitud que suena ahora, o null. Las pone a sonar el reproductor del DJ (módulo siguiente);
     * lo que suena de la lista por defecto se agregará con ese módulo.
     */
    fun actual(sesionId: UUID): ElementoActual? =
        Solicitudes.join(Canciones, JoinType.INNER, Solicitudes.videoId, Canciones.videoId)
            .selectAll()
            .where { (Solicitudes.sesionId eq sesionId) and (Solicitudes.estado eq EstadoSolicitud.REPRODUCIENDO.name) }
            .singleOrNull()
            ?.let { fila ->
                val videoId = fila[Solicitudes.videoId]
                ElementoActual(
                    origen = Origen.SOLICITUD,
                    solicitudId = fila[Solicitudes.id].toString(),
                    videoId = videoId,
                    titulo = fila[Canciones.titulo],
                    artista = fila[Canciones.artista] ?: fila[Canciones.canal],
                    canal = fila[Canciones.canal],
                    miniaturaUrl = fila[Canciones.miniaturaUrl] ?: miniaturaPorDefecto(videoId),
                    duracionSeg = fila[Canciones.duracionSeg],
                    inicioEn = (fila[Solicitudes.inicioReproduccion] ?: fila[Solicitudes.solicitadaEn]).toString(),
                )
            }

    fun item(solicitudId: UUID): ItemCola = itemsDonde(Solicitudes.id eq solicitudId, enCola = false).single()

    fun votosDe(solicitudId: UUID): Int =
        Votos.selectAll().where { Votos.solicitudId eq solicitudId }.count().toInt()

    /** Solicitudes del dispositivo en la sesión, las más recientes primero. */
    fun solicitudesDe(sesionId: UUID, dispositivoHash: String): List<ResultRow> =
        Solicitudes.selectAll()
            .where { (Solicitudes.sesionId eq sesionId) and (Solicitudes.dispositivoHash eq dispositivoHash) }
            .orderBy(Solicitudes.solicitadaEn, SortOrder.DESC)
            .toList()

    /** Cuándo pidió el dispositivo cada canción desde [desde], la más antigua primero (para el límite por ventana). */
    fun solicitadasDesde(sesionId: UUID, dispositivoHash: String, desde: Instant): List<Instant> =
        Solicitudes.select(Solicitudes.solicitadaEn)
            .where {
                (Solicitudes.sesionId eq sesionId) and (Solicitudes.dispositivoHash eq dispositivoHash) and
                        (Solicitudes.solicitadaEn greater desde)
            }
            .orderBy(Solicitudes.solicitadaEn, SortOrder.ASC)
            .map { it[Solicitudes.solicitadaEn] }

    /** Solicitudes de la sesión que el dispositivo votó, incluidas las suyas. */
    fun votadasPor(sesionId: UUID, dispositivoHash: String): List<UUID> =
        Votos.join(Solicitudes, JoinType.INNER, Votos.solicitudId, Solicitudes.id)
            .select(Votos.solicitudId)
            .where { (Solicitudes.sesionId eq sesionId) and (Votos.dispositivoHash eq dispositivoHash) }
            .map { it[Votos.solicitudId] }

    /** Votos del dispositivo en la sesión que cuentan para su límite: los que dio a canciones que pidieron otros. */
    fun votosContados(sesionId: UUID, dispositivoHash: String): Int =
        Votos.join(Solicitudes, JoinType.INNER, Votos.solicitudId, Solicitudes.id)
            .selectAll()
            .where {
                (Solicitudes.sesionId eq sesionId) and (Votos.dispositivoHash eq dispositivoHash) and
                        (Solicitudes.dispositivoHash neq dispositivoHash)
            }
            .count().toInt()

    fun yaVoto(solicitudId: UUID, dispositivoHash: String): Boolean =
        Votos.selectAll().where { (Votos.solicitudId eq solicitudId) and (Votos.dispositivoHash eq dispositivoHash) }.count() > 0

    /**
     * Crea la solicitud EN_COLA. Devuelve false si otro dispositivo acaba de poner ese video en la cola
     * (el índice ux_solicitud_activa rechaza la segunda y ON CONFLICT DO NOTHING evita el error).
     */
    fun crearSolicitud(id: UUID, sesionId: UUID, videoId: String, dispositivoHash: String, ahora: Instant): Boolean =
        Solicitudes.insertIgnore {
            it[Solicitudes.id] = id
            it[Solicitudes.sesionId] = sesionId
            it[Solicitudes.videoId] = videoId
            it[Solicitudes.dispositivoHash] = dispositivoHash
            it[estado] = EstadoSolicitud.EN_COLA.name
            it[solicitadaEn] = ahora
        }.insertedCount > 0

    /** Registra el voto. Devuelve false si el dispositivo ya había votado esa solicitud (UNIQUE de VOTO). */
    fun crearVoto(solicitudId: UUID, dispositivoHash: String, ahora: Instant): Boolean =
        Votos.insertIgnore {
            it[id] = UUID.randomUUID()
            it[Votos.solicitudId] = solicitudId
            it[Votos.dispositivoHash] = dispositivoHash
            it[creadoEn] = ahora
        }.insertedCount > 0

    private fun itemsDonde(condicion: Op<Boolean>, enCola: Boolean): List<ItemCola> {
        val votos = Votos.id.count()
        return Solicitudes
            .join(Canciones, JoinType.INNER, Solicitudes.videoId, Canciones.videoId)
            .join(Votos, JoinType.LEFT, Solicitudes.id, Votos.solicitudId)
            .select(
                Solicitudes.id, Solicitudes.videoId, Solicitudes.solicitadaEn, votos,
                Canciones.titulo, Canciones.artista, Canciones.canal, Canciones.miniaturaUrl, Canciones.duracionSeg,
            )
            .where { if (enCola) condicion and (Solicitudes.estado eq EstadoSolicitud.EN_COLA.name) else condicion }
            .groupBy(Solicitudes.id, Canciones.videoId)
            .orderBy(votos to SortOrder.DESC, Solicitudes.solicitadaEn to SortOrder.ASC, Solicitudes.id to SortOrder.ASC)
            .map { fila ->
                val videoId = fila[Solicitudes.videoId]
                ItemCola(
                    solicitudId = fila[Solicitudes.id].toString(),
                    videoId = videoId,
                    titulo = fila[Canciones.titulo],
                    artista = fila[Canciones.artista] ?: fila[Canciones.canal],
                    canal = fila[Canciones.canal],
                    miniaturaUrl = fila[Canciones.miniaturaUrl] ?: miniaturaPorDefecto(videoId),
                    duracionSeg = fila[Canciones.duracionSeg],
                    votos = fila[votos].toInt(),
                    solicitadaEn = fila[Solicitudes.solicitadaEn].toString(),
                )
            }
    }

    private fun miniaturaPorDefecto(videoId: String) = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
}