package vicecity.servidor.dominio

/**
 * YouTube no tiene un campo "artista": se deduce del canal o del título.
 * Funciona con la mayoría de los videos musicales oficiales, pero no con todos (limitación documentada).
 */
object Artista {
    private const val SUFIJO_TOPIC = " - Topic"
    private const val SUFIJO_VEVO = "VEVO"
    private const val LARGO_MAXIMO = 60
    private val separadoresTitulo = listOf(" - ", " – ", " — ")
    private val sufijoOficial = Regex("""\s*[-|]?\s*(Official|Oficial)(\s+(Channel|Canal|Music))?\s*$""", RegexOption.IGNORE_CASE)
    private val cambioMinusculaMayuscula = Regex("""(?<=\p{Ll})(?=\p{Lu})""")

    /**
     * Orden de las reglas:
     * 1. Canal "Artista - Topic" (canales que YouTube crea para cada artista): el nombre sin el sufijo.
     * 2. Título "Artista - Canción": la parte antes del guion.
     * 3. El canal sin "VEVO" (separando "KarolG" en "Karol G") ni "Official"/"Oficial".
     */
    fun deducir(titulo: String, canal: String): String {
        val canalLimpio = canal.trim()
        if (canalLimpio.endsWith(SUFIJO_TOPIC)) return canalLimpio.removeSuffix(SUFIJO_TOPIC).trim()
        return delTitulo(titulo) ?: limpiarCanal(canalLimpio)
    }

    private fun delTitulo(titulo: String): String? {
        val separador = separadoresTitulo.firstOrNull { it in titulo } ?: return null
        return titulo.substringBefore(separador).trim().trim('"', '\'')
            .takeIf { it.isNotEmpty() && it.length <= LARGO_MAXIMO }
    }

    private fun limpiarCanal(canal: String): String {
        var nombre = canal
        if (nombre.endsWith(SUFIJO_VEVO) && nombre.length > SUFIJO_VEVO.length) {
            nombre = nombre.removeSuffix(SUFIJO_VEVO).replace(cambioMinusculaMayuscula, " ")
        }
        return nombre.replace(sufijoOficial, "").trim().ifEmpty { canal }
    }
}