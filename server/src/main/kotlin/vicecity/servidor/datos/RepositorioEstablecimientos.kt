package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.EstadoSesion
import java.util.UUID

/** Consultas de establecimientos y de su sesión abierta. Se usan dentro de una transacción. */
object RepositorioEstablecimientos {

    /** El código del QR no distingue mayúsculas: se guarda y se compara en minúsculas. */
    fun porCodigoQr(codigoQr: String): ResultRow? =
        Establecimientos.selectAll()
            .where { Establecimientos.codigoQr eq codigoQr.trim().lowercase() }
            .singleOrNull()

    fun porId(id: UUID): ResultRow? =
        Establecimientos.selectAll().where { Establecimientos.id eq id }.singleOrNull()

    fun sesionAbierta(establecimientoId: UUID): ResultRow? =
        Sesiones.selectAll()
            .where { (Sesiones.establecimientoId eq establecimientoId) and (Sesiones.estado eq EstadoSesion.ABIERTA.name) }
            .singleOrNull()
}