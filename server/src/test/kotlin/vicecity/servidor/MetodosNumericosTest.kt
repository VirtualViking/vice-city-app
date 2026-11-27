package vicecity.servidor

import vicecity.compartido.MetodoPico
import vicecity.compartido.MetodosNumericos
import vicecity.compartido.ModeloDemanda
import vicecity.compartido.Polinomio
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Métodos numéricos del modelo de demanda: cada uno contra un resultado que se conoce de antemano. No usa base de datos. */
class MetodosNumericosTest {

    private fun cerca(esperado: Double, real: Double, tolerancia: Double = 1e-9) =
        assertTrue(abs(esperado - real) <= tolerancia, "Se esperaba $esperado y salió $real")

    /** Solicitudes por hora del reloj (24 valores) a partir de un mapa hora → solicitudes. */
    private fun porHora(vararg horas: Pair<Int, Int>): List<Int> {
        val mapa = horas.toMap()
        return (0..23).map { mapa[it] ?: 0 }
    }

    @Test
    fun `Gauss con pivoteo parcial resuelve un sistema que empieza con un cero en la diagonal`() {
        // x = 1, y = 2, z = 3. Sin pivoteo, el primer paso dividiría por 0
        val a = listOf(listOf(0.0, 2.0, 1.0), listOf(1.0, 1.0, 1.0), listOf(2.0, 1.0, 3.0))
        val x = MetodosNumericos.eliminacionGauss(a, listOf(7.0, 6.0, 13.0))
        listOf(1.0, 2.0, 3.0).zip(x).forEach { (esperado, real) -> cerca(esperado, real) }
        assertFailsWith<IllegalArgumentException> {
            MetodosNumericos.eliminacionGauss(listOf(listOf(1.0, 2.0), listOf(2.0, 4.0)), listOf(3.0, 6.0))
        }
    }

    @Test
    fun `minimos cuadrados recupera un polinomio exacto y coincide con la formula de la recta`() {
        val cubica = Polinomio(listOf(2.0, -1.0, 0.5, 3.0))
        val t = (0..8).map { -1.0 + it * 0.25 }
        val ajustado = MetodosNumericos.minimosCuadrados(t, t.map(cubica::valor), grado = 3)
        cubica.coeficientes.zip(ajustado.coeficientes).forEach { (esperado, real) -> cerca(esperado, real) }

        // Con ruido, la recta debe ser la de las fórmulas cerradas: pendiente 0,8 e intercepto 1,4
        val recta = MetodosNumericos.minimosCuadrados(listOf(0.0, 1.0, 2.0, 3.0, 4.0), listOf(1.0, 3.0, 2.0, 5.0, 4.0), grado = 1)
        cerca(1.4, recta.coeficientes[0])
        cerca(0.8, recta.coeficientes[1])
    }

    @Test
    fun `Newton-Raphson converge mas rapido que biseccion y se detiene si sale del intervalo`() {
        val f = { x: Double -> x * x - 2 }
        val newton = assertNotNull(MetodosNumericos.newtonRaphson(f, { 2 * it }, x0 = 1.0, minimo = 0.0, maximo = 2.0))
        val biseccion = assertNotNull(MetodosNumericos.biseccion(f, 1.0, 2.0))
        cerca(sqrt(2.0), newton.valor, 1e-6)
        cerca(sqrt(2.0), biseccion.valor, 1e-6)
        assertTrue(newton.iteraciones < biseccion.iteraciones, "Newton: ${newton.iteraciones}, bisección: ${biseccion.iteraciones}")

        // Desde 0,001 el primer paso de Newton salta a ~1000, fuera de [0, 2]
        assertNull(MetodosNumericos.newtonRaphson(f, { 2 * it }, x0 = 0.001, minimo = 0.0, maximo = 2.0))
        // Sin cambio de signo no hay bisección
        assertNull(MetodosNumericos.biseccion(f, 2.0, 3.0))
    }

    @Test
    fun `Simpson es exacto en una cubica y el error del trapecio se divide por 4 al duplicar los pasos`() {
        val f = { x: Double -> x * x * x - 2 * x + 1 }   // su integral de 0 a 2 es 2
        cerca(2.0, MetodosNumericos.simpson(f, 0.0, 2.0, 2))
        val error8 = MetodosNumericos.trapecio(f, 0.0, 2.0, 8) - 2.0
        val error16 = MetodosNumericos.trapecio(f, 0.0, 2.0, 16) - 2.0
        cerca(4.0, error8 / error16, 1e-6)
    }

