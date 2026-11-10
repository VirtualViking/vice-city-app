package vicecity.servidor.servicios

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.EstadoCola
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.EstadoSesion
import vicecity.compartido.EstadoSolicitud
import vicecity.compartido.MiActividad
import vicecity.compartido.MiSolicitud
import vicecity.compartido.Modo
import vicecity.compartido.RespuestaSolicitud
import vicecity.compartido.RespuestaVoto
import vicecity.compartido.ResultadoSolicitud
import vicecity.servidor.ApiExcepcion
import vicecity.servidor.BaseDatos
import vicecity.servidor.cancionReciente
import vicecity.servidor.datos.Canciones
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Eventos
import vicecity.servidor.datos.RepositorioCola
import vicecity.servidor.datos.RepositorioDispositivos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.limiteSolicitudes
import vicecity.servidor.limiteVotos
import vicecity.servidor.noEncontrado
import vicecity.servidor.sesionCerrada
import vicecity.servidor.solicitudNoActiva
import vicecity.servidor.videoNoValido
import vicecity.servidor.yaVotado
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Solicitudes, votos y cola de una sesión (RF-05 a RF-09, RF-26, RF-27).
 * - Cada dispositivo pide hasta limite_solicitudes canciones cada ventana_limite_min minutos.
 * - Al pedir una canción su primer voto se crea solo y no cuenta para el límite de votos.
 * - Además puede dar [maxVotos] votos en la sesión a canciones que pidieron otros. Pedir una canción
 *   que ya está en la cola cuenta como uno de esos votos. Los votos no se pueden retirar.
 *
 * Las operaciones de un mismo dispositivo en una sesión se ejecutan de a una (bloqueo de PostgreSQL),
 * así dos toques seguidos no pueden saltarse los límites.
 */
class ColaService(private val bd: BaseDatos, private val maxVotos: Int) {

    suspend fun estado(sesionId: UUID): EstadoCola = bd.tx {
        val sesion = sesionAbierta(sesionId)
        EstadoCola(
            sesionId = sesionId.toString(),
            modo = Modo.valueOf(sesion[Sesiones.modoActual]),
            estadoReproductor = EstadoReproductor.valueOf(sesion[Sesiones.estadoReproductor]),
            actual = RepositorioCola.actual(sesionId),
            cola = RepositorioCola.cola(sesionId),
            actualizadoEn = Instant.now().toString(),
        )
    }

    suspend fun miActividad(sesionId: UUID, dispositivoHash: String): MiActividad = bd.tx {
        val sesion = sesionAbierta(sesionId)
        val reglas = reglasDe(sesion)
        val ahora = Instant.now()
        val posiciones = RepositorioCola.cola(sesionId).withIndex().associate { (i, item) -> item.solicitudId to i + 1 }
        val recientes = RepositorioCola.solicitadasDesde(sesionId, dispositivoHash, ahora - reglas.ventana)
        val restantes = (reglas.limite - recientes.size).coerceAtLeast(0)

        MiActividad(
            solicitudes = RepositorioCola.solicitudesDe(sesionId, dispositivoHash).map { fila ->
                val id = fila[Solicitudes.id].toString()
                MiSolicitud(
                    solicitudId = id,
                    videoId = fila[Solicitudes.videoId],
                    titulo = tituloDe(fila[Solicitudes.videoId]),
                    estado = EstadoSolicitud.valueOf(fila[Solicitudes.estado]),
                    posicion = posiciones[id],
                    solicitadaEn = fila[Solicitudes.solicitadaEn].toString(),
                )
            },
            votos = RepositorioCola.votadasPor(sesionId, dispositivoHash).map { it.toString() },
            solicitudesRestantes = restantes,
            limiteSolicitudes = reglas.limite,
            ventanaLimiteMin = reglas.ventanaMin,
            proximaSolicitudEn = if (restantes == 0) (recientes.first() + reglas.ventana).toString() else null,
            votosRestantes = (maxVotos - RepositorioCola.votosContados(sesionId, dispositivoHash)).coerceAtLeast(0),
            limiteVotos = maxVotos,
        )
    }

    /**
     * Pide [videoId]. Si ya estaba en la cola, la solicitud se convierte en un voto para ella (VOTO_REGISTRADO).
     * Cada rechazo de negocio queda registrado como SOLICITUD_RECHAZADA con su motivo.
     */
    suspend fun solicitar(sesionId: UUID, videoId: String, dispositivoHash: String, claveIdempotencia: String?): RespuestaSolicitud =
        bd.tx {
            val sesion = sesionAbierta(sesionId)
            val establecimientoId = sesion[Sesiones.establecimientoId]
            bloquear(sesionId, dispositivoHash)
            val idEvento = idEvento("SOLICITUD", dispositivoHash, claveIdempotencia)
            repetida(idEvento)?.let { anterior ->
                val resultado = if (anterior[Eventos.tipo] == TipoEvento.SOLICITUD_CREADA.name) ResultadoSolicitud.SOLICITUD_CREADA else ResultadoSolicitud.VOTO_REGISTRADO
                return@tx Result.success(RespuestaSolicitud(resultado, RepositorioCola.item(anterior[Eventos.solicitudId]!!)))
            }

            val ahora = Instant.now()
            RepositorioDispositivos.registrar(dispositivoHash, ahora)
            try {
                Result.success(nuevaSolicitud(sesion, videoId, dispositivoHash, idEvento, ahora))
            } catch (e: ApiExcepcion) {
                EventoService.registrar(
                    TipoEvento.SOLICITUD_RECHAZADA, establecimientoId, sesionId = sesionId, videoId = videoId,
                    dispositivoHash = dispositivoHash,
                    datos = buildJsonObject {
                        put("motivo", e.codigo)
                        e.detalles.forEach { (clave, valor) -> put(clave, valor) }
                    },
                    ocurridoEn = ahora,
                )
                // El rechazo se devuelve sin lanzarlo para que la transacción guarde el evento
                Result.failure(e)
            }
        }.getOrThrow()

