package vicecity.servidor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import vicecity.compartido.EstadoBaseDatos
import java.net.URI

/** Conexión a PostgreSQL (local o Neon en producción) mediante HikariCP + Exposed, con migraciones Flyway. */
class BaseDatos private constructor(val fuente: HikariDataSource) {
    val database: Database = Database.connect(fuente)

    /** Ejecuta SELECT 1 para comprobar que la base responde. */
    fun responde(): Boolean = runCatching {
        transaction(database) { exec("SELECT 1") { it.next() } } == true
    }.getOrDefault(false)

    /** Aplica las migraciones pendientes de src/main/resources/db/migracion. */
    fun migrar() {
        val flyway = Flyway.configure()
            .dataSource(fuente)
            .locations("classpath:db/migracion")
            .connectRetries(5)               // Neon puede tardar unos instantes en despertar
            .load()
        val resultado = flyway.migrate()
        log.info("Migraciones aplicadas: {} (esquema en versión {})", resultado.migrationsExecuted, flyway.info().current()?.version)
    }

    /** Ejecuta [bloque] en una transacción, fuera del hilo de las corrutinas de Ktor (JDBC es bloqueante). */
    suspend fun <T> tx(bloque: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { bloque() } }

    companion object {
        private val log = LoggerFactory.getLogger(BaseDatos::class.java)

        /** null si no hay DATABASE_URL: el servidor arranca igual y /salud lo informa. */
        fun conectar(url: String?): BaseDatos? {
            if (url == null) {
                log.warn("DATABASE_URL no está definida: el servidor arranca sin base de datos")
                return null
            }
            val jdbc = aJdbc(url)
            val config = HikariConfig().apply {
                jdbcUrl = jdbc.url
                username = jdbc.usuario
                password = jdbc.clave
                maximumPoolSize = 5           // el plan gratuito de Neon admite pocas conexiones
                initializationFailTimeout = -1 // no fallar al arrancar si Neon está "dormido"
            }
            return BaseDatos(HikariDataSource(config))
        }

        fun estado(baseDatos: BaseDatos?): EstadoBaseDatos = when {
            baseDatos == null -> EstadoBaseDatos.NO_CONFIGURADA
            baseDatos.responde() -> EstadoBaseDatos.CONECTADA
            else -> EstadoBaseDatos.ERROR
        }

        /**
         * Convierte la URL de Neon (postgresql://usuario:clave@host/base?params) al formato JDBC.
         * También acepta una URL que ya sea JDBC (jdbc:postgresql://...).
         */
        fun aJdbc(url: String): Jdbc {
            if (url.startsWith("jdbc:")) return Jdbc(url, null, null)
            val uri = URI(url.replaceFirst("postgres://", "postgresql://"))
            val (usuario, clave) = (uri.rawUserInfo ?: ":").split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val puerto = if (uri.port > 0) ":${uri.port}" else ""
            val consulta = uri.rawQuery?.let { "?$it" } ?: ""
            return Jdbc(
                url = "jdbc:postgresql://${uri.host}$puerto${uri.rawPath}$consulta",
                usuario = java.net.URLDecoder.decode(usuario, Charsets.UTF_8),
                clave = java.net.URLDecoder.decode(clave, Charsets.UTF_8),
            )
        }
    }

    data class Jdbc(val url: String, val usuario: String?, val clave: String?)
}
