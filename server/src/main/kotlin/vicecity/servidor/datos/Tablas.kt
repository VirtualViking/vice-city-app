package vicecity.servidor.datos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

/*
 * Mapeo de las tablas creadas por las migraciones Flyway (db/migracion).
 * El esquema lo define el SQL de las migraciones; estos objetos solo lo describen para Exposed.
 */

object Establecimientos : Table("establecimiento") {
    val id = javaUUID("id")
    val nombre = varchar("nombre", 120)
    val codigoQr = varchar("codigo_qr", 16)
    val limiteSolicitudes = integer("limite_solicitudes")
    val ventanaLimiteMin = integer("ventana_limite_min")
    val antirepeticionMin = integer("antirepeticion_min")
    val duracionMaxSeg = integer("duracion_max_seg")
    val creadoEn = timestamp("creado_en")
    override val primaryKey = PrimaryKey(id)
}

object Personal : Table("personal") {
    val id = javaUUID("id")
    val establecimientoId = javaUUID("establecimiento_id")
    val nombre = varchar("nombre", 120)
    val email = varchar("email", 160)
    val passwordHash = varchar("password_hash", 100)
    val rol = varchar("rol", 10)
    val activo = bool("activo")
    val creadoEn = timestamp("creado_en")
    override val primaryKey = PrimaryKey(id)
}

object Sesiones : Table("sesion") {
    val id = javaUUID("id")
    val establecimientoId = javaUUID("establecimiento_id")
    val estado = varchar("estado", 10)
    val modoActual = varchar("modo_actual", 15)
    val estadoReproductor = varchar("estado_reproductor", 15)
    val posicionListaDefecto = integer("posicion_lista_defecto").nullable()
    val inicio = timestamp("inicio")
    val fin = timestamp("fin").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Solicitudes : Table("solicitud") {
    val id = javaUUID("id")
    val sesionId = javaUUID("sesion_id")
    val videoId = varchar("video_id", 11)
    val dispositivoHash = varchar("dispositivo_hash", 64)
    val estado = varchar("estado", 15)
    val solicitadaEn = timestamp("solicitada_en")
    val inicioReproduccion = timestamp("inicio_reproduccion").nullable()
    val finReproduccion = timestamp("fin_reproduccion").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Votos : Table("voto") {
    val id = javaUUID("id")
    val solicitudId = javaUUID("solicitud_id")
    val dispositivoHash = varchar("dispositivo_hash", 64)
    val creadoEn = timestamp("creado_en")
    override val primaryKey = PrimaryKey(id)
}

object Eventos : Table("evento") {
    val id = long("id")
    val idIdempotencia = javaUUID("id_idempotencia")
    val tipo = varchar("tipo", 40)
    val establecimientoId = javaUUID("establecimiento_id")
    val sesionId = javaUUID("sesion_id").nullable()
    val solicitudId = javaUUID("solicitud_id").nullable()
    val videoId = varchar("video_id", 11).nullable()
    val dispositivoHash = varchar("dispositivo_hash", 64).nullable()
    val ocurridoEn = timestamp("ocurrido_en")
    val datos = jsonb("datos", Json, JsonObject.serializer())
    override val primaryKey = PrimaryKey(id)
}