    /** Vota por una canción de la cola. */
    suspend fun votar(solicitudId: UUID, dispositivoHash: String, claveIdempotencia: String?): RespuestaVoto = bd.tx {
        val solicitud = RepositorioCola.solicitud(solicitudId) ?: throw noEncontrado("la canción")
        val sesionId = solicitud[Solicitudes.sesionId]
        val sesion = sesionAbierta(sesionId)
        bloquear(sesionId, dispositivoHash)
        val idEvento = idEvento("VOTO", dispositivoHash, claveIdempotencia)
        if (repetida(idEvento) != null) return@tx RespuestaVoto(solicitudId.toString(), RepositorioCola.votosDe(solicitudId))

        val ahora = Instant.now()
        RepositorioDispositivos.registrar(dispositivoHash, ahora)
        val estado = RepositorioCola.solicitud(solicitudId)!![Solicitudes.estado]
        if (estado != EstadoSolicitud.EN_COLA.name) throw solicitudNoActiva(estado)
        val votos = votarEnTx(sesion, solicitud, dispositivoHash, idEvento, ahora, desdeSolicitud = false)
        RespuestaVoto(solicitudId.toString(), votos)
    }

    private fun JdbcTransaction.nuevaSolicitud(
        sesion: ResultRow, videoId: String, dispositivoHash: String, idEvento: UUID, ahora: Instant,
    ): RespuestaSolicitud {
        val sesionId = sesion[Sesiones.id]
        val reglas = reglasDe(sesion)
        val cancion = Canciones.selectAll().where { Canciones.videoId eq videoId }.singleOrNull()
            ?: throw videoNoValido(videoId, "NO_DISPONIBLE")
        if (cancion[Canciones.duracionSeg] > reglas.duracionMaxSeg) throw videoNoValido(videoId, "DURACION_EXCEDIDA")

        // Ya está en la cola: la solicitud se convierte en un voto
        RepositorioCola.activaDeVideo(sesionId, videoId)?.let { activa ->
            return siYaEsta(sesion, activa, cancion, dispositivoHash, idEvento, ahora, reglas)
        }

        // RF-27: anti-repetición
        RepositorioCola.ultimaReproduccion(sesionId, videoId)?.let { fin ->
            val disponibleEn = fin + reglas.antirepeticion
            if (disponibleEn > ahora) throw cancionReciente(disponibleEn)
        }

        // RF-09: límite de solicitudes por ventana de tiempo
        val recientes = RepositorioCola.solicitadasDesde(sesionId, dispositivoHash, ahora - reglas.ventana)
        if (recientes.size >= reglas.limite) {
            throw limiteSolicitudes(reglas.limite, reglas.ventanaMin, recientes.first() + reglas.ventana)
        }

        val solicitudId = UUID.randomUUID()
        if (!RepositorioCola.crearSolicitud(solicitudId, sesionId, videoId, dispositivoHash, ahora)) {
            // Otro dispositivo la puso en la cola en este mismo instante
            val activa = RepositorioCola.activaDeVideo(sesionId, videoId) ?: error("La solicitud activa de $videoId desapareció")
            return siYaEsta(sesion, activa, cancion, dispositivoHash, idEvento, ahora, reglas)
        }
        RepositorioCola.crearVoto(solicitudId, dispositivoHash, ahora)

        val establecimientoId = sesion[Sesiones.establecimientoId]
        val item = RepositorioCola.item(solicitudId)
        EventoService.registrar(
            TipoEvento.SOLICITUD_CREADA, establecimientoId, sesionId = sesionId, solicitudId = solicitudId, videoId = videoId,
            dispositivoHash = dispositivoHash,
            datos = buildJsonObject { put("posicion", RepositorioCola.cola(sesionId).indexOfFirst { it.solicitudId == item.solicitudId } + 1) },
            idIdempotencia = idEvento, ocurridoEn = ahora,
        )
        // El primer voto, el del propio dispositivo
        EventoService.registrar(
            TipoEvento.VOTO_REGISTRADO, establecimientoId, sesionId = sesionId, solicitudId = solicitudId, videoId = videoId,
            dispositivoHash = dispositivoHash,
            datos = buildJsonObject {
                put("propio", true)
                put("votos", item.votos)
            },
            ocurridoEn = ahora,
        )
        return RespuestaSolicitud(ResultadoSolicitud.SOLICITUD_CREADA, item)
    }

