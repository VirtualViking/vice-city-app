package vicecity.servidor.servicios

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import vicecity.servidor.datos.Eventos
import java.time.Instant
import java.util.UUID

/** Catálogo de eventos analíticos (documento de diseño, sección 5.2). */
enum class TipoEvento {
    SESION_ABIERTA,
    ACCESO_SESION,
    BUSQUEDA_REALIZADA,
    SOLICITUD_CREADA,
    SOLICITUD_RECHAZADA,
    VOTO_REGISTRADO,
    REPRODUCCION_INICIADA,
    REPRODUCCION_FINALIZADA,
    REPRODUCCION_FALLIDA,
    SOLICITUD_ELIMINADA,
    MODO_CAMBIADO,
    REPRODUCTOR_PAUSADO,
    REPRODUCTOR_REANUDADO,
    SOLICITUD_NO_REPRODUCIDA,
    SESION_CERRADA,
}

/**
 * Registra eventos en la tabla EVENTO (solo inserción, RNF-15).
 * Debe llamarse dentro de la misma transacción que el cambio que describe: si la operación falla, no queda evento.
 */
object EventoService {
    fun registrar(
        tipo: TipoEvento,
        establecimientoId: UUID,
        sesionId: UUID? = null,
        solicitudId: UUID? = null,
        videoId: String? = null,
        dispositivoHash: String? = null,
        datos: JsonObject = buildJsonObject { },
        idIdempotencia: UUID = UUID.randomUUID(),
        ocurridoEn: Instant = Instant.now(),
    ) {
        Eventos.insert {
            it[Eventos.idIdempotencia] = idIdempotencia
            it[Eventos.tipo] = tipo.name
            it[Eventos.establecimientoId] = establecimientoId
            it[Eventos.sesionId] = sesionId
            it[Eventos.solicitudId] = solicitudId
            it[Eventos.videoId] = videoId
            it[Eventos.dispositivoHash] = dispositivoHash
            it[Eventos.ocurridoEn] = ocurridoEn
            it[Eventos.datos] = datos
        }
    }
}