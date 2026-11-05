package vicecity.servidor

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Origen permitido por CORS, por ejemplo https://virtualviking.github.io */
data class Origen(val esquema: String, val host: String)

/**
 * Reglas que protegen el cupo diario de la YouTube Data API (unas 100 búsquedas al día).
 * Las búsquedas que se responden desde la caché no cuentan para ningún límite.
 */
data class ReglasBusqueda(
    val duracionCache: Duration = 7.days,
    val maxNuevasPorDispositivo: Int = 10,
    val ventanaPorDispositivo: Duration = 1.hours,
    val maxNuevasDiarias: Int = 90,
    val maxResultadosYouTube: Int = 20,
    val maxSugerencias: Int = 8,
)

/**
 * Configuración leída de variables de entorno (en Render se definen en Environment).
 * - DATABASE_URL: URL de conexión de Neon (postgresql://usuario:clave@host/base?sslmode=require)
 * - CORS_ORIGENES: orígenes del frontend separados por coma
 * - YOUTUBE_API_KEY: clave de la YouTube Data API
 * - JWT_SECRETO: clave para firmar los tokens del personal (texto largo y aleatorio)
 * - DISPOSITIVO_SECRETO: clave del HMAC que convierte el identificador del celular en dispositivo_hash
 * - WEB_URL: dirección pública de la app web; es la que contiene el código QR
 * - ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NOMBRE: cuenta de administrador que se crea al primer arranque
 * - ESTABLECIMIENTO_NOMBRE: nombre del establecimiento que se crea al primer arranque
 */
data class Configuracion(
    val urlBaseDatos: String?,
    val origenesPermitidos: List<Origen>,
    val youtubeApiKey: String?,
    val jwtSecreto: String? = null,
    val dispositivoSecreto: String? = null,
    val webUrl: String = WEB_URL_POR_DEFECTO,
    val adminEmail: String? = null,
    val adminPassword: String? = null,
    val adminNombre: String = "Administrador",
    val establecimientoNombre: String = "Establecimiento de prueba",
    val reglasBusqueda: ReglasBusqueda = ReglasBusqueda(),
) {
    companion object {
        private const val ORIGENES_LOCALES = "http://localhost:8080"
        const val WEB_URL_POR_DEFECTO = "https://virtualviking.github.io/vice-city-app/"

        private fun leer(nombre: String): String? = System.getenv(nombre)?.trim()?.takeIf { it.isNotEmpty() }

        fun desdeEntorno(): Configuracion = Configuracion(
            urlBaseDatos = leer("DATABASE_URL"),
            origenesPermitidos = leerOrigenes(leer("CORS_ORIGENES") ?: ORIGENES_LOCALES),
            youtubeApiKey = leer("YOUTUBE_API_KEY"),
            jwtSecreto = leer("JWT_SECRETO"),
            dispositivoSecreto = leer("DISPOSITIVO_SECRETO"),
            webUrl = leer("WEB_URL") ?: WEB_URL_POR_DEFECTO,
            adminEmail = leer("ADMIN_EMAIL"),
            adminPassword = leer("ADMIN_PASSWORD"),
            adminNombre = leer("ADMIN_NOMBRE") ?: "Administrador",
            establecimientoNombre = leer("ESTABLECIMIENTO_NOMBRE") ?: "Establecimiento de prueba",
        )

        fun leerOrigenes(texto: String): List<Origen> = texto.split(",")
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
            .map { url ->
                val esquema = url.substringBefore("://", "https")
                Origen(esquema, url.substringAfter("://"))
            }
    }
}