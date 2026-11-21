package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.CambioMantenimiento
import vicecity.compartido.CodigosError
import vicecity.compartido.ConfiguracionOperacion
import vicecity.compartido.DiaSemana
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.EstadoEstablecimiento
import vicecity.compartido.EstadoOperacion
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.Horario
import vicecity.compartido.MotivoEliminacion
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.ResultadoLimpieza
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionActiva
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.Personal
import vicecity.servidor.datos.RepositorioBloqueos
import vicecity.servidor.datos.RepositorioCache
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.dominio.Agenda
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Horario, programación automática, mantenimiento y limpieza. */
class OperacionTest : PruebaIntegracion() {
    private val dispositivo = UUID.randomUUID().toString()
    private val formato = DateTimeFormatter.ofPattern("HH:mm")

    /** El mismo horario todos los días, relativo a ahora: así la prueba no depende de la hora en que corre. */
    private fun todosLosDias(desde: Duration, hasta: Duration): List<Horario> {
        val ahora = Instant.now()
        val apertura = ahora.plus(desde).atZone(Agenda.ZONA_COLOMBIA).format(formato)
        val cierre = ahora.plus(hasta).atZone(Agenda.ZONA_COLOMBIA).format(formato)
        return DiaSemana.entries.map { Horario(it, apertura, cierre) }
    }

