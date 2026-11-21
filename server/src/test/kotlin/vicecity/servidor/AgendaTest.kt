package vicecity.servidor

import vicecity.servidor.dominio.Agenda
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cálculo de franjas del horario semanal, en hora de Colombia (UTC-5 todo el año). */
class AgendaTest {
    // Viernes de 20:00 a 03:00 del sábado, y sábado de 18:00 a 23:00
    private val agenda = Agenda(
        mapOf(
            DayOfWeek.FRIDAY to (LocalTime.of(20, 0) to LocalTime.of(3, 0)),
            DayOfWeek.SATURDAY to (LocalTime.of(18, 0) to LocalTime.of(23, 0)),
        ),
    )

    /** Hora local de Colombia convertida a UTC: 2026-11-20 es viernes. */
    private fun bogota(fecha: String, hora: String) = Instant.parse("${fecha}T$hora:00-05:00")

    @Test
    fun `una franja que pasa la medianoche sigue abierta el dia siguiente`() {
        val franja = agenda.franjaEn(bogota("2026-11-21", "02:30"))!!
        assertEquals(bogota("2026-11-20", "20:00"), franja.inicio)
        assertEquals(bogota("2026-11-21", "03:00"), franja.fin)
        assertTrue(bogota("2026-11-20", "20:00") in franja)
        assertNull(agenda.franjaEn(bogota("2026-11-21", "03:00")))   // el cierre ya no está dentro
        assertNull(agenda.franjaEn(bogota("2026-11-20", "19:59")))
    }

    @Test
    fun `la siguiente apertura y el proximo cierre`() {
        assertEquals(bogota("2026-11-21", "18:00"), agenda.siguienteFranja(bogota("2026-11-21", "10:00"))!!.inicio)
        // El domingo no abre: la siguiente es el viernes
        assertEquals(bogota("2026-11-27", "20:00"), agenda.siguienteFranja(bogota("2026-11-22", "12:00"))!!.inicio)
        assertEquals(bogota("2026-11-21", "03:00"), agenda.proximoCierre(bogota("2026-11-20", "23:00")))
        assertEquals(bogota("2026-11-21", "23:00"), agenda.proximoCierre(bogota("2026-11-21", "10:00")))
    }

    @Test
    fun `sin horario no hay franjas`() {
        val vacia = Agenda(emptyMap())
        assertTrue(vacia.vacia)
        assertNull(vacia.franjaEn(Instant.now()))
        assertNull(vacia.siguienteFranja(Instant.now()))
    }
}