package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or

/** Comparaciones LIKE sobre columnas normalizadas (minúsculas, sin tildes). */
object Coincidencias {
    private const val ESCAPE = '\\'

    /** El texto empieza por [termino]: "bad" encuentra "bad bunny". */
    fun empiezaPor(columna: Column<String>, termino: String): Op<Boolean> =
        columna like LikePattern(escapar(termino) + "%", ESCAPE)

    /** Alguna palabra del texto empieza por [termino]: "pregunto" encuentra "titi me pregunto". */
    fun algunaPalabraEmpiezaPor(columna: Column<String?>, termino: String): Op<Boolean> =
        (columna like LikePattern(escapar(termino) + "%", ESCAPE)) or
                (columna like LikePattern("% " + escapar(termino) + "%", ESCAPE))

    /** Los comodines de LIKE (% y _) que escriba el usuario se buscan como texto. */
    private fun escapar(texto: String): String =
        texto.replace("$ESCAPE", "$ESCAPE$ESCAPE").replace("%", "$ESCAPE%").replace("_", "${ESCAPE}_")
}