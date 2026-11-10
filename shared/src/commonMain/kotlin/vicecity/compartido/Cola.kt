package vicecity.compartido

import kotlinx.serialization.Serializable

@Serializable
enum class EstadoSolicitud { EN_COLA, REPRODUCIENDO, REPRODUCIDA, ELIMINADA, FALLIDA, NO_REPRODUCIDA }

/** De dónde viene lo que suena: una solicitud de la cola o la lista por defecto del establecimiento. */
@Serializable
enum class Origen { SOLICITUD, LISTA_DEFECTO }

/** Canción en la cola, con los votos de todos los dispositivos (RF-07). Fechas en ISO 8601 (UTC). */
@Serializable
data class ItemCola(
    val solicitudId: String,
    val videoId: String,
    val titulo: String,
    val artista: String,
    val canal: String,
    val miniaturaUrl: String,
    val duracionSeg: Int,
    val votos: Int,
    val solicitadaEn: String,
)

/** Lo que suena ahora. [solicitudId] es null si viene de la lista por defecto. */
@Serializable
data class ElementoActual(
    val origen: Origen,
    val solicitudId: String? = null,
    val videoId: String,
    val titulo: String,
    val artista: String,
    val canal: String,
    val miniaturaUrl: String,
    val duracionSeg: Int,
    val inicioEn: String,
)

/** Respuesta de GET /api/v1/sesiones/{sesionId}/cola. [cola] ya viene ordenada: más votos primero y, con empate, la más antigua. */
@Serializable
data class EstadoCola(
    val sesionId: String,
    val modo: Modo,
    val estadoReproductor: EstadoReproductor,
    val actual: ElementoActual? = null,
    val cola: List<ItemCola>,
    val actualizadoEn: String,
)

/** Solicitud vista por el dispositivo que la hizo. [posicion] solo existe mientras está EN_COLA (1 = la siguiente). */
@Serializable
data class MiSolicitud(
    val solicitudId: String,
    val videoId: String,
    val titulo: String,
    val estado: EstadoSolicitud,
    val posicion: Int? = null,
    val solicitadaEn: String,
)

/**
 * Respuesta de GET /api/v1/sesiones/{sesionId}/mi-actividad: lo que este dispositivo hizo en la sesión y lo que aún puede hacer.
 * - [votos]: solicitudes que ya votó, incluidas las suyas (al pedir una canción su voto cuenta solo).
 * - [solicitudesRestantes]: cuántas canciones puede pedir todavía en la ventana de [ventanaLimiteMin] minutos.
 * - [proximaSolicitudEn]: si ya no le quedan, cuándo podrá pedir otra.
 * - [votosRestantes]: votos que le quedan en la sesión para canciones que pidieron otros (máximo [limiteVotos]).
 */
@Serializable
data class MiActividad(
    val solicitudes: List<MiSolicitud>,
    val votos: List<String>,
    val solicitudesRestantes: Int,
    val limiteSolicitudes: Int,
    val ventanaLimiteMin: Int,
    val proximaSolicitudEn: String? = null,
    val votosRestantes: Int,
    val limiteVotos: Int,
)

/** Cuerpo de POST /api/v1/sesiones/{sesionId}/solicitudes */
@Serializable
data class NuevaSolicitud(
    val videoId: String,
)

/** VOTO_REGISTRADO: la canción ya estaba en la cola y la solicitud se convirtió en un voto para ella. */
@Serializable
enum class ResultadoSolicitud { SOLICITUD_CREADA, VOTO_REGISTRADO }

/** Respuesta de POST /api/v1/sesiones/{sesionId}/solicitudes */
@Serializable
data class RespuestaSolicitud(
    val resultado: ResultadoSolicitud,
    val solicitud: ItemCola,
)

/** Respuesta de POST /api/v1/solicitudes/{solicitudId}/votos */
@Serializable
data class RespuestaVoto(
    val solicitudId: String,
    val votos: Int,
)