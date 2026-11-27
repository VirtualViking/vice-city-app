package vicecity.servidor.datos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

/*
 * Esquema "analitica" (migración V4): la capa de datos limpios y anónimos que llena el pipeline.
 * Ninguna de estas tablas guarda el hash del dispositivo ni el id de la solicitud.
 */

object AnCanciones : Table("analitica.cancion") {
    val videoId = varchar("video_id", 11)
    val titulo = varchar("titulo", 200)
    val artista = varchar("artista", 200)
    val duracionSeg = integer("duracion_seg")
    val actualizadaEn = timestamp("actualizada_en")
    override val primaryKey = PrimaryKey(videoId)
}

object AnCancionGeneros : Table("analitica.cancion_genero") {
    val videoId = varchar("video_id", 11)
    val genero = varchar("genero", 60)
    override val primaryKey = PrimaryKey(videoId, genero)
}

object AnSesiones : Table("analitica.sesion") {
    val sesionId = javaUUID("sesion_id")
    val establecimientoId = javaUUID("establecimiento_id")
    val inicio = timestamp("inicio")
    val fin = timestamp("fin").nullable()
    val fechaLocal = date("fecha_local")
    val automatica = bool("automatica")
    val duracionSeg = integer("duracion_seg").nullable()
    val transiciones = integer("transiciones")
    val consolidada = bool("consolidada")
    val procesadaEn = timestamp("procesada_en")
    override val primaryKey = PrimaryKey(sesionId)
}

object AnParticipaciones : Table("analitica.participacion") {
    val sesionId = javaUUID("sesion_id")
    val participante = varchar("participante", 16)
    val accedio = bool("accedio")
    val nuevo = bool("nuevo")
    val solicitudes = integer("solicitudes")
    val votos = integer("votos")
    override val primaryKey = PrimaryKey(sesionId, participante)
}

object AnSolicitudes : Table("analitica.solicitud") {
    val sesionId = javaUUID("sesion_id")
    val solicitudRef = varchar("solicitud_ref", 16)
    val participante = varchar("participante", 16)
    val videoId = varchar("video_id", 11)
    val solicitadaEn = timestamp("solicitada_en")
    val horaLocal = short("hora_local")
    val estadoFinal = varchar("estado_final", 15)
    val votos = integer("votos")
    val esperaSeg = integer("espera_seg").nullable()
    val enColaSeg = integer("en_cola_seg").nullable()
    override val primaryKey = PrimaryKey(sesionId, solicitudRef)
}

object AnVotos : Table("analitica.voto") {
    val sesionId = javaUUID("sesion_id")
    val solicitudRef = varchar("solicitud_ref", 16)
    val participante = varchar("participante", 16)
    val origen = varchar("origen", 20)
    val ocurridoEn = timestamp("ocurrido_en")
    val horaLocal = short("hora_local")
    override val primaryKey = PrimaryKey(sesionId, solicitudRef, participante)
}

object AnReproducciones : Table("analitica.reproduccion") {
    val sesionId = javaUUID("sesion_id")
    val orden = integer("orden")
    val videoId = varchar("video_id", 11)
    val origen = varchar("origen", 15)
    val solicitudRef = varchar("solicitud_ref", 16).nullable()
    val inicio = timestamp("inicio")
    val fin = timestamp("fin").nullable()
    val segundos = integer("segundos")
    val resultado = varchar("resultado", 15)
    val finInferido = bool("fin_inferido")
    val votos = integer("votos").nullable()
    val esperaSeg = integer("espera_seg").nullable()
    override val primaryKey = PrimaryKey(sesionId, orden)
}

object AnEjecuciones : Table("analitica.ejecucion") {
    val id = long("id").autoIncrement()
    val origen = varchar("origen", 15)
    val inicio = timestamp("inicio")
    val fin = timestamp("fin").nullable()
    val eventosLeidos = integer("eventos_leidos")
    val eventosCargados = integer("eventos_cargados")
    val eventosRechazados = integer("eventos_rechazados")
    val sesionesProcesadas = integer("sesiones_procesadas")
    val sesionesConsolidadas = integer("sesiones_consolidadas")
    val detalle = jsonb("detalle", Json, JsonObject.serializer())
    override val primaryKey = PrimaryKey(id)
}

object AnEventosRechazados : Table("analitica.evento_rechazado") {
    val eventoId = long("evento_id")
    val ejecucionId = long("ejecucion_id")
    val tipo = varchar("tipo", 40)
    val motivo = varchar("motivo", 40)
    val detectadoEn = timestamp("detectado_en")
    override val primaryKey = PrimaryKey(eventoId)
}

object AnSales : Table("analitica.sal_sesion") {
    val sesionId = javaUUID("sesion_id")
    val sal = binary("sal", 32)
    val creadaEn = timestamp("creada_en")
    override val primaryKey = PrimaryKey(sesionId)
}

/** Sesiones que el administrador sacó del tablero (migración V5). Quitar la fila la vuelve a incluir. */
object AnSesionesExcluidas : Table("analitica.sesion_excluida") {
    val sesionId = javaUUID("sesion_id")
    val excluidaEn = timestamp("excluida_en")
    val excluidaPor = javaUUID("excluida_por")
    override val primaryKey = PrimaryKey(sesionId)
}