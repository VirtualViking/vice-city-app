package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import vicecity.compartido.CalidadDatos
import vicecity.compartido.CodigosError
import vicecity.compartido.EjecucionPipeline
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.OrigenReproduccion
import vicecity.compartido.ResultadoReproduccion
import vicecity.compartido.Rol
import vicecity.compartido.Rutas
import vicecity.compartido.SesionesAnaliticas
import vicecity.compartido.TableroAnalitico
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.datos.AnCancionGeneros
import vicecity.servidor.datos.AnCanciones
import vicecity.servidor.datos.AnParticipaciones
import vicecity.servidor.datos.AnReproducciones
import vicecity.servidor.datos.AnSesiones
import vicecity.servidor.datos.AnSolicitudes
import vicecity.servidor.datos.AnVotos
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.dominio.Agenda
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Indicadores del tablero, filtros, permisos y calidad de datos (RF-21 a RF-23, RF-29, RF-30). */
class AnaliticaTest : PruebaIntegracion() {

    private suspend fun Contexto.tablero(token: String, desde: String? = null, hasta: String? = null, sesion: String? = null) =
        cliente.get(Rutas.ANALITICA) {
            bearerAuth(token)
            desde?.let { parameter("desde", it) }
            hasta?.let { parameter("hasta", it) }
            sesion?.let { parameter("sesion", it) }
        }

    // ---------- Datos sintéticos de la capa analítica, con valores conocidos

    private val sesionA = UUID.randomUUID()
    private val sesionB = UUID.randomUUID()

    private fun instante(fecha: String, hora: String) = Instant.parse("${fecha}T$hora:00-05:00")

    private fun cargarHechos() = transaction(bd.database) {
        val est = establecimientoId()
        listOf(sesionA to "2026-11-20", sesionB to "2026-11-21").forEach { (id, fecha) ->
            AnSesiones.insert {
                it[sesionId] = id
                it[establecimientoId] = est
                it[inicio] = instante(fecha, "20:00")
                it[fin] = instante(fecha, "23:59")
                it[fechaLocal] = LocalDate.parse(fecha)
                it[automatica] = false
                it[duracionSeg] = 14340
                it[transiciones] = 2
                it[consolidada] = true
                it[procesadaEn] = Instant.now()
            }
        }
        listOf(
            Triple("video000001", "Canción uno", "Artista Uno"),
            Triple("video000002", "Canción dos", "Artista Uno"),
            Triple("video000003", "Canción tres", "Artista Tres"),
            Triple("video000004", "Canción cuatro", "Artista Cuatro"),
        ).forEach { (v, t, a) ->
            AnCanciones.insert {
                it[videoId] = v
                it[titulo] = t
                it[artista] = a
                it[duracionSeg] = 200
                it[actualizadaEn] = Instant.now()
            }
        }
        listOf("video000001" to "Pop", "video000001" to "Reguetón", "video000002" to "Pop").forEach { (v, g) ->
            AnCancionGeneros.insert {
                it[videoId] = v
                it[genero] = g
            }
        }
        // Sesión A: 4 solicitudes con esperas 120, 400, 700 y 1500 s; una más que no sonó
        data class S(val ref: String, val video: String, val hora: Int, val estado: String, val votos: Int, val espera: Int?, val enCola: Int?)
        listOf(
            S("a1", "video000001", 21, "REPRODUCIDA", 5, 120, null),
            S("a2", "video000002", 21, "REPRODUCIDA", 2, 400, null),
            S("a3", "video000003", 22, "REPRODUCIDA", 1, 700, null),
            S("a4", "video000004", 23, "FALLIDA", 1, 1500, null),
            S("a5", "video000003", 23, "NO_REPRODUCIDA", 4, null, 1800),
        ).forEach { s ->
            AnSolicitudes.insert {
                it[sesionId] = sesionA
                it[solicitudRef] = s.ref.padEnd(16, '0')
                it[participante] = "p1".padEnd(16, '0')
                it[videoId] = s.video
                it[solicitadaEn] = instante("2026-11-20", "${s.hora}:00")
                it[horaLocal] = s.hora.toShort()
                it[estadoFinal] = s.estado
                it[votos] = s.votos
                it[esperaSeg] = s.espera
                it[enColaSeg] = s.enCola
            }
        }
        // Votos de la sesión A: p1 da 5, otros 8 participantes dan 1 cada uno (13 votos)
        (1..5).forEach { n -> voto(sesionA, "a$n", "p1", 21) }
        (2..9).forEach { n -> voto(sesionA, "a1", "p$n", 22) }
        (1..9).forEach { n ->
            AnParticipaciones.insert {
                it[sesionId] = sesionA
                it[participante] = "p$n".padEnd(16, '0')
                it[accedio] = n <= 6
                it[nuevo] = n <= 4
                it[solicitudes] = if (n == 1) 5 else 0
                it[votos] = if (n == 1) 5 else 1
            }
        }
        // Una visita que no participó
        AnParticipaciones.insert {
            it[sesionId] = sesionA
            it[participante] = "solomira".padEnd(16, '0')
            it[accedio] = true
            it[nuevo] = true
            it[solicitudes] = 0
            it[votos] = 0
        }
        // Reproducciones: 600 s de cola y 300 s de lista
        listOf(
            Triple(1, "video000001", OrigenReproduccion.COLA) to (400 to ResultadoReproduccion.TERMINADA),
            Triple(2, "video000002", OrigenReproduccion.COLA) to (200 to ResultadoReproduccion.SALTADA),
            Triple(3, "video000003", OrigenReproduccion.LISTA_DEFECTO) to (300 to ResultadoReproduccion.CIERRE),
        ).forEach { (d, r) ->
            AnReproducciones.insert {
                it[sesionId] = sesionA
                it[orden] = d.first
                it[videoId] = d.second
                it[origen] = d.third.name
                it[inicio] = instante("2026-11-20", "21:0${d.first}")
                it[fin] = instante("2026-11-20", "21:0${d.first + 1}")
                it[segundos] = r.first
                it[resultado] = r.second.name
                it[finInferido] = r.second == ResultadoReproduccion.CIERRE
            }
        }
        // Sesión B: una solicitud de la canción 4 con 9 votos
        AnSolicitudes.insert {
            it[sesionId] = sesionB
            it[solicitudRef] = "b1".padEnd(16, '0')
            it[participante] = "q1".padEnd(16, '0')
            it[videoId] = "video000004"
            it[solicitadaEn] = instante("2026-11-21", "22:00")
            it[horaLocal] = 22
            it[estadoFinal] = "REPRODUCIDA"
            it[votos] = 9
            it[esperaSeg] = 60
        }
    }

