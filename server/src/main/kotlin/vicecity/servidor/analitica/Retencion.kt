package vicecity.servidor.analitica

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import vicecity.compartido.EstadoSesion
import vicecity.compartido.ResultadoRetencion
import vicecity.servidor.datos.Dispositivos
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.datos.Votos
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Plazos de retención del diseño (sección 6.2). La capa analítica es anónima y se conserva.
 * - Solicitudes y votos operativos: 90 días después del cierre de su sesión.
 * - Dispositivos: 12 meses sin actividad (y sin solicitudes ni votos que los referencien).
 * - Eventos crudos: 24 meses. El trigger de la tabla evento solo deja borrarlos pasado ese plazo
 *   y con la variable vicecity.retencion activa en la transacción (migración V4).
 */
object Retencion {
    val SOLICITUDES: Duration = Duration.ofDays(90)
    const val MESES_DISPOSITIVOS = 12L
    const val MESES_EVENTOS = 24L

    fun JdbcTransaction.aplicar(ahora: Instant): ResultadoRetencion {
        val sesionesViejas = Sesiones.select(Sesiones.id)
            .where { (Sesiones.estado eq EstadoSesion.CERRADA.name) and (Sesiones.fin less ahora.minus(SOLICITUDES)) }
        val solicitudesViejas = Solicitudes.select(Solicitudes.id).where { Solicitudes.sesionId inSubQuery sesionesViejas }
        val votos = Votos.deleteWhere { Votos.solicitudId inSubQuery solicitudesViejas }
        val solicitudes = Solicitudes.deleteWhere { Solicitudes.sesionId inSubQuery sesionesViejas }

        val dispositivos = Dispositivos.deleteWhere {
            (Dispositivos.ultimaVisita less menosMeses(ahora, MESES_DISPOSITIVOS)) and
                    notExists(Solicitudes.select(Solicitudes.id).where { Solicitudes.dispositivoHash eq Dispositivos.hash }) and
                    notExists(Votos.select(Votos.id).where { Votos.dispositivoHash eq Dispositivos.hash })
        }

        // Un minuto de margen para que la fecha de corte quede siempre antes de la del trigger (now() - 24 meses)
        exec("SET LOCAL vicecity.retencion = 'activa'")
        val eventos = Eventos.deleteWhere { Eventos.ocurridoEn less menosMeses(ahora, MESES_EVENTOS).minusSeconds(60) }
        exec("SET LOCAL vicecity.retencion = 'inactiva'")

        return ResultadoRetencion(solicitudes = solicitudes, votos = votos, dispositivos = dispositivos, eventos = eventos)
    }

    private fun menosMeses(ahora: Instant, meses: Long): Instant = ahora.atZone(ZoneOffset.UTC).minusMonths(meses).toInstant()
}