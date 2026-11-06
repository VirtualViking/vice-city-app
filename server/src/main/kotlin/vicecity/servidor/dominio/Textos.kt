package vicecity.servidor.dominio

import java.text.Normalizer
import java.util.Locale

/** Reglas de texto compartidas por la búsqueda y el autocompletado. */
object Textos {
    private val marcas = Regex("\\p{Mn}+")
    private val espacios = Regex("\\s+")

    /** Quita espacios al inicio y al final y deja uno solo entre palabras. Es el texto que se muestra y se envía a YouTube. */
    fun limpiar(texto: String): String = texto.replace(espacios, " ").trim()

    /**
     * Forma de comparar textos: minúsculas, sin tildes ni diéresis y con espacios simples.
     * "  Tití  Me PREGUNTÓ " y "titi me pregunto" dan el mismo resultado.
     */
    fun normalizar(texto: String): String =
        Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD)
            .replace(marcas, "")
            .lowercase(Locale.ROOT)

    /** Corta el texto a [maximo] caracteres para que quepa en su columna. */
    fun recortar(texto: String, maximo: Int): String = if (texto.length <= maximo) texto else texto.take(maximo).trimEnd()
}