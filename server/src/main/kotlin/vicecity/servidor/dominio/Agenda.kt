package vicecity.servidor.dominio

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Una franja concreta de operación: desde [inicio] hasta [fin]. */
data class Franja(val inicio: Instant, val fin: Instant) {
    operator fun contains(momento: Instant) = momento >= inicio && momento < fin
}

/**
 * Horario semanal de un establecimiento, en hora de Colombia: a lo sumo una franja por día.
 * Si el cierre es anterior a la apertura, la franja termina al día siguiente (20:00 a 03:00 del viernes
 * termina el sábado a las 03:00). Colombia no tiene horario de verano, así que las horas no se corren.
 */
class Agenda(private val franjas: Map<DayOfWeek, Pair<LocalTime, LocalTime>>, private val zona: ZoneId = ZONA_COLOMBIA) {

    val vacia: Boolean get() = franjas.isEmpty()

    /** La franja que contiene [momento], o null si a esa hora el establecimiento está cerrado. */
    fun franjaEn(momento: Instant): Franja? {
        val fecha = momento.atZone(zona).toLocalDate()
        return listOf(fecha.minusDays(1), fecha).mapNotNull(::franjaDel).firstOrNull { momento in it }
    }

    /** La primera franja que empieza después de [momento], o null si no hay horario. */
    fun siguienteFranja(momento: Instant): Franja? {
        val fecha = momento.atZone(zona).toLocalDate()
        return (0L..7L).asSequence().mapNotNull { franjaDel(fecha.plusDays(it)) }.firstOrNull { it.inicio > momento }
    }

    /** Cuándo cierra: el fin de la franja actual o, si está cerrado, el de la siguiente. */
    fun proximoCierre(momento: Instant): Instant? = (franjaEn(momento) ?: siguienteFranja(momento))?.fin

    /** La franja que empieza en la fecha local [fecha], si ese día tiene horario. */
    private fun franjaDel(fecha: LocalDate): Franja? {
        val (apertura, cierre) = franjas[fecha.dayOfWeek] ?: return null
        val inicio = fecha.atTime(apertura).atZone(zona).toInstant()
        val fechaCierre = if (cierre.isAfter(apertura)) fecha else fecha.plusDays(1)
        return Franja(inicio, fechaCierre.atTime(cierre).atZone(zona).toInstant())
    }

    companion object {
        val ZONA_COLOMBIA: ZoneId = ZoneId.of("America/Bogota")
    }
}