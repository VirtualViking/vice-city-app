package vicecity.compartido

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Modelo de demanda por hora (analítica con métodos numéricos):
 * 1. ajusta por mínimos cuadrados un polinomio a las solicitudes por hora (promedio por noche),
 * 2. busca la hora pico, el punto más alto de la curva: compara el máximo local (raíz de la derivada, con Newton-Raphson
 *    o, si Newton no sirve, bisección) con los extremos de la curva, y
 * 3. estima la demanda de un intervalo integrando el polinomio con Simpson, comparado con el trapecio.
 * El eje x son horas desde el mediodía (12 p. m. = 0, 11 p. m. = 11, 1 a. m. = 13): así la medianoche no corta la curva.
 */

/** Cómo se encontró la hora pico. BORDE: el punto más alto está en el centro de la primera o de la última hora con datos. */
@Serializable
enum class MetodoPico { NEWTON_RAPHSON, BISECCION, BORDE }

/** Solicitudes observadas en una hora (promedio por noche), en el centro de esa hora: la de las 10 p. m. va en x = 10,5. */
@Serializable
data class PuntoDemanda(val x: Double, val solicitudes: Double)

@Serializable
data class PicoDemanda(
    /** Horas desde el mediodía: 11,67 son las 11:40 p. m. */
    val x: Double,
    /** Solicitudes por hora que estima el modelo en el pico. */
    val solicitudes: Double,
    val metodo: MetodoPico,
    val iteraciones: Int,
)

/** Demanda de un intervalo: la que estima el modelo con cada regla y la que hubo de verdad (promedio por noche). */
data class DemandaIntervalo(val desde: Double, val hasta: Double, val simpson: Double, val trapecio: Double, val reales: Double)

/**
 * Polinomio ajustado a las solicitudes por hora. Se calcula en el eje escalado t = (x − [centro]) / [escala],
 * que lleva la ventana con datos a [−1, 1]: así las potencias no crecen y el sistema de Gauss no pierde precisión.
 */
