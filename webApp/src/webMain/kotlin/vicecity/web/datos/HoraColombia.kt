package vicecity.web.datos

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Fechas del servidor (ISO 8601 en UTC) mostradas en hora de Colombia, que es UTC-5 todo el año.
 * Se calcula a mano para no sumar una librería de fechas a la web.
 */
@OptIn(ExperimentalTime::class)
object HoraColombia {
    private const val DESFASE_SEG = -5 * 3600L
    private val DIAS = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")
    private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** "8:05 p. m." */
    fun hora(iso: String?): String {
        val local = segundosLocales(iso) ?: return ""
        val minutosDelDia = local.mod(86_400L) / 60
        val h = (minutosDelDia / 60).toInt()
        val m = (minutosDelDia % 60).toInt()
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${m.toString().padStart(2, '0')} ${if (h < 12) "a. m." else "p. m."}"
    }

    /** "hoy a las 8:00 p. m.", "mañana a las 8:00 p. m." o "el viernes 27 de noviembre a las 8:00 p. m." */
    fun diaYHora(iso: String?): String {
        val local = segundosLocales(iso) ?: return ""
        val dia = local.floorDiv(86_400L)
        val hoy = (Clock.System.now().epochSeconds + DESFASE_SEG).floorDiv(86_400L)
        val cuando = when (dia - hoy) {
            0L -> "hoy"
            1L -> "mañana"
            else -> {
                val (_, mes, diaMes) = fechaCivil(dia)
                "el ${DIAS[(dia + 4).mod(7L).toInt()]} $diaMes de ${MESES[mes - 1]}"
            }
        }
        return "$cuando a las ${hora(iso)}"
    }

    /** Minutos desde [iso] hasta ahora (0 si es futuro o no se puede leer). */
    fun minutosDesde(iso: String?): Int {
        val instante = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0
        return ((Clock.System.now() - instante).inWholeMinutes).toInt().coerceAtLeast(0)
    }

    private fun segundosLocales(iso: String?): Long? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { it.epochSeconds + DESFASE_SEG }

    /** Año, mes y día de un número de días desde 1970-01-01 (algoritmo de Howard Hinnant). */
    private fun fechaCivil(diasEpoca: Long): Triple<Int, Int, Int> {
        val z = diasEpoca + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val diaEra = z - era * 146_097
        val anioEra = (diaEra - diaEra / 1_460 + diaEra / 36_524 - diaEra / 146_096) / 365
        val diaAnio = diaEra - (365 * anioEra + anioEra / 4 - anioEra / 100)
        val mp = (5 * diaAnio + 2) / 153
        val dia = diaAnio - (153 * mp + 2) / 5 + 1
        val mes = if (mp < 10) mp + 3 else mp - 9
        val anio = anioEra + era * 400 + if (mes <= 2) 1 else 0
        return Triple(anio.toInt(), mes.toInt(), dia.toInt())
    }
}