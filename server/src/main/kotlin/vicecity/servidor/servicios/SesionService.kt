package vicecity.servidor.servicios

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.postgresql.util.PSQLException
import vicecity.compartido.CierreSesion
import vicecity.compartido.CodigosError
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.EstadoSesion
import vicecity.compartido.Modo
import vicecity.compartido.SesionDto
import vicecity.servidor.BaseDatos
import vicecity.servidor.conflicto
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datos.Solicitudes
import vicecity.servidor.datos.Votos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.UsuarioPersonal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Apertura, cierre y consulta de las sesiones de solicitudes (RF-16, HU-03). */
class SesionService(private val bd: BaseDatos) {

    suspend fun abrir(usuario: UsuarioPersonal): SesionDto = try {
        bd.tx {
            val abierta = RepositorioEstablecimientos.sesionAbierta(usuario.establecimientoId)
            if (abierta != null) {
                throw conflicto(CodigosError.SESION_YA_ABIERTA, "Ya hay una sesión abierta", mapOf("sesionId" to abierta[Sesiones.id].toString()))
            }
            val id = UUID.randomUUID()
            val ahora = Instant.now()
            Sesiones.insert {
                it[Sesiones.id] = id
                it[establecimientoId] = usuario.establecimientoId
                it[estado] = EstadoSesion.ABIERTA.name
                it[modoActual] = Modo.COLA.name
                it[estadoReproductor] = EstadoReproductor.DETENIDO.name
                it[inicio] = ahora
            }
            EventoService.registrar(
                TipoEvento.SESION_ABIERTA, usuario.establecimientoId, sesionId = id,
                datos = buildJsonObject { put("personal_id", usuario.personalId.toString()) },
                ocurridoEn = ahora,
            )
            aDto(Sesiones.selectAll().where { Sesiones.id eq id }.single())
        }
    } catch (e: Exception) {
        // Dos aperturas simultáneas: el índice único ux_sesion_abierta rechaza la segunda
        if (e.causa<PSQLException>()?.sqlState == "23505") {
            throw conflicto(CodigosError.SESION_YA_ABIERTA, "Ya hay una sesión abierta")
        }
        throw e
    }

    suspend fun cerrar(usuario: UsuarioPersonal, sesionId: UUID): CierreSesion = bd.tx {
        val sesion = buscar(usuario.establecimientoId, sesionId)
        if (sesion[Sesiones.estado] == EstadoSesion.CERRADA.name) {
            throw conflicto(CodigosError.SESION_CERRADA, "La sesión ya está cerrada")
        }
        val ahora = Instant.now()

        // Lo que sonaba al cerrar se da por reproducido
        Solicitudes.update({ (Solicitudes.sesionId eq sesionId) and (Solicitudes.estado eq "REPRODUCIENDO") }) {
            it[estado] = "REPRODUCIDA"
            it[finReproduccion] = ahora
        }

        // Lo que seguía en cola queda como no reproducido (base del diagnóstico D1, D2 y D7)
        val enCola = Solicitudes.selectAll()
            .where { (Solicitudes.sesionId eq sesionId) and (Solicitudes.estado eq "EN_COLA") }
            .toList()
        enCola.forEach { s ->
            val solicitudId = s[Solicitudes.id]
            val votos = Votos.selectAll().where { Votos.solicitudId eq solicitudId }.count()
            Solicitudes.update({ Solicitudes.id eq solicitudId }) { it[estado] = "NO_REPRODUCIDA" }
            EventoService.registrar(
                TipoEvento.SOLICITUD_NO_REPRODUCIDA, usuario.establecimientoId, sesionId = sesionId,
                solicitudId = solicitudId, videoId = s[Solicitudes.videoId],
                datos = buildJsonObject {
                    put("votos", votos)
                    put("espera_seg", Duration.between(s[Solicitudes.solicitadaEn], ahora).seconds)
                },
                ocurridoEn = ahora,
            )
        }

        Sesiones.update({ Sesiones.id eq sesionId }) {
            it[estado] = EstadoSesion.CERRADA.name
            it[fin] = ahora
            it[estadoReproductor] = EstadoReproductor.DETENIDO.name
        }
        EventoService.registrar(
            TipoEvento.SESION_CERRADA, usuario.establecimientoId, sesionId = sesionId,
            datos = buildJsonObject {
                put("duracion_seg", Duration.between(sesion[Sesiones.inicio], ahora).seconds)
                put("no_reproducidas", enCola.size)
                put("personal_id", usuario.personalId.toString())
            },
            ocurridoEn = ahora,
        )
        CierreSesion(aDto(buscar(usuario.establecimientoId, sesionId)), enCola.size)
    }

    /** Sesión abierta del establecimiento del usuario, o null. */
    suspend fun activa(usuario: UsuarioPersonal): SesionDto? = bd.tx {
        RepositorioEstablecimientos.sesionAbierta(usuario.establecimientoId)?.let(::aDto)
    }

    /** Sesiones del establecimiento, más recientes primero. [desde] y [hasta] filtran por inicio. */
    suspend fun listar(usuario: UsuarioPersonal, desde: Instant?, hasta: Instant?): List<SesionDto> = bd.tx {
        Sesiones.selectAll()
            .where {
                var condicion = Sesiones.establecimientoId eq usuario.establecimientoId
                if (desde != null) condicion = condicion and (Sesiones.inicio greaterEq desde)
                if (hasta != null) condicion = condicion and (Sesiones.inicio less hasta)
                condicion
            }
            .orderBy(Sesiones.inicio, SortOrder.DESC)
            .limit(100)
            .map(::aDto)
    }

    /** Una sesión de otro establecimiento se trata como inexistente. */
    private fun buscar(establecimientoId: UUID, sesionId: UUID): ResultRow =
        Sesiones.selectAll()
            .where { (Sesiones.id eq sesionId) and (Sesiones.establecimientoId eq establecimientoId) }
            .singleOrNull() ?: throw noEncontrado("la sesión")

    private fun aDto(fila: ResultRow) = SesionDto(
        id = fila[Sesiones.id].toString(),
        estado = EstadoSesion.valueOf(fila[Sesiones.estado]),
        modo = Modo.valueOf(fila[Sesiones.modoActual]),
        estadoReproductor = EstadoReproductor.valueOf(fila[Sesiones.estadoReproductor]),
        inicio = fila[Sesiones.inicio].toString(),
        fin = fila[Sesiones.fin]?.toString(),
    )
}

/** Busca en la cadena de causas una excepción del tipo indicado. */
inline fun <reified T : Throwable> Throwable.causa(): T? =
    generateSequence(this) { it.cause }.filterIsInstance<T>().firstOrNull()