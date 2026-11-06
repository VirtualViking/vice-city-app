package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant

/** Caché de búsquedas de YouTube (RNF-14). La clave es el término normalizado. Se usa dentro de una transacción. */
object RepositorioCache {

    /** videoId guardados para [termino] si la entrada no ha vencido; null si no hay o venció. */
    fun vigente(termino: String, ahora: Instant): List<String>? =
        CacheBusquedas.selectAll()
            .where { (CacheBusquedas.termino eq termino) and (CacheBusquedas.expiraEn greater ahora) }
            .singleOrNull()
            ?.get(CacheBusquedas.resultados)

    fun guardar(termino: String, videoIds: List<String>, expiraEn: Instant) {
        CacheBusquedas.upsert {
            it[CacheBusquedas.termino] = termino
            it[resultados] = videoIds
            it[CacheBusquedas.expiraEn] = expiraEn
        }
    }
}