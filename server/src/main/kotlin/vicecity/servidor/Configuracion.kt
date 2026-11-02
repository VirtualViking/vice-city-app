package vicecity.servidor

/** Origen permitido por CORS, por ejemplo https://virtualviking.github.io */
data class Origen(val esquema: String, val host: String)

/**
 * Configuración leída de variables de entorno (en Render se definen en el panel del servicio).
 * - DATABASE_URL: URL de conexión de Neon (postgresql://usuario:clave@host/base?sslmode=require)
 * - CORS_ORIGENES: orígenes del frontend separados por coma
 * - YOUTUBE_API_KEY: clave de la YouTube Data API (se usará en la iteración 1)
 */
data class Configuracion(
    val urlBaseDatos: String?,
    val origenesPermitidos: List<Origen>,
    val youtubeApiKey: String?,
) {
    companion object {
        private const val ORIGENES_LOCALES = "http://localhost:8080"

        fun desdeEntorno(): Configuracion = Configuracion(
            urlBaseDatos = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() },
            origenesPermitidos = leerOrigenes(System.getenv("CORS_ORIGENES") ?: ORIGENES_LOCALES),
            youtubeApiKey = System.getenv("YOUTUBE_API_KEY")?.takeIf { it.isNotBlank() },
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
