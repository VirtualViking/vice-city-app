package vicecity.servidor

import vicecity.servidor.dominio.Artista
import vicecity.servidor.dominio.Textos
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reglas puras de texto y de artista: sin base de datos ni red. */
class DominioTest {

    @Test
    fun `normalizar quita tildes, mayusculas y espacios de mas`() {
        val casos = mapOf(
            "  Tití  Me PREGUNTÓ " to "titi me pregunto",
            "Bad Bunny" to "bad bunny",
            "Pingüino" to "pinguino",
            "Ñengo Flow" to "nengo flow",
            "\tKarol\nG " to "karol g",
        )
        casos.forEach { (entrada, esperado) -> assertEquals(esperado, Textos.normalizar(entrada), "normalizar(\"$entrada\")") }
    }

    @Test
    fun `limpiar conserva tildes y mayusculas`() {
        assertEquals("Tití Me Preguntó", Textos.limpiar("  Tití   Me Preguntó "))
    }

    @Test
    fun `recortar respeta el largo maximo`() {
        assertEquals("abc", Textos.recortar("abcdef", 3))
        assertEquals("abc", Textos.recortar("abc", 10))
    }

    @Test
    fun `el artista se deduce del canal o del titulo`() {
        val casos = listOf(
            Triple("Tití Me Preguntó", "Bad Bunny - Topic", "Bad Bunny"),
            Triple("KAROL G - PROVENZA (Official Video)", "KarolGVEVO", "KAROL G"),
            Triple("Provenza", "KarolGVEVO", "Karol G"),
            Triple("Feid, ATL Jacob - LUNA (Official Video)", "FeidVEVO", "Feid, ATL Jacob"),
            Triple("Hips Don't Lie", "Shakira", "Shakira"),
            Triple("Despacito", "Luis Fonsi Official", "Luis Fonsi"),
            Triple("La Bachata", "Manuel Turizo - Oficial", "Manuel Turizo"),
            Triple("Canción sin guion", "VEVO", "VEVO"),
            Triple(" - sin artista antes del guion", "Canal X", "Canal X"),
        )
        casos.forEach { (titulo, canal, esperado) ->
            assertEquals(esperado, Artista.deducir(titulo, canal), "deducir(\"$titulo\", \"$canal\")")
        }
    }
}