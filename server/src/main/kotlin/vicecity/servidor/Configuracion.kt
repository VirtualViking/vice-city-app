package vicecity.servidor

/** Origen permitido por CORS, por ejemplo https://virtualviking.github.io */
data class Origen(val esquema: String, val host: String)

/**
 * Configuración leída de variables de entorno (en Render se definen en Environment).
 * - DATABASE_URL: URL de conexión de Neon (postgresql://usuario:clave@host/base?sslmode=require)
 * - CORS_ORIGENES: orígenes del frontend separados por coma
 * - YOUTUBE_API_KEY: clave de la YouTube Data API (entrega 1.2)
 * - JWT_SECRETO: clave para firmar los tokens del personal (texto largo y aleatorio)
 * - ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NOMBRE: cuenta de administrador que se crea al primer arranque
 * - ESTABLECIMIENTO_NOMBRE: nombre del establecimiento que se crea al primer arranque
 */
data class Configuracion(
    val urlBaseDatos: String?,
    val origenesPermitidos: List<Origen>,
    val youtubeApiKey: String?,
    val jwtSecreto: String? = null,
    val adminEmail: String? = null,
    val adminPassword: String? = null,
    val adminNombre: String = "Administrador",
    val establecimientoNombre: String = "Establecimiento de prueba",
) {
    companion object {
        private const val ORIGENES_LOCALES = "http://localhost:8080"

        private fun leer(nombre: String): String? = System.getenv(nombre)?.trim()?.takeIf { it.isNotEmpty() }

        fun desdeEntorno(): Configuracion = Configuracion(
            urlBaseDatos = leer("DATABASE_URL"),
            origenesPermitidos = leerOrigenes(leer("CORS_ORIGENES") ?: ORIGENES_LOCALES),
            youtubeApiKey = leer("YOUTUBE_API_KEY"),
            jwtSecreto = leer("JWT_SECRETO"),
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
