package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.util.UUID

/** Términos buscados por establecimiento: la fuente principal del autocompletado. Se usa dentro de una transacción. */
object RepositorioTerminos {

    /** Suma una búsqueda a [termino] (normalizado) y guarda [texto] tal como se escribió, para mostrarlo en las sugerencias. */
    fun registrar(establecimientoId: UUID, termino: String, texto: String, ahora: Instant) {
        TerminosBuscados.upsert(
            onUpdate = {
                it[TerminosBuscados.veces] = TerminosBuscados.veces + 1
                it[TerminosBuscados.texto] = insertValue(TerminosBuscados.texto)
                it[TerminosBuscados.ultimaBusqueda] = insertValue(TerminosBuscados.ultimaBusqueda)
            },
        ) {
            it[TerminosBuscados.establecimientoId] = establecimientoId
            it[TerminosBuscados.termino] = termino
            it[TerminosBuscados.texto] = texto
            it[veces] = 1
            it[ultimaBusqueda] = ahora
        }
    }

    /** Textos buscados en el establecimiento que empiezan por [prefijo] (normalizado), los más buscados primero. */
    fun sugerir(establecimientoId: UUID, prefijo: String, limite: Int): List<String> =
        TerminosBuscados.select(TerminosBuscados.texto)
            .where { (TerminosBuscados.establecimientoId eq establecimientoId) and Coincidencias.empiezaPor(TerminosBuscados.termino, prefijo) }
            .orderBy(TerminosBuscados.veces to SortOrder.DESC, TerminosBuscados.ultimaBusqueda to SortOrder.DESC)
            .limit(limite)
            .map { it[TerminosBuscados.texto] }
}