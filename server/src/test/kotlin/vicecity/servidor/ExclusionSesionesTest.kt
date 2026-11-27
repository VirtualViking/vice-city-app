package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.CambioExclusion
import vicecity.compartido.CodigosError
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionAnalitica
import vicecity.compartido.SesionesAnaliticas
import vicecity.compartido.TableroAnalitico
import vicecity.servidor.datos.AnCanciones
import vicecity.servidor.datos.AnSesiones
import vicecity.servidor.datos.AnSesionesExcluidas
import vicecity.servidor.datos.AnSolicitudes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** El administrador saca sesiones (por ejemplo, de prueba) del tablero y las vuelve a incluir, sin borrar datos. */
class ExclusionSesionesTest : PruebaIntegracion() {

    private val prueba = UUID.randomUUID()
    private val real = UUID.randomUUID()
    private val abierta = UUID.randomUUID()

    private fun instante(fecha: String, hora: String) = Instant.parse("${fecha}T$hora:00-05:00")

    /** Sesión de prueba con 3 solicitudes, una real con 2 y una abierta sin solicitudes. */
    private fun cargarSesiones() = transaction(bd.database) {
        AnCanciones.insert {
            it[videoId] = "video000001"
            it[titulo] = "Canción uno"
            it[artista] = "Artista Uno"
            it[duracionSeg] = 200
            it[actualizadaEn] = Instant.now()
        }
        listOf(Triple(prueba, "2026-11-12", 3), Triple(real, "2026-11-20", 2), Triple(abierta, "2026-11-21", 0)).forEach { (id, fecha, n) ->
            AnSesiones.insert {
                it[sesionId] = id
                it[establecimientoId] = establecimientoId()
                it[inicio] = instante(fecha, "20:00")
                it[fin] = if (id == abierta) null else instante(fecha, "23:00")
                it[fechaLocal] = LocalDate.parse(fecha)
                it[automatica] = false
                it[transiciones] = 0
                it[consolidada] = id != abierta
                it[procesadaEn] = Instant.now()
            }
            (1..n).forEach { i ->
                AnSolicitudes.insert {
                    it[sesionId] = id
                    it[solicitudRef] = "$fecha-$i".padEnd(16, '0').take(16)
                    it[participante] = "p$i".padEnd(16, '0')
                    it[videoId] = "video000001"
                    it[solicitadaEn] = instante(fecha, "21:00")
                    it[horaLocal] = 21
                    it[estadoFinal] = "REPRODUCIDA"
                    it[votos] = 1
                    it[esperaSeg] = 60
                }
            }
        }
    }

    private suspend fun Contexto.sesiones(token: String): SesionesAnaliticas =
        cliente.get(Rutas.ANALITICA_SESIONES) {
            bearerAuth(token)
            parameter("desde", "2026-11-01")
            parameter("hasta", "2026-11-25")
        }.body()

    private suspend fun Contexto.periodo(token: String): TableroAnalitico =
        cliente.get(Rutas.ANALITICA) {
            bearerAuth(token)
            parameter("desde", "2026-11-01")
            parameter("hasta", "2026-11-25")
        }.body()

    private suspend fun Contexto.excluir(token: String, id: String, excluida: Boolean = true): HttpResponse =
        cliente.put(Rutas.exclusionSesionAnalitica(id)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CambioExclusion(excluida))
        }

    @Test
    fun `una sesion excluida deja de contar en el tablero y se puede volver a incluir`() = conApp {
        cargarSesiones()
        val token = loginAdmin()
        assertEquals(5, periodo(token).resumen.solicitudes)

        val r = excluir(token, prueba.toString())
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.body<SesionAnalitica>().excluida)
        // Excluir otra vez no cambia nada
        assertEquals(HttpStatusCode.OK, excluir(token, prueba.toString()).status)
        assertEquals(1L, transaction(bd.database) { AnSesionesExcluidas.selectAll().count() })

        val lista = sesiones(token)
        assertEquals(listOf(abierta, real).map { it.toString() }, lista.sesiones.map { it.id })
        assertEquals(listOf(prueba.toString()), lista.excluidas.map { it.id })
        assertEquals(3, lista.excluidas.single().solicitudes)

        // El periodo ya no la cuenta, pero sus datos siguen ahí: pedida por su id, se ve completa
        val sinPrueba = periodo(token)
        assertEquals(2, sinPrueba.resumen.sesiones)
        assertEquals(2, sinPrueba.resumen.solicitudes)
        val sola: TableroAnalitico = cliente.get(Rutas.ANALITICA) { bearerAuth(token); parameter("sesion", prueba.toString()) }.body()
        assertEquals(3, sola.resumen.solicitudes)

        // Volver a incluirla
        val de = excluir(token, prueba.toString(), excluida = false)
        assertEquals(HttpStatusCode.OK, de.status)
        assertFalse(de.body<SesionAnalitica>().excluida)
        assertTrue(sesiones(token).excluidas.isEmpty())
        assertEquals(5, periodo(token).resumen.solicitudes)
    }

    @Test
    fun `solo el administrador excluye y solo sesiones cerradas de su establecimiento`() = conApp {
        cargarSesiones()
        val ajena = UUID.randomUUID()
        transaction(bd.database) {
            AnSesiones.insert {
                it[sesionId] = ajena
                it[establecimientoId] = UUID.randomUUID()
                it[inicio] = instante("2026-11-20", "20:00")
                it[fin] = instante("2026-11-20", "23:00")
                it[fechaLocal] = LocalDate.parse("2026-11-20")
                it[automatica] = false
                it[transiciones] = 0
                it[consolidada] = true
                it[procesadaEn] = Instant.now()
            }
        }
        val token = loginAdmin()
        error(excluir(token, abierta.toString()), HttpStatusCode.Conflict, CodigosError.SESION_EN_CURSO)
        error(excluir(token, ajena.toString()), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(excluir(token, UUID.randomUUID().toString()), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
        error(excluir(token, "no-es-un-id"), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)

        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        error(excluir(login("dj@vicecity.test", "clave-dj-123"), prueba.toString()), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.put(Rutas.exclusionSesionAnalitica(prueba.toString())), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
        assertEquals(0L, transaction(bd.database) { AnSesionesExcluidas.selectAll().count() })
    }
}