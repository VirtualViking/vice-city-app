package vicecity.servidor

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.EliminacionSolicitud
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.FinReproduccion
import vicecity.compartido.HEADER_DISPOSITIVO
import vicecity.compartido.MotivoEliminacion
import vicecity.compartido.NuevaListaDefecto
import vicecity.compartido.NuevaSolicitud
import vicecity.compartido.ReproduccionFallida
import vicecity.compartido.RespuestaSolicitud
import vicecity.compartido.Rutas
import vicecity.servidor.CatalogoFalso.Companion.video
import vicecity.servidor.analitica.Anonimizador
import vicecity.servidor.analitica.PipelineDatos
import vicecity.servidor.datos.AnCancionGeneros
import vicecity.servidor.datos.AnCanciones
import vicecity.servidor.datos.AnEjecuciones
import vicecity.servidor.datos.AnEventosRechazados
import vicecity.servidor.datos.AnParticipaciones
import vicecity.servidor.datos.AnReproducciones
import vicecity.servidor.datos.AnSales
import vicecity.servidor.datos.AnSesiones
import vicecity.servidor.datos.AnSolicitudes
import vicecity.servidor.datos.AnVotos
import vicecity.servidor.datos.Dispositivos
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.datos.Votos
import vicecity.servidor.seguridad.HuellaDispositivo
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pipeline de datos: ingesta, limpieza, transformación, anonimización, almacenamiento y retención (Entregable 6). */
class PipelineTest : PruebaIntegracion() {
    private val ana = UUID.randomUUID().toString()
    private val beto = UUID.randomUUID().toString()
    private val carla = UUID.randomUUID().toString()
    private val dave = UUID.randomUUID().toString()

    private fun guardarCanciones() = transaction(bd.database) {
        RepositorioCanciones.guardar(
            listOf(
                video("cancion0001", "Canción uno", "Artista Uno - Topic", categorias = listOf("Pop music", "Latin music")),
                video("cancion0002", "Canción dos", "Artista Dos - Topic", categorias = listOf("Reggaeton")),
                video("cancion0003", "Canción tres", "Artista Tres"),
                video("cancion0004", "Canción cuatro", "Artista Cuatro"),
                video("cancion0005", "Canción cinco", "Artista Cinco"),
                video("cancion0006", "Canción seis", "Artista Seis"),
                video("listado0001", "Clásico uno", "Clásicos - Topic"),
            ),
            Instant.now(),
        )
    }

    private fun pipeline() = PipelineDatos(bd)

    private suspend fun Contexto.entrar(dispositivo: String) =
        assertEquals(HttpStatusCode.OK, cliente.get(Rutas.establecimiento(codigoQr())) { header(HEADER_DISPOSITIVO, dispositivo) }.status)

    private suspend fun Contexto.pedir(sesionId: String, videoId: String, dispositivo: String): String {
        val r = cliente.post(Rutas.solicitudes(sesionId)) {
            header(HEADER_DISPOSITIVO, dispositivo)
            contentType(ContentType.Application.Json)
            setBody(NuevaSolicitud(videoId))
        }
        assertTrue(r.status == HttpStatusCode.Created || r.status == HttpStatusCode.OK, "pedir $videoId: ${r.status}")
        return r.body<RespuestaSolicitud>().solicitud.solicitudId
    }