    private fun voto(sesion: UUID, ref: String, participante: String, hora: Int) {
        AnVotos.insert {
            it[sesionId] = sesion
            it[solicitudRef] = ref.padEnd(16, '0')
            it[AnVotos.participante] = participante.padEnd(16, '0')
            it[origen] = "DIRECTO"
            it[ocurridoEn] = instante("2026-11-20", "$hora:00")
            it[horaLocal] = hora.toShort()
        }
    }

    @Test
    fun `los indicadores se calculan con las formulas del diseno`() = conApp {
        cargarHechos()
        val token = loginAdmin()
        val t: TableroAnalitico = tablero(token, sesion = sesionA.toString()).also { assertEquals(HttpStatusCode.OK, it.status) }.body()

        with(t.resumen) {
            assertEquals(1, sesiones)
            assertEquals(9, usuariosUnicos)          // la visita que no pidió ni votó no cuenta
            assertEquals(7, visitantes)
            assertEquals(5, nuevos)
            assertEquals(2, recurrentes)
            assertEquals(5, solicitudes)
            assertEquals(13, votos)
            assertEquals(680, esperaPromedioSeg)     // (120 + 400 + 700 + 1500) / 4
            assertEquals(3, reproducidas)
            assertEquals(1, noReproducidas)
            assertEquals(1, saltadas)
            assertEquals(0, fallidas)                // ninguna reproducción falló en el historial de esta sesión
        }
        // Más popular: la canción 1 tiene 5 votos; la 3 suma 1 + 4 = 5 votos pero en 2 solicitudes: gana por solicitudes
        assertEquals("video000003", t.masPopular!!.videoId)
        assertEquals(2, t.masPopular!!.solicitudes)
        assertEquals(listOf("video000003", "video000001", "video000002", "video000004"), t.ranking.canciones.map { it.videoId })
        assertEquals("Artista Uno", t.ranking.artistas.first().nombre)      // 5 + 2 votos
        assertEquals(7, t.ranking.artistas.first().votos)
        assertEquals(listOf("Pop" to 7, "Reguetón" to 5), t.ranking.generos.map { it.nombre to it.votos })

        assertEquals(24, t.franjas.size)
        assertEquals(listOf(2, 1, 2), listOf(21, 22, 23).map { h -> t.franjas[h].solicitudes })
        assertEquals(5, t.franjas[21].votos)
        assertEquals(8, t.franjas[22].votos)

        assertEquals(550, t.esperas.medianaSeg)
        assertEquals(1500, t.esperas.maximoSeg)
        assertEquals(listOf(1, 1, 1, 1), t.esperas.tramos.map { it.solicitudes })
        assertEquals(1800, t.esperas.noReproducidasPromedioSeg)

        assertEquals(600, t.listaDefecto.segundosCola)
        assertEquals(300, t.listaDefecto.segundosLista)
        assertEquals(67, t.listaDefecto.porcentajeCola)
        assertEquals(33, t.listaDefecto.porcentajeLista)
        assertEquals(2, t.listaDefecto.transiciones)

        assertEquals(listOf(3, 2, 1).map { "video00000$it" }, t.historial.map { it.videoId })     // el más reciente primero
        assertTrue(t.historial.first().finInferido)
        assertEquals("video000003", t.noReproducidas.single().videoId)

        // Concentración: 9 participantes votaron; el 10 % más activo (1) dio 5 de 13 votos
        assertEquals(9, t.concentracion.participantesQueVotaron)
        assertEquals(1, t.concentracion.participantesTop)
        assertEquals(38, t.concentracion.porcentajeVotosTop)

        // Todo el periodo: suma las dos sesiones y la canción 4 pasa a ser la más popular (1 + 9 votos)
        val periodo: TableroAnalitico = tablero(token, "2026-11-19", "2026-11-22").body()
        assertEquals(2, periodo.resumen.sesiones)
        assertEquals(6, periodo.resumen.solicitudes)
        assertEquals("video000004", periodo.masPopular!!.videoId)
        assertEquals(4, periodo.listaDefecto.transiciones)

        // Un periodo sin datos da ceros, sin errores
        val vacio: TableroAnalitico = tablero(token, "2026-01-01", "2026-01-31").body()
        assertEquals(0, vacio.resumen.solicitudes)
        assertNull(vacio.masPopular)
        assertNull(vacio.esperas.promedioSeg)
        assertEquals(0, vacio.listaDefecto.porcentajeCola)
        assertNull(vacio.concentracion.porcentajeVotosTop)

        val sesiones: SesionesAnaliticas = cliente.get(Rutas.ANALITICA_SESIONES) {
            bearerAuth(token)
            parameter("desde", "2026-11-19")
            parameter("hasta", "2026-11-22")
        }.body()
        assertEquals(listOf(sesionB, sesionA).map { it.toString() }, sesiones.sesiones.map { it.id })
        assertEquals(listOf(1, 5), sesiones.sesiones.map { it.solicitudes })
    }

