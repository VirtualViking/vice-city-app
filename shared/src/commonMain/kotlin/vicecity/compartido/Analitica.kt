package vicecity.compartido

import kotlinx.serialization.Serializable

/*
 * Capa analítica (RF-21 a RF-23, RF-29, RF-30 y Entregable 6).
 * Todos los datos salen del esquema "analitica", que el pipeline llena con datos limpios y anónimos:
 * no hay identificadores de dispositivo ni de solicitud, solo códigos de participante que cambian en cada sesión.
 * Las fechas son ISO 8601 (UTC) y las horas del día, en hora de Colombia.
 */

/** Origen de lo que sonó: una solicitud del público o la lista por defecto. */
@Serializable
enum class OrigenReproduccion { COLA, LISTA_DEFECTO }

/** Cómo terminó una reproducción. CIERRE: seguía sonando cuando se cerró la sesión (fin inferido por el pipeline). */
@Serializable
enum class ResultadoReproduccion { TERMINADA, SALTADA, FALLIDA, ELIMINADA, CIERRE, SONANDO }

/**
 * Respuesta de GET /api/v1/admin/analitica?desde=yyyy-MM-dd&hasta=yyyy-MM-dd&sesion={id}.
 * [desde] y [hasta] son fechas de Colombia, ambas incluidas. Con [sesionId], los indicadores son solo de esa sesión.
 */
@Serializable
data class TableroAnalitico(
    val desde: String,
    val hasta: String,
    val sesionId: String? = null,
    val resumen: ResumenAnalitico,
    /** Canción más popular de la sesión elegida (o del periodo); null si no hubo solicitudes. */
    val masPopular: CancionPopular? = null,
    val ranking: RankingPopularidad,
    /** Solicitudes y votos por hora del día (0 a 23, hora de Colombia); siempre 24 elementos. */
    val franjas: List<FranjaHoraria>,
    val esperas: TiemposEspera,
    val listaDefecto: CumplimientoListaDefecto,
    /** Canciones que sonaron, la más reciente primero (máximo 200). */
    val historial: List<ReproduccionHistorial>,
    /** Solicitudes que seguían en cola al cerrar la sesión, ordenadas por votos (máximo 20). */
    val noReproducidas: List<SolicitudNoReproducida>,
    val concentracion: ConcentracionVotos,
    /** Hasta cuándo están procesados los datos (fin de la última ejecución del pipeline). */
    val datosHasta: String? = null,
)

/**
 * Indicadores de participación (RF-21). [usuariosUnicos] son participantes con al menos una solicitud o un voto,
 * contados por sesión: por la anonimización, quien vino dos noches cuenta dos veces.
 */
@Serializable
data class ResumenAnalitico(
    val sesiones: Int,
    val usuariosUnicos: Int,
    val visitantes: Int,
    val nuevos: Int,
    val recurrentes: Int,
    val solicitudes: Int,
    val votos: Int,
    val esperaPromedioSeg: Int? = null,
    /** Solicitudes de la cola que sonaron. */
    val reproducidas: Int,
    val noReproducidas: Int,
    val saltadas: Int,
    val fallidas: Int,
    val eliminadas: Int,
)

@Serializable
data class CancionPopular(
    val videoId: String,
    val titulo: String,
    val artista: String,
    val solicitudes: Int,
    val votos: Int,
    val reproducida: Boolean,
)

/** Una fila de un ranking: canción, artista o género. [videoId] solo en el de canciones. */
@Serializable
data class ElementoRanking(
    val nombre: String,
    val solicitudes: Int,
    val votos: Int,
    val videoId: String? = null,
)

/** Popularidad por canción, artista y género (RF-22, RF-29): máximo 10 por ranking, ordenados por votos. */
@Serializable
data class RankingPopularidad(
    val canciones: List<ElementoRanking>,
    val artistas: List<ElementoRanking>,
    val generos: List<ElementoRanking>,
)

@Serializable
data class FranjaHoraria(
    val hora: Int,
    val solicitudes: Int,
    val votos: Int,
)

/** Tramo de la distribución de esperas: desde [desdeMin] hasta antes de [hastaMin] (null: sin límite). */
@Serializable
data class TramoEspera(
    val desdeMin: Int,
    val hastaMin: Int? = null,
    val solicitudes: Int,
)

/** Tiempo desde que se pide una canción hasta que empieza a sonar (solo las que sonaron). */
@Serializable
data class TiemposEspera(
    val promedioSeg: Int? = null,
    val medianaSeg: Int? = null,
    val maximoSeg: Int? = null,
    val tramos: List<TramoEspera>,
    /** Promedio de lo que esperaron las que no sonaron, hasta el cierre. */
    val noReproducidasPromedioSeg: Int? = null,
)

