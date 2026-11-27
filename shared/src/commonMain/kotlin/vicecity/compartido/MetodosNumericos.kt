package vicecity.compartido

import kotlin.math.abs
import kotlin.math.pow

/*
 * Métodos numéricos del modelo de demanda (analítica): eliminación de Gauss, mínimos cuadrados,
 * Newton-Raphson, bisección y las reglas de Simpson y del trapecio.
 * Son funciones puras: el servidor las usa para ajustar el modelo y la web para integrar el intervalo que se elija.
 */

/** Polinomio c0 + c1·t + c2·t² + …: [coeficientes] va del término independiente al de mayor grado. */
data class Polinomio(val coeficientes: List<Double>) {
    val grado: Int get() = coeficientes.size - 1

    /** Valor en [t] con el método de Horner: tantas multiplicaciones como el grado, sin calcular potencias. */
    fun valor(t: Double): Double {
        var resultado = 0.0
        for (i in coeficientes.indices.reversed()) resultado = resultado * t + coeficientes[i]
        return resultado
    }

    fun derivada(): Polinomio =
        if (coeficientes.size <= 1) Polinomio(listOf(0.0))
        else Polinomio(coeficientes.drop(1).mapIndexed { i, c -> c * (i + 1) })
}

/** Raíz que encontró un método iterativo y cuántas iteraciones le tomó. */
data class Raiz(val valor: Double, val iteraciones: Int)

object MetodosNumericos {

    /**
     * Resuelve A·x = b por eliminación de Gauss con pivoteo parcial y sustitución hacia atrás.
     * En cada columna se usa como pivote el valor más grande en valor absoluto, para no dividir por números pequeños.
     * Lanza IllegalArgumentException si el sistema no tiene solución única.
     */
    fun eliminacionGauss(a: List<List<Double>>, b: List<Double>): List<Double> {
        val n = b.size
        require(a.size == n && a.all { it.size == n }) { "La matriz debe ser cuadrada y del tamaño de b" }
        // Matriz aumentada [A | b], copiada para no modificar la original
        val m = MutableList(n) { i -> (a[i] + b[i]).toMutableList() }
        for (k in 0 until n) {
            val pivote = (k until n).maxBy { abs(m[it][k]) }
            require(abs(m[pivote][k]) > EPSILON_PIVOTE) { "El sistema no tiene solución única" }
            if (pivote != k) {
                val fila = m[k]
                m[k] = m[pivote]
                m[pivote] = fila
            }
            for (i in k + 1 until n) {
                val factor = m[i][k] / m[k][k]
                for (j in k..n) m[i][j] -= factor * m[k][j]
            }
        }
        val x = MutableList(n) { 0.0 }
        for (i in n - 1 downTo 0) {
            var suma = m[i][n]
            for (j in i + 1 until n) suma -= m[i][j] * x[j]
            x[i] = suma / m[i][i]
        }
        return x
    }

    /**
     * Mínimos cuadrados: el polinomio de grado [grado] que minimiza la suma de (yᵢ − p(xᵢ))².
     * Arma las ecuaciones normales (AᵀA)·c = Aᵀy, con A la matriz de Vandermonde (fila i: 1, xᵢ, xᵢ², …),
     * y las resuelve con eliminación de Gauss.
     */
    fun minimosCuadrados(x: List<Double>, y: List<Double>, grado: Int): Polinomio {
        require(x.size == y.size) { "x e y deben tener el mismo tamaño" }
        require(x.size > grado) { "Se necesitan al menos ${grado + 1} puntos para un polinomio de grado $grado" }
        val m = grado + 1
        // (AᵀA)[i][j] = Σ xᵏ con k = i + j, y (Aᵀy)[i] = Σ y·xⁱ
        val sumasPotencias = List(2 * grado + 1) { k -> x.sumOf { it.pow(k) } }
        val ata = List(m) { i -> List(m) { j -> sumasPotencias[i + j] } }
        val aty = List(m) { i -> x.indices.sumOf { y[it] * x[it].pow(i) } }
        return Polinomio(eliminacionGauss(ata, aty))
    }

    /**
     * Newton-Raphson: x(k+1) = x(k) − f(x(k)) / f'(x(k)), desde [x0], hasta que el paso sea menor que [tolerancia].
     * Devuelve null si la derivada se anula, si sale de [[minimo], [maximo]] o si no converge en [maxIteraciones].
     */
    fun newtonRaphson(
        f: (Double) -> Double,
        df: (Double) -> Double,
        x0: Double,
        minimo: Double,
        maximo: Double,
        tolerancia: Double = 1e-6,
        maxIteraciones: Int = 50,
    ): Raiz? {
        var x = x0
        for (i in 1..maxIteraciones) {
            val pendiente = df(x)
            if (abs(pendiente) < EPSILON_PIVOTE) return null
            val siguiente = x - f(x) / pendiente
            if (siguiente.isNaN() || siguiente < minimo || siguiente > maximo) return null
            if (abs(siguiente - x) < tolerancia) return Raiz(siguiente, i)
            x = siguiente
        }
        return null
    }

    /**
     * Bisección: parte [a, b] por la mitad y se queda con la mitad donde f cambia de signo,
     * hasta que el intervalo mida menos que [tolerancia]. Devuelve null si f(a) y f(b) tienen el mismo signo.
     */
    fun biseccion(f: (Double) -> Double, a: Double, b: Double, tolerancia: Double = 1e-6, maxIteraciones: Int = 100): Raiz? {
        var izquierda = a
        var derecha = b
        var fIzquierda = f(izquierda)
        if (fIzquierda * f(derecha) > 0) return null
        var iteraciones = 0
        while (derecha - izquierda > tolerancia && iteraciones < maxIteraciones) {
            iteraciones++
            val medio = (izquierda + derecha) / 2
            val fMedio = f(medio)
            if (fIzquierda * fMedio <= 0) {
                derecha = medio
            } else {
                izquierda = medio
                fIzquierda = fMedio
            }
        }
        return Raiz((izquierda + derecha) / 2, iteraciones)
    }

    /** Regla de Simpson compuesta con [n] subintervalos (si es impar se usa el par siguiente). Es exacta para polinomios de grado 3 o menos. */
    fun simpson(f: (Double) -> Double, a: Double, b: Double, n: Int): Double {
        val pasos = maxOf(2, if (n % 2 == 0) n else n + 1)
        val h = (b - a) / pasos
        var suma = f(a) + f(b)
        for (i in 1 until pasos) suma += f(a + i * h) * (if (i % 2 == 1) 4 else 2)
        return suma * h / 3
    }

    /** Regla del trapecio compuesta con [n] subintervalos. Su error baja con h²: al duplicar [n], se divide más o menos por 4. */
    fun trapecio(f: (Double) -> Double, a: Double, b: Double, n: Int): Double {
        val pasos = maxOf(1, n)
        val h = (b - a) / pasos
        var suma = (f(a) + f(b)) / 2
        for (i in 1 until pasos) suma += f(a + i * h)
        return suma * h
    }

    private const val EPSILON_PIVOTE = 1e-12
}