    /** La canción pedida ya está en la cola o sonando. */
    private fun JdbcTransaction.siYaEsta(
        sesion: ResultRow, activa: ResultRow, cancion: ResultRow, dispositivoHash: String, idEvento: UUID, ahora: Instant, reglas: Reglas,
    ): RespuestaSolicitud {
        if (activa[Solicitudes.estado] == EstadoSolicitud.REPRODUCIENDO.name) {
            val inicio = activa[Solicitudes.inicioReproduccion] ?: ahora
            throw cancionReciente(inicio + Duration.ofSeconds(cancion[Canciones.duracionSeg].toLong()) + reglas.antirepeticion)
        }
        votarEnTx(sesion, activa, dispositivoHash, idEvento, ahora, desdeSolicitud = true)
        return RespuestaSolicitud(ResultadoSolicitud.VOTO_REGISTRADO, RepositorioCola.item(activa[Solicitudes.id]))
    }

    /** Registra el voto respetando el límite de la sesión y devuelve el total de votos de la canción. */
    private fun JdbcTransaction.votarEnTx(
        sesion: ResultRow, solicitud: ResultRow, dispositivoHash: String, idEvento: UUID, ahora: Instant, desdeSolicitud: Boolean,
    ): Int {
        val sesionId = sesion[Sesiones.id]
        val solicitudId = solicitud[Solicitudes.id]
        if (RepositorioCola.yaVoto(solicitudId, dispositivoHash)) throw yaVotado(solicitudId)
        if (RepositorioCola.votosContados(sesionId, dispositivoHash) >= maxVotos) throw limiteVotos(maxVotos)
        if (!RepositorioCola.crearVoto(solicitudId, dispositivoHash, ahora)) throw yaVotado(solicitudId)

        val votos = RepositorioCola.votosDe(solicitudId)
        EventoService.registrar(
            TipoEvento.VOTO_REGISTRADO, sesion[Sesiones.establecimientoId], sesionId = sesionId, solicitudId = solicitudId,
            videoId = solicitud[Solicitudes.videoId], dispositivoHash = dispositivoHash,
            datos = buildJsonObject {
                put("votos", votos)
                if (desdeSolicitud) put("desde_solicitud", true)
            },
            idIdempotencia = idEvento, ocurridoEn = ahora,
        )
        return votos
    }

    // ---------- Apoyo

    private class Reglas(val limite: Int, val ventanaMin: Int, antirepeticionMin: Int, val duracionMaxSeg: Int) {
        val ventana: Duration = Duration.ofMinutes(ventanaMin.toLong())
        val antirepeticion: Duration = Duration.ofMinutes(antirepeticionMin.toLong())
    }

    private fun reglasDe(sesion: ResultRow): Reglas {
        val e = RepositorioEstablecimientos.porId(sesion[Sesiones.establecimientoId])!!
        return Reglas(e[Establecimientos.limiteSolicitudes], e[Establecimientos.ventanaLimiteMin], e[Establecimientos.antirepeticionMin], e[Establecimientos.duracionMaxSeg])
    }

    /** Una sesión inexistente responde 404 y una cerrada 409 SESION_CERRADA: el cliente vuelve a consultar el establecimiento. */
    private fun sesionAbierta(sesionId: UUID): ResultRow {
        val sesion = Sesiones.selectAll().where { Sesiones.id eq sesionId }.singleOrNull() ?: throw noEncontrado("la sesión")
        if (sesion[Sesiones.estado] != EstadoSesion.ABIERTA.name) throw sesionCerrada()
        return sesion
    }

    private fun tituloDe(videoId: String): String =
        Canciones.select(Canciones.titulo).where { Canciones.videoId eq videoId }.single()[Canciones.titulo]

    /** Bloqueo que dura lo que la transacción: las peticiones del mismo dispositivo en la sesión esperan su turno. */
    private fun JdbcTransaction.bloquear(sesionId: UUID, dispositivoHash: String) {
        val clave = UUID.nameUUIDFromBytes("BLOQUEO:$sesionId:$dispositivoHash".toByteArray()).mostSignificantBits
        exec("SELECT pg_advisory_xact_lock($clave)")
    }

    /**
     * Id del evento principal de la petición (RNF-18). Con Idempotency-Key, el mismo dispositivo y la misma clave
     * dan siempre el mismo id: si el evento ya existe, la petición es un reintento y se responde lo mismo.
     */
    private fun idEvento(operacion: String, dispositivoHash: String, clave: String?): UUID =
        if (clave == null) UUID.randomUUID() else UUID.nameUUIDFromBytes("$operacion:$dispositivoHash:$clave".toByteArray())

    private fun repetida(idEvento: UUID): ResultRow? =
        Eventos.selectAll().where { Eventos.idIdempotencia eq idEvento }.singleOrNull()
}