/** Cumplimiento de la lista por defecto (RF-29): cuánto sonó la cola frente a la lista y cuántas veces se pasó de una a otra. */
@Serializable
data class CumplimientoListaDefecto(
    val segundosCola: Long,
    val segundosLista: Long,
    val porcentajeCola: Int,
    val porcentajeLista: Int,
    val transiciones: Int,
    val reproduccionesCola: Int,
    val reproduccionesLista: Int,
)

@Serializable
data class ReproduccionHistorial(
    val sesionId: String,
    val inicio: String,
    val fin: String? = null,
    val videoId: String,
    val titulo: String,
    val artista: String,
    val origen: OrigenReproduccion,
    val segundos: Int,
    val resultado: ResultadoReproduccion,
    /** El fin no llegó como evento y el pipeline lo dedujo (por ejemplo, del cierre de la sesión). */
    val finInferido: Boolean = false,
    val votos: Int? = null,
    val esperaSeg: Int? = null,
)

@Serializable
data class SolicitudNoReproducida(
    val videoId: String,
    val titulo: String,
    val artista: String,
    val votos: Int,
    val enColaSeg: Int,
    val sesionId: String,
    val sesionInicio: String,
)

/** Concentración de votos (RF-30): qué porcentaje de los votos dio el 10 % de participantes más activos. */
@Serializable
data class ConcentracionVotos(
    val participantesQueVotaron: Int,
    val participantesTop: Int,
    val porcentajeVotosTop: Int? = null,
)

/** Sesión para el selector del tablero. */
@Serializable
data class SesionAnalitica(
    val id: String,
    val inicio: String,
    val fin: String? = null,
    val automatica: Boolean,
    val solicitudes: Int,
    /** true si el administrador la excluyó del tablero (por ejemplo, una sesión de prueba). */
    val excluida: Boolean = false,
)

/**
 * Respuesta de GET /api/v1/admin/analitica/sesiones?desde=&hasta=: la más reciente primero.
 * [excluidas] son las del periodo que el administrador sacó del tablero: no cuentan en los indicadores.
 */
@Serializable
data class SesionesAnaliticas(
    val sesiones: List<SesionAnalitica>,
    val excluidas: List<SesionAnalitica> = emptyList(),
)

/** PUT /api/v1/admin/analitica/sesiones/{id}/exclusion: saca la sesión del tablero o la vuelve a incluir. Los datos no se borran. */
@Serializable
data class CambioExclusion(val excluida: Boolean)

/** Lo que borró la retención (sección 6 del diseño). */
@Serializable
data class ResultadoRetencion(
    val solicitudes: Int,
    val votos: Int,
    val dispositivos: Int,
    val eventos: Int,
)

/** Una ejecución del pipeline: ingesta, limpieza, anonimización y carga. Respuesta de POST /api/v1/admin/analitica/pipeline. */
@Serializable
data class EjecucionPipeline(
    val id: Long,
    /** AUTOMATICA (cada 5 minutos), MANUAL (botón del tablero) o TABLERO (al abrirlo con datos viejos). */
    val origen: String,
    val inicio: String,
    val fin: String? = null,
    val eventosLeidos: Int,
    val eventosCargados: Int,
    val eventosRechazados: Int,
    val sesionesProcesadas: Int,
    /** Sesiones cerradas que quedaron fijas y cuya clave temporal se borró (anonimización irreversible). */
    val sesionesConsolidadas: Int,
    val rechazosPorMotivo: Map<String, Int> = emptyMap(),
    val normalizaciones: Map<String, Int> = emptyMap(),
    val retencion: ResultadoRetencion? = null,
)

/** Respuesta de GET /api/v1/admin/analitica/calidad: calidad de los datos y evidencia de anonimización. */
@Serializable
data class CalidadDatos(
    /** Las 10 ejecuciones más recientes. */
    val ejecuciones: List<EjecucionPipeline>,
    val sesionesConsolidadas: Int,
    val sesionesEnCurso: Int,
    /** Claves temporales que existen: solo las de sesiones que aún no se consolidan. */
    val clavesTemporales: Int,
    /** Valores de la capa analítica que coinciden con un identificador de dispositivo o de solicitud. Debe ser 0. */
    val identificadoresEnCapaAnalitica: Int,
    val eventosRechazados: Int,
    val rechazosPorMotivo: Map<String, Int> = emptyMap(),
)