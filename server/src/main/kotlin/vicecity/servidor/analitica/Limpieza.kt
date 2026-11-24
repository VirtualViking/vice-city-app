package vicecity.servidor.analitica

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import vicecity.servidor.servicios.TipoEvento
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Fila de la tabla evento tal como se guardó (capa cruda). */
data class EventoCrudo(
    val id: Long,
    val tipo: String,
    val establecimientoId: UUID,
    val sesionId: UUID?,
    val solicitudId: UUID?,
    val videoId: String?,
    val dispositivoHash: String?,
    val ocurridoEn: Instant,
    val datos: JsonObject,
) {
    fun texto(clave: String): String? = (datos[clave] as? JsonPrimitive)?.contentOrNull
    fun entero(clave: String): Int? = (datos[clave] as? JsonPrimitive)?.intOrNull
    fun booleano(clave: String): Boolean? = (datos[clave] as? JsonPrimitive)?.booleanOrNull
}

/** Lo que el pipeline necesita de la sesión operativa. */
data class SesionCruda(
    val id: UUID,
    val establecimientoId: UUID,
    val inicio: Instant,
    val fin: Instant?,
    val cerrada: Boolean,
    val automatica: Boolean,
)

/** Evento que pasó la limpieza, con su tipo ya reconocido. */
data class EventoLimpio(val evento: EventoCrudo, val tipo: TipoEvento)

data class ResultadoLimpieza(
    val validos: List<EventoLimpio>,
    val rechazados: List<Pair<EventoCrudo, MotivoRechazo>>,
)

/** Por qué un evento no pasa la limpieza. Queda en analitica.evento_rechazado. */
enum class MotivoRechazo {
    TIPO_DESCONOCIDO,
    ESTABLECIMIENTO_DISTINTO,
    FECHA_FUTURA,
    FUERA_DE_SESION,
    FALTA_DISPOSITIVO,
    FALTA_SOLICITUD,
    FALTA_VIDEO,
    FALTA_ORIGEN,
    SOLICITUD_DESCONOCIDA,
    CANCION_DESCONOCIDA,
    REPRODUCCION_DESCONOCIDA,
    DUPLICADO,
}

/**
 * Etapa de limpieza del pipeline: valida cada evento de una sesión y aparta los que no sirven para los indicadores.
 * Las reglas son de tres tipos:
 * - Formato: tipo conocido, establecimiento de la sesión, fecha dentro de la sesión y no futura, campos obligatorios del tipo.
 * - Coherencia: un voto, una eliminación o un inicio de reproducción deben referirse a una solicitud creada antes en la sesión,
 *   y un fin de reproducción debe corresponder a lo que estaba sonando.
 * - Duplicados: la misma solicitud creada dos veces, dos accesos o dos votos del mismo dispositivo, dos inicios de la misma solicitud.
 * Los eventos llegan ordenados por fecha y, con la misma fecha, por id (orden de inserción).
 */
object Limpieza {
    /** Margen para relojes y transacciones que terminan unos instantes después. */
    private val TOLERANCIA: Duration = Duration.ofMinutes(1)
    private val TIPOS = TipoEvento.entries.associateBy { it.name }

    fun limpiar(eventos: List<EventoCrudo>, sesion: SesionCruda, cancionesConocidas: Set<String>, ahora: Instant): ResultadoLimpieza {
        val validos = mutableListOf<EventoLimpio>()
        val rechazados = mutableListOf<Pair<EventoCrudo, MotivoRechazo>>()
        val creadas = mutableSetOf<UUID>()
        val iniciadas = mutableSetOf<UUID>()
        val accesos = mutableSetOf<String>()
        val votos = mutableSetOf<Pair<UUID, String>>()
        var sonando: Pair<String, UUID?>? = null   // video y solicitud de lo que suena

        for (e in eventos) {
            val tipo = TIPOS[e.tipo]
            val motivo = formato(e, tipo, sesion, ahora) ?: when (tipo!!) {
                TipoEvento.ACCESO_SESION -> if (!accesos.add(e.dispositivoHash!!)) MotivoRechazo.DUPLICADO else null
                TipoEvento.SOLICITUD_CREADA -> when {
                    e.videoId !in cancionesConocidas -> MotivoRechazo.CANCION_DESCONOCIDA
                    !creadas.add(e.solicitudId!!) -> MotivoRechazo.DUPLICADO
                    else -> null
                }
                TipoEvento.VOTO_REGISTRADO -> when {
                    e.solicitudId !in creadas -> MotivoRechazo.SOLICITUD_DESCONOCIDA
                    !votos.add(e.solicitudId!! to e.dispositivoHash!!) -> MotivoRechazo.DUPLICADO
                    else -> null
                }
                TipoEvento.REPRODUCCION_INICIADA -> when {
                    e.videoId !in cancionesConocidas -> MotivoRechazo.CANCION_DESCONOCIDA
                    e.texto("origen") == ORIGEN_COLA && e.solicitudId !in creadas -> MotivoRechazo.SOLICITUD_DESCONOCIDA
                    e.texto("origen") == ORIGEN_COLA && !iniciadas.add(e.solicitudId!!) -> MotivoRechazo.DUPLICADO
                    else -> null.also { sonando = e.videoId!! to e.solicitudId }
                }
                TipoEvento.REPRODUCCION_FINALIZADA, TipoEvento.REPRODUCCION_FALLIDA ->
                    if (sonando?.first != e.videoId) MotivoRechazo.REPRODUCCION_DESCONOCIDA else null.also { sonando = null }
                TipoEvento.SOLICITUD_ELIMINADA, TipoEvento.SOLICITUD_NO_REPRODUCIDA ->
                    if (e.solicitudId !in creadas) MotivoRechazo.SOLICITUD_DESCONOCIDA
                    else null.also { if (sonando?.second == e.solicitudId) sonando = null }
                TipoEvento.SESION_CERRADA -> null.also { sonando = null }
                else -> null
            }
            if (motivo == null) validos += EventoLimpio(e, tipo!!) else rechazados += e to motivo
        }
        return ResultadoLimpieza(validos, rechazados)
    }

