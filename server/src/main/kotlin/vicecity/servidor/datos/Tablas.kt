package vicecity.servidor.datos

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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

object Dispositivos : Table("dispositivo") {
    val hash = varchar("hash", 64)
    val primeraVisita = timestamp("primera_visita")
    val ultimaVisita = timestamp("ultima_visita")
    override val primaryKey = PrimaryKey(hash)
}

object Canciones : Table("cancion") {
    val videoId = varchar("video_id", 11)
    val titulo = varchar("titulo", 200)
    val canal = varchar("canal", 200)
    val duracionSeg = integer("duracion_seg")
    val miniaturaUrl = varchar("miniatura_url", 300).nullable()
    val actualizadoEn = timestamp("actualizado_en")
    val artista = varchar("artista", 200).nullable()
    val tituloNormalizado = varchar("titulo_normalizado", 200).nullable()
    val artistaNormalizado = varchar("artista_normalizado", 200).nullable()
    override val primaryKey = PrimaryKey(videoId)
}

object CancionCategorias : Table("cancion_categoria") {
    val videoId = varchar("video_id", 11)
    val categoria = varchar("categoria", 60)
    override val primaryKey = PrimaryKey(videoId, categoria)
}

/** Caché de búsquedas de YouTube (RNF-14): guarda los videoId en el orden en que YouTube los devolvió. */
object CacheBusquedas : Table("cache_busqueda") {
    val termino = varchar("termino", 100)
    val resultados = jsonb("resultados", Json, ListSerializer(String.serializer()))
    val expiraEn = timestamp("expira_en")
    override val primaryKey = PrimaryKey(termino)
}

object TerminosBuscados : Table("termino_buscado") {
    val establecimientoId = javaUUID("establecimiento_id")
    val termino = varchar("termino", 100)
    val texto = varchar("texto", 100)
    val veces = integer("veces")
    val ultimaBusqueda = timestamp("ultima_busqueda")
    override val primaryKey = PrimaryKey(establecimientoId, termino)
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