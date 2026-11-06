package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.CodigosError
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.Modo
import vicecity.compartido.Rutas
import vicecity.servidor.datos.Dispositivos
import vicecity.servidor.datos.Eventos
import vicecity.servidor.seguridad.HuellaDispositivo
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Acceso del cliente al escanear el QR e identificación del dispositivo (RF-01, RNF-17). */
class AccesoClienteTest : PruebaIntegracion() {
    private val dispositivo = UUID.randomUUID().toString()

    @Test
    fun `el QR resuelve el establecimiento sin sesion abierta`() = conApp {
        val r = cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }
        assertEquals(HttpStatusCode.OK, r.status)
        val establecimiento = r.body<EstablecimientoPublico>()
        assertEquals("Bar de pruebas", establecimiento.nombre)
        assertNull(establecimiento.sesionActiva)
        // Sin sesión abierta no hay evento de acceso
        transaction(bd.database) { assertEquals(0, Eventos.selectAll().where { Eventos.tipo eq "ACCESO_SESION" }.count()) }
    }

    @Test
    fun `el codigo del QR no distingue mayusculas`() = conApp {
        val r = cliente.get(Rutas.establecimiento(codigoQr().uppercase())) { header(HEADER_DISPOSITIVO, dispositivo) }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `con sesion abierta devuelve la sesion y registra el acceso una sola vez`() = conApp {
        val sesion = abrir(loginAdmin())
        repeat(3) {
            val establecimiento = cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }
                .body<EstablecimientoPublico>()
            assertEquals(sesion.id, establecimiento.sesionActiva?.id)
            assertEquals(Modo.COLA, establecimiento.sesionActiva?.modo)
        }
        transaction(bd.database) {
            val accesos = Eventos.selectAll().where { Eventos.tipo eq "ACCESO_SESION" }.toList()
            assertEquals(1, accesos.size)
            assertEquals("true", accesos.single()[Eventos.datos]["nuevo"].toString())
        }
        // Otro celular sí genera su propio evento
        cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, UUID.randomUUID().toString()) }
        transaction(bd.database) { assertEquals(2, Eventos.selectAll().where { Eventos.tipo eq "ACCESO_SESION" }.count()) }
    }

    @Test
    fun `el dispositivo se guarda solo como hash y conserva su primera visita`() = conApp {
        cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }
        val primera = transaction(bd.database) { Dispositivos.selectAll().single() }
        cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }
        val segunda = transaction(bd.database) { Dispositivos.selectAll().single() }

        val esperado = HuellaDispositivo(config.dispositivoSecreto).de(UUID.fromString(dispositivo))
        assertEquals(esperado, segunda[Dispositivos.hash])
        assertEquals(64, segunda[Dispositivos.hash].length)
        assertTrue(dispositivo !in segunda[Dispositivos.hash])
        assertEquals(primera[Dispositivos.primeraVisita], segunda[Dispositivos.primeraVisita])
        assertNotEquals(primera[Dispositivos.ultimaVisita], segunda[Dispositivos.ultimaVisita])
    }

    @Test
    fun `sin identificador de dispositivo o con uno invalido responde 400`() = conApp {
        error(cliente.get(Rutas.establecimiento(codigoQr())), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        val r = cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, "no-es-un-uuid") }
        val e = error(r, HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        assertEquals(HEADER_DISPOSITIVO, e.detalles["campo"])
    }

    @Test
    fun `un codigo inexistente responde 404`() = conApp {
        val r = cliente.get(Rutas.establecimiento("noexiste")) { header(HEADER_DISPOSITIVO, dispositivo) }
        error(r, HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)
    }
}