@Serializable
data class ModeloDemanda(
    val grado: Int,
    /** Coeficientes en el eje escalado t, del término independiente al de mayor grado. */
    val coeficientes: List<Double>,
    val centro: Double,
    val escala: Double,
    /** Ventana con datos, en horas desde el mediodía: de la primera a la última hora con solicitudes. */
    val desde: Double,
    val hasta: Double,
    val puntos: List<PuntoDemanda>,
    /** Coeficiente de determinación: qué parte de la variación explica la curva (1 = perfecta). */
    val r2: Double? = null,
    val pico: PicoDemanda,
    /** Noches promediadas: 1 si es una sola sesión. */
    val noches: Int,
) {
    /** Solicitudes por hora que estima el modelo en [x]. Nunca menos de 0. */
    fun valor(x: Double): Double = max(0.0, Polinomio(coeficientes).valor((x - centro) / escala))

    /**
     * Demanda estimada entre [desdeX] y [hastaX], recortado a la ventana con datos (fuera de ella el modelo no sabe nada).
     * Integra con Simpson y con el trapecio en pasos de 5 minutos. Null si el intervalo queda vacío.
     */
    fun demanda(desdeX: Double, hastaX: Double): DemandaIntervalo? {
        val a = max(desdeX, desde)
        val b = min(hastaX, hasta)
        if (b <= a) return null
        val pasos = ceil((b - a) * PASOS_POR_HORA).toInt()
        val reales = puntos.filter { it.x - 0.5 >= a - TOLERANCIA && it.x + 0.5 <= b + TOLERANCIA }.sumOf { it.solicitudes }
        return DemandaIntervalo(
            desde = a,
            hasta = b,
            simpson = MetodosNumericos.simpson(::valor, a, b, pasos),
            trapecio = MetodosNumericos.trapecio(::valor, a, b, pasos),
            reales = reales,
        )
    }

    companion object {
        /** La posición 0 del eje es el mediodía. */
        const val MEDIODIA = 12
        /** Pasos de 5 minutos al integrar. */
        private const val PASOS_POR_HORA = 12
        /** Una millonésima de hora (menos de una centésima de segundo). */
        private const val TOLERANCIA = 1e-6

        /** Hora del reloj (0 a 23) → horas desde el mediodía (0 a 23). */
        fun ejeDeHora(hora: Int): Int = (hora - MEDIODIA).mod(24)

        /**
         * Ajusta el modelo a [solicitudesPorHora] (24 valores, uno por hora del reloj: 0 = medianoche) de [noches] sesiones.
         * Usa las horas entre la primera y la última con solicitudes, incluidas las que quedaron en cero.
         * Grado 3 con 5 horas o más, grado 2 con 4. Con menos datos devuelve null.
         */
        fun ajustar(solicitudesPorHora: List<Int>, noches: Int): ModeloDemanda? {
            require(solicitudesPorHora.size == 24) { "Se esperan 24 horas" }
            if (noches <= 0) return null
            val enOrden = (0 until 24).map { posicion -> solicitudesPorHora[(posicion + MEDIODIA) % 24] }
            val primera = enOrden.indexOfFirst { it > 0 }
            if (primera < 0) return null
            val ultima = enOrden.indexOfLast { it > 0 }
            val horas = ultima - primera + 1
            val grado = when {
                horas >= 5 -> 3
                horas == 4 -> 2
                else -> return null
            }

            val x = (primera..ultima).map { it + 0.5 }
            val y = (primera..ultima).map { enOrden[it].toDouble() / noches }
            val desde = primera.toDouble()
            val hasta = (ultima + 1).toDouble()
            val centro = (desde + hasta) / 2
            val escala = (hasta - desde) / 2
            val t = x.map { (it - centro) / escala }

            // 1. Mínimos cuadrados con eliminación de Gauss
            val p = MetodosNumericos.minimosCuadrados(t, y, grado)
            val promedio = y.average()
            val totalCuadrados = y.sumOf { (it - promedio) * (it - promedio) }
            val residuos = t.indices.sumOf { (y[it] - p.valor(t[it])) * (y[it] - p.valor(t[it])) }
            val r2 = if (totalCuadrados > 0) 1 - residuos / totalCuadrados else null

            // 2. Hora pico: el punto más alto de la curva entre la primera y la última hora con datos
            val pico = pico(p, t[y.indices.maxBy { y[it] }], t.first(), t.last(), escala)
            return ModeloDemanda(
                grado = grado,
                coeficientes = p.coeficientes,
                centro = centro,
                escala = escala,
                desde = desde,
                hasta = hasta,
                puntos = x.indices.map { PuntoDemanda(x[it], y[it]) },
                r2 = r2,
                pico = PicoDemanda(centro + pico.valor * escala, max(0.0, p.valor(pico.valor)), pico.metodo, pico.iteraciones),
                noches = noches,
            )
        }

        private class Pico(val valor: Double, val metodo: MetodoPico, val iteraciones: Int)

        /**
         * El punto más alto de p entre [tMin] y [tMax], los centros de la primera y de la última hora con datos.
         * Ese punto es un máximo local (raíz de p' con p'' < 0) o uno de los dos extremos, así que se comparan los tres.
         * El máximo local se busca con Newton-Raphson desde la hora observada más alta ([t0]). Si no converge, sale de la
         * ventana o llega a un mínimo, con bisección en el tramo donde p' pasa de positiva a negativa (el más alto si hay varios).
         * Los extremos son los centros y no los bordes de la ventana: más allá del último dato la curva no tiene con qué
         * compararse y puede exagerar.
         */
        private fun pico(p: Polinomio, t0: Double, tMin: Double, tMax: Double, escala: Double): Pico {
            val dp = p.derivada()
            val d2p = dp.derivada()
            val tolerancia = TOLERANCIA / escala
            val maximoLocal = MetodosNumericos.newtonRaphson({ dp.valor(it) }, { d2p.valor(it) }, t0, tMin, tMax, tolerancia)
                ?.takeIf { d2p.valor(it.valor) < 0 }
                ?.let { Pico(it.valor, MetodoPico.NEWTON_RAPHSON, it.iteraciones) }
                ?: maximoPorBiseccion(p, dp, tMin, tMax, escala, tolerancia)
            val candidatos = listOfNotNull(maximoLocal, Pico(tMin, MetodoPico.BORDE, 0), Pico(tMax, MetodoPico.BORDE, 0))
            // Si empatan, gana el máximo local porque va primero en la lista
            return candidatos.maxBy { p.valor(it.valor) }
        }

        /** Tramos de 15 minutos donde p' cambia de + a −, cada uno con bisección. Devuelve el más alto o null si no hay. */
        private fun maximoPorBiseccion(p: Polinomio, dp: Polinomio, tMin: Double, tMax: Double, escala: Double, tolerancia: Double): Pico? {
            val paso = 0.25 / escala
            val cantidad = ceil((tMax - tMin) / paso).toInt()
            return (0 until cantidad).map { i -> tMin + i * paso to min(tMax, tMin + (i + 1) * paso) }
                .filter { (a, b) -> a < b && dp.valor(a) > 0 && dp.valor(b) <= 0 }
                .mapNotNull { (a, b) -> MetodosNumericos.biseccion({ dp.valor(it) }, a, b, tolerancia) }
                .maxByOrNull { p.valor(it.valor) }
                ?.let { Pico(it.valor, MetodoPico.BISECCION, it.iteraciones) }
        }
    }
}