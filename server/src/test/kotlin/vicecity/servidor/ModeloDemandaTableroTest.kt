package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.MetodoPico
import vicecity.compartido.Rutas
import vicecity.compartido.TableroAnalitico
import vicecity.servidor.datos.AnSesiones
import vicecity.servidor.datos.AnSolicitudes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** El tablero trae el modelo de demanda (métodos numéricos) calculado con las solicitudes por hora de la capa analítica. */
class ModeloDemandaTableroTest : PruebaIntegracion() {

    /** Solicitudes por hora de una noche de 8 p. m. a 2 a. m.: sube rápido, pico cerca de las 10 y baja más despacio. */
    private val conteos = listOf(20 to 2, 21 to 8, 22 to 12, 23 to 9, 0 to 5, 1 to 2)

    /** Carga una noche que empieza el día [fecha] con las solicitudes de [conteos]. */
    private fun cargarNoche(fecha: String) = transaction(bd.database) {
        val sesion = UUID.randomUUID()
        val dia = LocalDate.parse(fecha)
        AnSesiones.insert {
            it[sesionId] = sesion
            it[establecimientoId] = establecimientoId()
            it[inicio] = Instant.parse("${fecha}T20:00:00-05:00")
            it[fin] = Instant.parse("${dia.plusDays(1)}T02:00:00-05:00")
            it[fechaLocal] = dia
            it[automatica] = false
            it[transiciones] = 0
            it[consolidada] = true
            it[procesadaEn] = Instant.now()
        }
        var n = 0
        conteos.forEach { (hora, cantidad) ->
            val diaHora = if (hora < 12) dia.plusDays(1) else dia
            repeat(cantidad) {
                n++
                AnSolicitudes.insert {
                    it[sesionId] = sesion
                    it[solicitudRef] = "ref" + n.toString().padStart(13, '0')
                    it[participante] = "p" + n.toString().padStart(15, '0')
                    it[videoId] = "video000001"
                    it[solicitadaEn] = Instant.parse("${diaHora}T${hora.toString().padStart(2, '0')}:15:00-05:00")
                    it[horaLocal] = hora.toShort()
                    it[estadoFinal] = "REPRODUCIDA"
                    it[votos] = 1
                    it[esperaSeg] = 120
                }
            }
        }
        sesion
    }

    private suspend fun Contexto.tablero(token: String, sesion: UUID? = null): TableroAnalitico =
        cliente.get(Rutas.ANALITICA) {
            bearerAuth(token)
            parameter("desde", "2026-11-01")
            parameter("hasta", "2026-11-25")
            if (sesion != null) parameter("sesion", sesion.toString())
        }.body()

    @Test
    fun `el tablero ajusta la curva de una noche y promedia las noches del periodo`() = conApp {
        val primera = cargarNoche("2026-11-20")
        val token = loginAdmin()

        // Una sola noche: polinomio de grado 3 y hora pico con Newton-Raphson, cerca de las 10:25 p. m.
        val unaNoche = assertNotNull(tablero(token, primera).modeloDemanda)
        assertEquals(3, unaNoche.grado)
        assertEquals(1, unaNoche.noches)
        assertEquals(MetodoPico.NEWTON_RAPHSON, unaNoche.pico.metodo)
        assertTrue(abs(unaNoche.pico.x - 10.4246) < 1e-3, "Pico en ${unaNoche.pico.x}")
        assertEquals(listOf(2.0, 8.0, 12.0, 9.0, 5.0, 2.0), unaNoche.puntos.map { it.solicitudes })

        // Dos noches iguales en el periodo: el promedio por noche da la misma curva
        cargarNoche("2026-11-21")
        val periodo = assertNotNull(tablero(token).modeloDemanda)
        assertEquals(2, periodo.noches)
        assertEquals(unaNoche.puntos, periodo.puntos)
        assertTrue(abs(unaNoche.pico.x - periodo.pico.x) < 1e-9)
    }
}