    @Test
    fun `el modelo de demanda encuentra la hora pico y estima la demanda de la noche`() {
        // De 8 p. m. a 1 a. m.: sube rápido, llega al pico cerca de las 10 y baja más despacio
        val horas = porHora(20 to 2, 21 to 8, 22 to 12, 23 to 9, 0 to 5, 1 to 2)
        val modelo = assertNotNull(ModeloDemanda.ajustar(horas, noches = 1))
        assertEquals(3, modelo.grado)
        assertEquals(8.0, modelo.desde)      // 8 p. m.
        assertEquals(14.0, modelo.hasta)     // 2 a. m.: termina la franja de la 1 a. m.

        // El pico es una raíz de la derivada y cae dentro de la franja más alta, de 10 a 11 p. m.
        val pico = modelo.pico
        assertEquals(MetodoPico.NEWTON_RAPHSON, pico.metodo)
        cerca(10.4246, pico.x, 1e-3)        // 10:25 p. m.
        val derivada = Polinomio(modelo.coeficientes).derivada()
        cerca(0.0, derivada.valor((pico.x - modelo.centro) / modelo.escala), 1e-6)
        assertTrue(modelo.r2!! > 0.9)

        // De 9 p. m. a 1 a. m. la curva es positiva: Simpson da la integral exacta del polinomio (34,0417)
        // y queda cerca de las 34 solicitudes reales; el trapecio casi coincide
        val intervalo = assertNotNull(modelo.demanda(9.0, 13.0))
        assertEquals(34.0, intervalo.reales)
        cerca(34.041666, intervalo.simpson, 1e-5)
        cerca(intervalo.simpson, intervalo.trapecio, 0.05)

        // Toda la noche: al empezar la curva bajaría de 0 y se toma como 0; la integral queda cerca de las 38 reales
        val noche = assertNotNull(modelo.demanda(0.0, 24.0))
        assertEquals(38.0, noche.reales)
        assertTrue(abs(noche.simpson - 38.0) < 0.5, "Simpson dio ${noche.simpson}")

        // Con dos noches se promedia: la mitad de solicitudes por hora, el mismo pico
        val dosNoches = assertNotNull(ModeloDemanda.ajustar(horas, noches = 2))
        cerca(pico.x, dosNoches.pico.x)
        cerca(pico.solicitudes / 2, dosNoches.pico.solicitudes)
    }

    @Test
    fun `la medianoche no corta la curva y un intervalo solo cuenta dentro de los datos`() {
        // De 10 p. m. a 2 a. m.: en el eje de la noche son las posiciones 10 a 14, seguidas
        val modelo = assertNotNull(ModeloDemanda.ajustar(porHora(22 to 3, 23 to 6, 0 to 7, 1 to 4, 2 to 1), noches = 1))
        assertEquals(10.0, modelo.desde)
        assertEquals(15.0, modelo.hasta)
        assertEquals(14, ModeloDemanda.ejeDeHora(2))

        // De 11 p. m. a 1 a. m. cuentan las franjas de las 11 y de las 12: 13 solicitudes
        val intervalo = assertNotNull(modelo.demanda(11.0, 13.0))
        assertEquals(13.0, intervalo.reales)
        // Pedir desde las 6 p. m. se recorta a las 10 p. m.; fuera de los datos no hay estimación
        assertEquals(10.0, modelo.demanda(6.0, 13.0)!!.desde)
        assertNull(modelo.demanda(4.0, 9.0))
    }

    @Test
    fun `si la noche solo sube el pico queda en el borde y con pocos datos no hay modelo`() {
        val subiendo = assertNotNull(ModeloDemanda.ajustar(porHora(20 to 1, 21 to 2, 22 to 3, 23 to 4, 0 to 5), noches = 1))
        assertEquals(MetodoPico.BORDE, subiendo.pico.metodo)
        assertEquals(subiendo.hasta, subiendo.pico.x)

        assertEquals(2, ModeloDemanda.ajustar(porHora(21 to 1, 22 to 4, 23 to 3, 0 to 1), noches = 1)?.grado)
        assertNull(ModeloDemanda.ajustar(porHora(21 to 2, 22 to 1, 23 to 2), noches = 1))
        assertNull(ModeloDemanda.ajustar(porHora(), noches = 1))
        assertNull(ModeloDemanda.ajustar(porHora(22 to 5, 23 to 5, 0 to 5, 1 to 5), noches = 0))
    }
}