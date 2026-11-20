package vicecity.compartido

import kotlinx.serialization.Serializable

/**
 * Respuesta de GET /api/v1/admin/reproductor y de las acciones del reproductor: lo que ven el panel del DJ y la vista pública.
 * [sesion] es null si no hay una sesión abierta. [actual] es null si no suena nada: el reproductor está detenido
 * o no hay solicitudes ni lista por defecto. [cola] viene ordenada como la ve el público.
 */
@Serializable
data class EstadoReproduccion(
    val establecimiento: String,
    val sesion: SesionDto? = null,
    val actual: ElementoActual? = null,
    val cola: List<ItemCola> = emptyList(),
    val cancionesListaDefecto: Int = 0,
    val actualizadoEn: String,
)

/**
 * Cuerpo de POST /api/v1/admin/reproductor/terminada: la vista pública avisa que [videoId], que empezó en [inicioEn], terminó.
 * Si ya no es lo que suena (otra pantalla avisó antes, o fue un reintento), el servidor no salta otra canción.
 */
@Serializable
data class FinReproduccion(
    val videoId: String,
    val inicioEn: String,
)

/** Cuerpo de POST /api/v1/admin/reproductor/fallida (RF-25). [codigo] es el error del reproductor de YouTube (100, 101, 150...). */
@Serializable
data class ReproduccionFallida(
    val videoId: String,
    val inicioEn: String,
    val codigo: Int,
)

@Serializable
enum class MotivoEliminacion { NO_MUSICAL, INAPROPIADO, OTRO }

/**
 * Cuerpo de POST /api/v1/admin/solicitudes/{id}/eliminar (HU-14).
 * Con [bloquear], la canción tampoco se puede pedir ni aparece en las búsquedas durante 24 horas.
 */
@Serializable
data class EliminacionSolicitud(
    val motivo: MotivoEliminacion,
    val bloquear: Boolean = false,
)

/** Canción de la lista por defecto (RF-18). [posicion] empieza en 1. */
@Serializable
data class ItemListaDefecto(
    val posicion: Int,
    val videoId: String,
    val titulo: String,
    val artista: String,
    val miniaturaUrl: String,
    val duracionSeg: Int,
)

/** Respuesta de GET y PUT /api/v1/admin/lista-defecto */
@Serializable
data class ListaDefecto(
    val canciones: List<ItemListaDefecto>,
)

/** Cuerpo de PUT /api/v1/admin/lista-defecto: la lista completa, en el orden en que debe sonar. */
@Serializable
data class NuevaListaDefecto(
    val videoIds: List<String>,
)

/** Canción bloqueada en el establecimiento hasta [hasta] (ISO 8601, UTC). */
@Serializable
data class Bloqueo(
    val videoId: String,
    val titulo: String,
    val artista: String,
    val miniaturaUrl: String,
    val motivo: MotivoEliminacion,
    val creadoEn: String,
    val hasta: String,
)

/** Respuesta de GET /api/v1/admin/bloqueos: los bloqueos vigentes, el más reciente primero. */
@Serializable
data class ListaBloqueos(
    val bloqueos: List<Bloqueo>,
)