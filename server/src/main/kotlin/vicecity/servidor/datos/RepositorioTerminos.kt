package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.plus
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
}