    @Test
    fun `al abrir el tablero el pipeline procesa la noche y la evidencia de anonimizacion da cero`() = conApp {
        transaction(bd.database) {
            RepositorioCanciones.guardar(
                listOf(video("cancion0001", "Canción uno", "Artista Uno - Topic"), video("cancion0002", "Canción dos", "Artista Dos - Topic")),
                Instant.now(),
            )
        }
        val token = loginAdmin()
        val sesion = abrir(token)
        val ana = UUID.randomUUID().toString()
        val beto = UUID.randomUUID().toString()
        listOf(ana to "cancion0001", beto to "cancion0002").forEach { (d, v) ->
            cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, d) }
            cliente.post(Rutas.solicitudes(sesion.id)) {
                header(HEADER_DISPOSITIVO, d)
                contentType(ContentType.Application.Json)
                setBody(NuevaSolicitud(v))
            }
        }
        val e: EstadoReproduccion = cliente.post(Rutas.REPRODUCTOR_INICIAR) { bearerAuth(token) }.body()
        assertEquals("cancion0001", e.actual!!.videoId)

        // Sesión abierta: el tablero ejecuta el pipeline solo y muestra lo que suena
        val enCurso: TableroAnalitico = tablero(token, sesion = sesion.id).body()
        assertEquals(2, enCurso.resumen.solicitudes)
        assertEquals(2, enCurso.resumen.usuariosUnicos)
        assertEquals(ResultadoReproduccion.SONANDO, enCurso.historial.single().resultado)
        assertNotNull(enCurso.datosHasta)
        var calidad: CalidadDatos = cliente.get(Rutas.ANALITICA_CALIDAD) { bearerAuth(token) }.body()
        assertEquals("TABLERO", calidad.ejecuciones.first().origen)
        assertEquals(1, calidad.sesionesEnCurso)
        assertEquals(1, calidad.clavesTemporales)
        assertEquals(0, calidad.identificadoresEnCapaAnalitica)

        cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }
        val manual: EjecucionPipeline = cliente.post(Rutas.ANALITICA_PIPELINE) { bearerAuth(token) }.body()
        assertEquals("MANUAL", manual.origen)
        assertEquals(1, manual.sesionesConsolidadas)

        val cerrada: TableroAnalitico = tablero(token, sesion = sesion.id).body()
        assertEquals(ResultadoReproduccion.CIERRE, cerrada.historial.single().resultado)
        assertEquals(1, cerrada.resumen.reproducidas)
        assertEquals(1, cerrada.resumen.noReproducidas)
        assertEquals("cancion0002", cerrada.noReproducidas.single().videoId)
        assertEquals(Instant.now().atZone(Agenda.ZONA_COLOMBIA).toLocalDate().toString(), cerrada.hasta)

        calidad = cliente.get(Rutas.ANALITICA_CALIDAD) { bearerAuth(token) }.body()
        assertEquals(1, calidad.sesionesConsolidadas)
        assertEquals(0, calidad.sesionesEnCurso)
        assertEquals(0, calidad.clavesTemporales)                // clave borrada: anonimización irreversible
        assertEquals(0, calidad.identificadoresEnCapaAnalitica)
        assertEquals(0, calidad.eventosRechazados)
    }

    @Test
    fun `la evidencia detecta un identificador que llegara a la capa analitica`() = conApp {
        cargarHechos()
        val token = loginAdmin()
        abrir(token).also { s ->
            cliente.post(Rutas.solicitudes(s.id)) {
                header(HEADER_DISPOSITIVO, UUID.randomUUID().toString())
                contentType(ContentType.Application.Json)
                setBody(NuevaSolicitud("video000001"))
            }
        }
        // Se fuerza un hash real en la capa analítica: la verificación debe encontrarlo
        transaction(bd.database) {
            val hash = exec("SELECT trim(hash) FROM dispositivo LIMIT 1") { rs -> rs.next(); rs.getString(1) }!!
            AnParticipaciones.insert {
                it[sesionId] = sesionA
                it[participante] = hash.take(16)
                it[accedio] = false
                it[nuevo] = false
                it[solicitudes] = 0
                it[votos] = 0
            }
        }
        val calidad: CalidadDatos = cliente.get(Rutas.ANALITICA_CALIDAD) { bearerAuth(token) }.body()
        assertEquals(1, calidad.identificadoresEnCapaAnalitica)
    }

    @Test
    fun `los filtros se validan y solo el administrador ve la analitica`() = conApp {
        cargarHechos()
        val token = loginAdmin()
        error(tablero(token, "20-11-2026"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(tablero(token, "2026-11-22", "2026-11-20"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(tablero(token, sesion = "no-es-un-id"), HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS)
        error(tablero(token, sesion = UUID.randomUUID().toString()), HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO)

        crearPersonal("dj@vicecity.test", "clave-dj-123", Rol.DJ)
        val dj = login("dj@vicecity.test", "clave-dj-123")
        error(tablero(dj), HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.get(Rutas.ANALITICA_SESIONES) { bearerAuth(dj) }, HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.post(Rutas.ANALITICA_PIPELINE) { bearerAuth(dj) }, HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.get(Rutas.ANALITICA_CALIDAD) { bearerAuth(dj) }, HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO)
        error(cliente.get(Rutas.ANALITICA), HttpStatusCode.Unauthorized, CodigosError.NO_AUTENTICADO)
    }
}