    private suspend fun Contexto.accion(token: String, ruta: String): EstadoReproduccion =
        cliente.post(ruta) { bearerAuth(token) }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun Contexto.terminar(token: String, e: EstadoReproduccion): EstadoReproduccion =
        cliente.post(Rutas.REPRODUCTOR_TERMINADA) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FinReproduccion(e.actual!!.videoId, e.actual!!.inicioEn))
        }.body()

    private fun hash(dispositivo: String) = HuellaDispositivo(config.dispositivoSecreto).de(UUID.fromString(dispositivo))

    @Test
    fun `una noche completa queda limpia, anonima y consolidada en la capa analitica`() = conApp {
        guardarCanciones()
        val token = loginAdmin()
        val sesion = abrir(token)
        entrar(ana)
        entrar(beto)
        cliente.put(Rutas.LISTA_DEFECTO) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(NuevaListaDefecto(listOf("listado0001")))
        }
        val uno = pedir(sesion.id, "cancion0001", ana)
        pedir(sesion.id, "cancion0002", beto)
        assertEquals(HttpStatusCode.Created, cliente.post(Rutas.votos(uno)) { header(HEADER_DISPOSITIVO, beto) }.status)
        pedir(sesion.id, "cancion0001", carla)          // ya estaba: se convierte en voto
        pedir(sesion.id, "cancion0003", ana)

        var e = accion(token, Rutas.REPRODUCTOR_INICIAR)     // suena la 1 (3 votos)
        e = terminar(token, e)                               // suena la 2
        e = accion(token, Rutas.REPRODUCTOR_SALTAR)          // suena la 3
        e = cliente.post(Rutas.REPRODUCTOR_FALLIDA) {        // la 3 no carga: cola vacía, suena la lista
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ReproduccionFallida(e.actual!!.videoId, e.actual!!.inicioEn, 150))
        }.body()
        assertEquals("listado0001", e.actual!!.videoId)
        val cuatro = pedir(sesion.id, "cancion0004", beto)
        pedir(sesion.id, "cancion0005", carla)
        e = terminar(token, e)                               // vuelve a la cola: suena la 4
        assertEquals("cancion0004", e.actual!!.videoId)
        cliente.post(Rutas.eliminarSolicitud(cuatro)) {      // el DJ la elimina mientras suena: suena la 5
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(EliminacionSolicitud(MotivoEliminacion.INAPROPIADO, bloquear = false))
        }
        pedir(sesion.id, "cancion0006", dave)                // queda en cola al cerrar
        assertEquals(HttpStatusCode.OK, cliente.post(Rutas.cerrarSesion(sesion.id)) { bearerAuth(token) }.status)

        val ejecucion = pipeline().ejecutar("MANUAL")
        val eventosSesion = transaction(bd.database) { Eventos.selectAll().where { Eventos.sesionId eq UUID.fromString(sesion.id) }.count().toInt() }
        assertEquals(1, ejecucion.sesionesProcesadas)
        assertEquals(1, ejecucion.sesionesConsolidadas)
        assertEquals(eventosSesion, ejecucion.eventosLeidos)
        assertEquals(eventosSesion, ejecucion.eventosCargados)
        assertEquals(0, ejecucion.eventosRechazados)
        assertEquals(8, ejecucion.normalizaciones["ORIGEN_VOTO_DERIVADO"])   // 6 solicitudes + 1 voto directo + 1 solicitud repetida
        assertEquals(1, ejecucion.normalizaciones["FIN_INFERIDO"])

        transaction(bd.database) {
            val s = AnSesiones.selectAll().single()
            assertTrue(s[AnSesiones.consolidada])
            assertEquals(2, s[AnSesiones.transiciones])
            assertEquals(0, AnSales.selectAll().count())

            val participaciones = AnParticipaciones.selectAll().toList()
            assertEquals(4, participaciones.size)
            assertEquals(2, participaciones.count { it[AnParticipaciones.accedio] })

            val estados = AnSolicitudes.selectAll().associate { it[AnSolicitudes.videoId] to it[AnSolicitudes.estadoFinal] }
            assertEquals(
                mapOf(
                    "cancion0001" to "REPRODUCIDA", "cancion0002" to "REPRODUCIDA", "cancion0003" to "FALLIDA",
                    "cancion0004" to "ELIMINADA", "cancion0005" to "REPRODUCIDA", "cancion0006" to "NO_REPRODUCIDA",
                ),
                estados,
            )
            val primera = AnSolicitudes.selectAll().single { it[AnSolicitudes.videoId] == "cancion0001" }
            assertEquals(3, primera[AnSolicitudes.votos])
            assertNotNull(primera[AnSolicitudes.esperaSeg])
            assertEquals(0, primera[AnSolicitudes.solicitadaEn].atOffset(ZoneOffset.UTC).second)   // redondeada al minuto
            val origenes = AnVotos.selectAll().where { AnVotos.solicitudRef eq primera[AnSolicitudes.solicitudRef] }.map { it[AnVotos.origen] }.sorted()
            assertEquals(listOf("DIRECTO", "INICIAL", "SOLICITUD_REPETIDA"), origenes)
            assertNotNull(AnSolicitudes.selectAll().single { it[AnSolicitudes.videoId] == "cancion0006" }[AnSolicitudes.enColaSeg])

            val historial = AnReproducciones.selectAll().orderBy(AnReproducciones.orden).map {
                Triple(it[AnReproducciones.videoId], it[AnReproducciones.origen], it[AnReproducciones.resultado])
            }
            assertEquals(
                listOf(
                    Triple("cancion0001", "COLA", "TERMINADA"), Triple("cancion0002", "COLA", "SALTADA"),
                    Triple("cancion0003", "COLA", "FALLIDA"), Triple("listado0001", "LISTA_DEFECTO", "TERMINADA"),
                    Triple("cancion0004", "COLA", "ELIMINADA"), Triple("cancion0005", "COLA", "CIERRE"),
                ),
                historial,
            )
            assertTrue(AnReproducciones.selectAll().single { it[AnReproducciones.videoId] == "cancion0005" }[AnReproducciones.finInferido])

            // Dimensión canción limpia
            assertEquals("Artista Uno", AnCanciones.selectAll().single { it[AnCanciones.videoId] == "cancion0001" }[AnCanciones.artista])
            assertEquals(
                setOf("Pop", "Música latina"),
                AnCancionGeneros.selectAll().where { AnCancionGeneros.videoId eq "cancion0001" }.map { it[AnCancionGeneros.genero] }.toSet(),
            )
        }

        // Una sesión consolidada no se vuelve a procesar
        val segunda = pipeline().ejecutar("MANUAL")
        assertEquals(0, segunda.sesionesProcesadas)
    }

    @Test
    fun `la limpieza aparta los eventos invalidos con su motivo y no llegan a los hechos`() = conApp {
        guardarCanciones()
        val token = loginAdmin()
        val sesion = abrir(token)
        val sesionId = UUID.fromString(sesion.id)
        entrar(ana)
        val uno = UUID.fromString(pedir(sesion.id, "cancion0001", ana))
        val est = establecimientoId()
        val h = hash(ana)
        transaction(bd.database) {
            fun crudo(tipo: String, solicitud: UUID? = null, video: String? = null, dispositivo: String? = null,
                      cuando: Instant = Instant.now(), establecimiento: UUID = est, origen: String? = null) =
                Eventos.insert {
                    it[idIdempotencia] = UUID.randomUUID()
                    it[Eventos.tipo] = tipo
                    it[establecimientoId] = establecimiento
                    it[Eventos.sesionId] = sesionId
                    it[solicitudId] = solicitud
                    it[videoId] = video
                    it[dispositivoHash] = dispositivo
                    it[ocurridoEn] = cuando
                    it[datos] = buildJsonObject { origen?.let { o -> put("origen", o) } }
                }
            crudo("EVENTO_RARO")                                                                  // TIPO_DESCONOCIDO
            crudo("VOTO_REGISTRADO", solicitud = UUID.randomUUID(), dispositivo = h)             // SOLICITUD_DESCONOCIDA
            crudo("ACCESO_SESION")                                                                // FALTA_DISPOSITIVO
            crudo("ACCESO_SESION", dispositivo = h)                                               // DUPLICADO
            crudo("VOTO_REGISTRADO", solicitud = uno, dispositivo = h, cuando = Instant.now().plusSeconds(3600))  // FECHA_FUTURA
            crudo("REPRODUCCION_FINALIZADA", video = "cancion0002")                               // REPRODUCCION_DESCONOCIDA
            crudo("SOLICITUD_CREADA", UUID.randomUUID(), "cancion0002", h, establecimiento = UUID.randomUUID())  // ESTABLECIMIENTO_DISTINTO
            crudo("REPRODUCCION_INICIADA", video = "cancion0002")                                 // FALTA_ORIGEN
        }

        val ejecucion = pipeline().ejecutar("MANUAL")
        assertEquals(8, ejecucion.eventosRechazados)
        assertEquals(
            mapOf(
                "TIPO_DESCONOCIDO" to 1, "SOLICITUD_DESCONOCIDA" to 1, "FALTA_DISPOSITIVO" to 1, "DUPLICADO" to 1,
                "FECHA_FUTURA" to 1, "REPRODUCCION_DESCONOCIDA" to 1, "ESTABLECIMIENTO_DISTINTO" to 1, "FALTA_ORIGEN" to 1,
            ),
            ejecucion.rechazosPorMotivo,
        )
        val primerCodigo = transaction(bd.database) {
            assertEquals(8, AnEventosRechazados.selectAll().count())
            assertEquals(1, AnSolicitudes.selectAll().count())                  // solo la solicitud válida
            assertEquals(1, AnVotos.selectAll().count())                        // solo su voto inicial
            assertEquals(1, AnSales.selectAll().count())                        // sesión abierta: la clave sigue
            AnParticipaciones.selectAll().single()[AnParticipaciones.participante]
        }

        // La sesión abierta se recalcula en cada ejecución sin duplicar rechazos y con el mismo código de participante
        pedir(sesion.id, "cancion0002", ana)
        pipeline().ejecutar("MANUAL")
        transaction(bd.database) {
            assertEquals(8, AnEventosRechazados.selectAll().count())
            assertEquals(2, AnSolicitudes.selectAll().count())
            val p = AnParticipaciones.selectAll().single()
            assertEquals(primerCodigo, p[AnParticipaciones.participante])
            assertEquals(2, p[AnParticipaciones.solicitudes])
            assertEquals(false, AnSesiones.selectAll().single()[AnSesiones.consolidada])
        }
    }

    @Test
    fun `la capa analitica no guarda identificadores y el mismo dispositivo tiene codigos distintos en cada sesion`() = conApp {
        guardarCanciones()
        val token = loginAdmin()
        val primera = abrir(token)
        entrar(ana)
        val s1 = pedir(primera.id, "cancion0001", ana)
        cliente.post(Rutas.cerrarSesion(primera.id)) { bearerAuth(token) }
        val segunda = abrir(token)
        entrar(ana)
        val s2 = pedir(segunda.id, "cancion0002", ana)
        cliente.post(Rutas.cerrarSesion(segunda.id)) { bearerAuth(token) }
        pipeline().ejecutar("MANUAL")

        transaction(bd.database) {
            val hashes = Dispositivos.selectAll().map { it[Dispositivos.hash].trim() }.toSet()
            val identificadores = hashes + listOf(s1, s2) + hashes.map { it.take(Anonimizador.LARGO_CODIGO) }
            val codigos = AnParticipaciones.selectAll().map { it[AnParticipaciones.participante] } +
                    AnSolicitudes.selectAll().flatMap { listOf(it[AnSolicitudes.participante], it[AnSolicitudes.solicitudRef]) } +
                    AnVotos.selectAll().flatMap { listOf(it[AnVotos.participante], it[AnVotos.solicitudRef]) }
            assertTrue(codigos.isNotEmpty())
            assertTrue(codigos.none { it in identificadores }, "Un identificador quedó en la capa analítica")
            assertTrue(codigos.all { it.length == Anonimizador.LARGO_CODIGO })

            val porSesion = AnParticipaciones.selectAll().associate { it[AnParticipaciones.sesionId] to it[AnParticipaciones.participante] }
            assertEquals(2, porSesion.size)
            assertNotEquals(porSesion[UUID.fromString(primera.id)], porSesion[UUID.fromString(segunda.id)])
            assertEquals(0, AnSales.selectAll().count())   // ambas consolidadas: las claves se borraron
            // El primer acceso de la noche 1 es nuevo; en la noche 2 el mismo celular ya es recurrente
            assertEquals(true, AnParticipaciones.selectAll().single { it[AnParticipaciones.sesionId] == UUID.fromString(primera.id) }[AnParticipaciones.nuevo])
            assertEquals(false, AnParticipaciones.selectAll().single { it[AnParticipaciones.sesionId] == UUID.fromString(segunda.id) }[AnParticipaciones.nuevo])
        }
    }

    @Test
    fun `la retencion borra lo vencido una vez al dia y el evento sigue protegido dentro de su plazo`() = conApp {
        guardarCanciones()
        val token = loginAdmin()
        val vieja = abrir(token)
        pedir(vieja.id, "cancion0001", ana)
        cliente.post(Rutas.cerrarSesion(vieja.id)) { bearerAuth(token) }
        val actual = abrir(token)
        pedir(actual.id, "cancion0002", beto)
        // Primera ejecución: consolida la sesión cerrada; la retención aún no encuentra nada vencido
        assertEquals(ResultadoCero, pipeline().ejecutar("MANUAL").retencion)
        val est = establecimientoId()
        transaction(bd.database) {
            // Pasa un día desde esa ejecución
            AnEjecuciones.update { it[inicio] = Instant.now().minus(Duration.ofHours(25)) }
            // La primera sesión cerró hace 100 días; un dispositivo sin actividad hace 13 meses; un evento de hace 25 meses
            Sesiones.update({ Sesiones.id eq UUID.fromString(vieja.id) }) {
                it[inicio] = Instant.now().minus(Duration.ofDays(101))
                it[fin] = Instant.now().minus(Duration.ofDays(100))
            }
            Dispositivos.insert {
                it[hash] = "f".repeat(64)
                it[primeraVisita] = Instant.now().minus(Duration.ofDays(500))
                it[ultimaVisita] = Instant.now().minus(Duration.ofDays(400))
            }
            Eventos.insert {
                it[idIdempotencia] = UUID.randomUUID()
                it[tipo] = "BUSQUEDA_REALIZADA"
                it[establecimientoId] = est
                it[ocurridoEn] = Instant.now().atZone(ZoneOffset.UTC).minusMonths(25).toInstant()
                it[datos] = buildJsonObject { }
            }
        }
        // Sin el proceso de retención, ni un evento viejo se puede borrar
        assertFailsWith<Exception> {
            transaction(bd.database) { Eventos.deleteWhere { Eventos.tipo eq "BUSQUEDA_REALIZADA" } }
        }

        val ejecucion = pipeline().ejecutar("MANUAL")
        val retencion = assertNotNull(ejecucion.retencion)
        assertEquals(1, retencion.solicitudes)
        assertEquals(1, retencion.votos)
        assertEquals(1, retencion.dispositivos)
        assertEquals(1, retencion.eventos)
        transaction(bd.database) {
            assertEquals(listOf("cancion0002"), Solicitudes.selectAll().map { it[Solicitudes.videoId] })
            assertEquals(1, Votos.selectAll().count())
            assertEquals(0, Eventos.selectAll().where { Eventos.tipo eq "BUSQUEDA_REALIZADA" }.count())
            // La capa analítica de la sesión vieja se conserva: es anónima
            assertEquals(2, AnSolicitudes.selectAll().count())
        }
        // Un evento reciente sigue sin poder borrarse, aunque se active la variable de retención
        assertFailsWith<Exception> {
            transaction(bd.database) {
                exec("SET LOCAL vicecity.retencion = 'activa'")
                Eventos.deleteWhere { Eventos.tipo eq "SESION_ABIERTA" }
            }
        }
        // Una vez al día: la siguiente ejecución no la repite
        assertNull(pipeline().ejecutar("MANUAL").retencion)
    }

    @Test
    fun `sin datos el pipeline termina sin errores`() = conApp {
        val ejecucion = pipeline().ejecutar("MANUAL")
        assertEquals(0, ejecucion.sesionesProcesadas)
        assertEquals(0, ejecucion.eventosLeidos)
        assertNotNull(ejecucion.fin)
        assertEquals(ResultadoCero, ejecucion.retencion)
    }

    private companion object {
        val ResultadoCero = vicecity.compartido.ResultadoRetencion(0, 0, 0, 0)
    }
}