    private suspend fun Contexto.configurar(token: String, automatica: Boolean, horarios: List<Horario>): HttpResponse =
        cliente.put(Rutas.OPERACION) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ConfiguracionOperacion(automatica, horarios))
        }

    private suspend fun Contexto.mantenimiento(token: String, activo: Boolean): HttpResponse =
        cliente.put(Rutas.MANTENIMIENTO) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CambioMantenimiento(activo))
        }

    private suspend fun Contexto.escanear(): EstablecimientoPublico =
        cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }
            .also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.activa(token: String) =
        cliente.get(Rutas.SESION_ACTIVA) { bearerAuth(token) }.body<SesionActiva>().sesion

    private fun eventos(tipo: String) = transaction(bd.database) { Eventos.selectAll().where { Eventos.tipo eq tipo }.toList() }

    @Test
    fun `con programacion automatica la sesion se abre sola en el horario y no se reabre si el DJ la cierra`() = conApp {
        val token = loginAdmin()
        val r = configurar(token, true, todosLosDias(Duration.ofHours(-1), Duration.ofHours(1)))
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(EstadoEstablecimiento.ABIERTO, r.body<EstadoOperacion>().estado)

        val qr = escanear()
        assertEquals(EstadoEstablecimiento.ABIERTO, qr.estado)
        val sesion = activa(token)!!
        assertEquals(sesion.id, qr.sesionActiva!!.id)
        assertTrue(sesion.automatica)
        assertEquals("AUTOMATICA", eventos("SESION_ABIERTA").single()[Eventos.datos]["origen"].toString().trim('"'))

        // El DJ la cierra antes de la hora: esa franja ya no se vuelve a abrir
        assertEquals(HttpStatusCode.OK, cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }.status)
        val despues = escanear()
        assertEquals(EstadoEstablecimiento.SIN_SESION, despues.estado)
        assertNull(despues.sesionActiva)
        assertEquals(1, eventos("SESION_ABIERTA").size)
    }

    @Test
    fun `fuera de horario el QR queda desactivado y dice cuando abre`() = conApp {
        val token = loginAdmin()
        configurar(token, true, todosLosDias(Duration.ofHours(2), Duration.ofHours(3)))
        val qr = escanear()
        assertEquals(EstadoEstablecimiento.FUERA_DE_HORARIO, qr.estado)
        assertNull(qr.sesionActiva)
        val proxima = Instant.parse(qr.proximaApertura)
        val esperada = Instant.now().plus(Duration.ofHours(2))
        assertTrue(Duration.between(proxima, esperada).abs() < Duration.ofMinutes(2), "próxima apertura: $proxima")

        // Una sesión abierta a mano fuera del horario no se cierra sola
        val manual = abrir(token)
        assertEquals(EstadoEstablecimiento.ABIERTO, escanear().estado)
        assertEquals(manual.id, activa(token)!!.id)

        // Sin programación automática, el horario sigue diciendo si el QR está activo
        cliente.post(Rutas.cerrarSesion(manual.id)) { bearerAuth(token) }
        configurar(token, false, todosLosDias(Duration.ofHours(2), Duration.ofHours(3)))
        assertEquals(EstadoEstablecimiento.FUERA_DE_HORARIO, escanear().estado)
        configurar(token, false, emptyList())
        assertEquals(EstadoEstablecimiento.SIN_SESION, escanear().estado)
    }

    @Test
    fun `una sesion que empezo en una franja ya terminada se cierra sola con la hora del horario`() = conApp {
        val token = loginAdmin()
        val sesion = abrir(token)
        transaction(bd.database) {
            Sesiones.update({ Sesiones.id eq UUID.fromString(sesion.id) }) { it[inicio] = Instant.now().minus(Duration.ofHours(3)) }
        }
        val estado = configurar(token, true, todosLosDias(Duration.ofHours(-4), Duration.ofHours(-2))).body<EstadoOperacion>()
        assertEquals(EstadoEstablecimiento.FUERA_DE_HORARIO, estado.estado)
        assertNull(activa(token))

        val fin = transaction(bd.database) { Sesiones.selectAll().where { Sesiones.id eq UUID.fromString(sesion.id) }.single()[Sesiones.fin]!! }
        val esperado = Instant.now().minus(Duration.ofHours(2))
        assertTrue(Duration.between(fin, esperado).abs() < Duration.ofMinutes(2), "fin: $fin")
        assertEquals("AUTOMATICA", eventos("SESION_CERRADA").single()[Eventos.datos]["origen"].toString().trim('"'))
    }

    @Test
    fun `el mantenimiento desactiva el QR y no deja abrir sesion ni a mano ni automaticamente`() = conApp {
        val token = loginAdmin()
        val sesion = abrir(token)
        error(mantenimiento(token, true), HttpStatusCode.Conflict, CodigosError.SESION_EN_CURSO)
        cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }
        // Esa sesión fue de una noche anterior: así no cuenta como "ya hubo sesión en esta franja"
        transaction(bd.database) {
            Sesiones.update({ Sesiones.id eq UUID.fromString(sesion.id) }) { it[inicio] = Instant.now().minus(Duration.ofHours(5)) }
        }

        assertEquals(EstadoEstablecimiento.MANTENIMIENTO, mantenimiento(token, true).body<EstadoOperacion>().estado)
        assertEquals(EstadoEstablecimiento.MANTENIMIENTO, escanear().estado)
        error(cliente.post(Rutas.SESIONES) { bearerAuth(token) }, HttpStatusCode.Conflict, CodigosError.EN_MANTENIMIENTO)

        // Dentro del horario automático tampoco abre
        configurar(token, true, todosLosDias(Duration.ofHours(-1), Duration.ofHours(1)))
        assertNull(activa(token))

        // Al terminar el mantenimiento, la programación automática abre la sesión
        val fin = mantenimiento(token, false).body<EstadoOperacion>()
        assertEquals(EstadoEstablecimiento.ABIERTO, fin.estado)
        assertNotNull(activa(token))
    }

    @Test
    fun `la limpieza solo se hace en mantenimiento y reporta lo que limpio`() = conApp {
        val token = loginAdmin()
        error(cliente.post(Rutas.LIMPIEZA) { bearerAuth(token) }, HttpStatusCode.Conflict, CodigosError.MANTENIMIENTO_INACTIVO)

        // Datos que deben limpiarse: una solicitud que quedó pendiente, una búsqueda y un bloqueo vencidos, y la rotación
        val ahora = Instant.now()
        transaction(bd.database) {
            RepositorioCanciones.guardar(listOf(video("cancion0001", "Canción uno", "Artista - Topic")), ahora)
            RepositorioCache.guardar("vieja", listOf("cancion0001"), ahora.minusSeconds(60))
            val adminId = Personal.selectAll().first()[Personal.id]
            RepositorioBloqueos.bloquear(establecimientoId(), "cancion0001", MotivoEliminacion.OTRO, adminId, ahora.minusSeconds(7200), ahora.minusSeconds(60))
            Establecimientos.update { it[posicionListaDefecto] = 3 }
        }
        val sesion = abrir(token)
        val pedida = cliente.post(Rutas.solicitudes(sesion.id)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            contentType(ContentType.Application.Json)
            setBody(NuevaSolicitud("cancion0001"))
        }
        assertEquals(HttpStatusCode.Created, pedida.status)
        cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }
        transaction(bd.database) { Solicitudes.update { it[estado] = "EN_COLA" } }

        mantenimiento(token, true)
        val r = cliente.post(Rutas.LIMPIEZA) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ResultadoLimpieza(1, 1, 1, true), r.body<ResultadoLimpieza>())
        assertEquals("NO_REPRODUCIDA", transaction(bd.database) { Solicitudes.selectAll().single()[Solicitudes.estado] })
        // Una segunda limpieza ya no encuentra nada
        assertEquals(ResultadoLimpieza(0, 0, 0, false), cliente.post(Rutas.LIMPIEZA) { bearerAuth(token) }.body<ResultadoLimpieza>())
    }

    @Test
    fun `el horario se valida y solo el administrador lo cambia`() = conApp {
        val token = loginAdmin()
        error(configurar(token, true, listOf(Horario(DiaSemana.VIERNES, "25:00", "03:00"))), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(configurar(token, true, listOf(Horario(DiaSemana.VIERNES, "20:00", "20:00"))), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(
            configurar(token, true, listOf(Horario(DiaSemana.VIERNES, "20:00", "03:00"), Horario(DiaSemana.VIERNES, "21:00", "02:00"))),
            HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS,
        )
        val guardado = configurar(token, false, listOf(Horario(DiaSemana.SABADO, "18:00", "23:30"), Horario(DiaSemana.VIERNES, "20:00", "03:00")))
            .body<EstadoOperacion>()
        assertEquals(listOf(DiaSemana.VIERNES, DiaSemana.SABADO), guardado.horarios.map { it.dia })
        assertEquals("America/Bogota", guardado.zonaHoraria)
        assertNotNull(guardado.proximaApertura)

        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        val dj = login("dj@vicecity.test", "clave-dj-123")
        assertEquals(HttpStatusCode.OK, cliente.get(Rutas.OPERACION) { bearerAuth(dj) }.status)
        error(configurar(dj, true, emptyList()), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(mantenimiento(dj, true), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.get(Rutas.OPERACION), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
    }
}