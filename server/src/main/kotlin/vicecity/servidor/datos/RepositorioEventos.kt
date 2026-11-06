package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Instant

/** Cuántas búsquedas consumieron cupo de YouTube desde [desde], y cuándo fue la primera de ellas. */
data class ConteoBusquedas(val cantidad: Int, val primera: Instant?)

/** Consultas de lectura sobre la tabla EVENTO. */
object RepositorioEventos {

    /**
     * Búsquedas respondidas por YouTube (fuente = YOUTUBE) desde [desde]; si se da [dispositivoHash], solo las de ese dispositivo.
     * Usa el índice parcial ix_evento_busquedas_youtube de la migración V2.
     */
    fun busquedasYouTube(tx: JdbcTransaction, desde: Instant, dispositivoHash: String? = null): ConteoBusquedas {
        val filtroDispositivo = if (dispositivoHash != null) "AND dispositivo_hash = ?" else ""
        val argumentos = buildList {
            add(JavaInstantColumnType() to desde)
            if (dispositivoHash != null) add(VarCharColumnType(64) to dispositivoHash)
        }
        return tx.exec(
            """
            SELECT count(*), min(ocurrido_en) FROM evento
            WHERE tipo = 'BUSQUEDA_REALIZADA' AND datos ->> 'fuente' = 'YOUTUBE' AND ocurrido_en > ? $filtroDispositivo
            """.trimIndent(),
            argumentos,
        ) { rs ->
            rs.next()
            ConteoBusquedas(rs.getInt(1), rs.getTimestamp(2)?.toInstant())
        }!!
    }
}