    /** Reglas que no dependen de los eventos anteriores. */
    private fun formato(e: EventoCrudo, tipo: TipoEvento?, sesion: SesionCruda, ahora: Instant): MotivoRechazo? = when {
        tipo == null -> MotivoRechazo.TIPO_DESCONOCIDO
        e.establecimientoId != sesion.establecimientoId -> MotivoRechazo.ESTABLECIMIENTO_DISTINTO
        e.ocurridoEn > ahora + TOLERANCIA -> MotivoRechazo.FECHA_FUTURA
        e.ocurridoEn < sesion.inicio - TOLERANCIA -> MotivoRechazo.FUERA_DE_SESION
        sesion.fin != null && e.ocurridoEn > sesion.fin + TOLERANCIA -> MotivoRechazo.FUERA_DE_SESION
        else -> camposFaltantes(e, tipo)
    }

    private fun camposFaltantes(e: EventoCrudo, tipo: TipoEvento): MotivoRechazo? {
        val necesita = when (tipo) {
            TipoEvento.ACCESO_SESION -> listOf(DISPOSITIVO)
            TipoEvento.SOLICITUD_CREADA -> listOf(SOLICITUD, VIDEO, DISPOSITIVO)
            TipoEvento.VOTO_REGISTRADO -> listOf(SOLICITUD, DISPOSITIVO)
            TipoEvento.REPRODUCCION_INICIADA -> listOf(VIDEO, ORIGEN)
            TipoEvento.REPRODUCCION_FINALIZADA, TipoEvento.REPRODUCCION_FALLIDA -> listOf(VIDEO)
            TipoEvento.SOLICITUD_ELIMINADA, TipoEvento.SOLICITUD_NO_REPRODUCIDA -> listOf(SOLICITUD)
            else -> emptyList()
        }
        return when {
            DISPOSITIVO in necesita && e.dispositivoHash.isNullOrBlank() -> MotivoRechazo.FALTA_DISPOSITIVO
            SOLICITUD in necesita && e.solicitudId == null -> MotivoRechazo.FALTA_SOLICITUD
            VIDEO in necesita && e.videoId.isNullOrBlank() -> MotivoRechazo.FALTA_VIDEO
            ORIGEN in necesita && e.texto("origen") !in setOf(ORIGEN_COLA, ORIGEN_LISTA) -> MotivoRechazo.FALTA_ORIGEN
            ORIGEN in necesita && e.texto("origen") == ORIGEN_COLA && e.solicitudId == null -> MotivoRechazo.FALTA_SOLICITUD
            else -> null
        }
    }

    private const val DISPOSITIVO = "dispositivo"
    private const val SOLICITUD = "solicitud"
    private const val VIDEO = "video"
    private const val ORIGEN = "origen"
    const val ORIGEN_COLA = "COLA"
    const val ORIGEN_LISTA = "LISTA_DEFECTO"
}

/**
 * Normalización de la dimensión canción: artista sin los sufijos que YouTube agrega a los canales
 * y géneros (temas de Wikipedia que entrega YouTube) traducidos a nombres en español.
 */
object Normalizacion {
    private val SUFIJOS_CANAL = listOf(" - Topic", " - Tema", "VEVO", " Official", " Oficial")

    fun artista(artista: String?, canal: String): String {
        var limpio = (artista?.takeIf { it.isNotBlank() } ?: canal).trim()
        SUFIJOS_CANAL.forEach { sufijo -> if (limpio.endsWith(sufijo, ignoreCase = true)) limpio = limpio.dropLast(sufijo.length).trim() }
        return limpio.ifBlank { canal.trim() }
    }

    fun genero(tema: String): String = GENEROS[tema.trim().lowercase()] ?: tema.trim().removeSuffix(" music").replaceFirstChar { it.uppercase() }

    /** Temas más comunes de la API de YouTube. Los que no están se dejan con la primera letra en mayúscula y sin " music". */
    private val GENEROS = mapOf(
        "pop music" to "Pop",
        "latin music" to "Música latina",
        "music of latin america" to "Música latina",
        "reggaeton" to "Reguetón",
        "hip hop music" to "Hip hop",
        "electronic music" to "Electrónica",
        "rock music" to "Rock",
        "independent music" to "Independiente",
        "rhythm and blues" to "R&B",
        "soul music" to "Soul",
        "jazz" to "Jazz",
        "classical music" to "Clásica",
        "country music" to "Country",
        "reggae" to "Reggae",
        "christian music" to "Cristiana",
        "vocal music" to "Vocal",
        "salsa music" to "Salsa",
        "cumbia" to "Cumbia",
        "vallenato" to "Vallenato",
        "bachata" to "Bachata",
        "merengue music" to "Merengue",
        "music of asia" to "Música asiática",
        "soundtrack" to "Banda sonora",
    )
}