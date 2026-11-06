package vicecity.servidor.servicios

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.Modo
import vicecity.compartido.SesionPublica
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioDispositivos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.noEncontrado
import java.time.Instant
import java.util.UUID

/** Lo que pasa cuando un cliente escanea el QR del establecimiento (RF-01). */
class AccesoService(private val bd: BaseDatos) {

    /**
     * Resuelve el código del QR, registra el dispositivo y, si hay una sesión abierta,
     * registra ACCESO_SESION una sola vez por dispositivo y sesión.
     */
    suspend fun acceder(codigoQr: String, dispositivoHash: String): EstablecimientoPublico = bd.tx {
        val establecimiento = RepositorioEstablecimientos.porCodigoQr(codigoQr) ?: throw noEncontrado("el establecimiento")
        val establecimientoId = establecimiento[Establecimientos.id]
        val ahora = Instant.now()
        val nuevo = RepositorioDispositivos.registrar(dispositivoHash, ahora)

        val sesion = RepositorioEstablecimientos.sesionAbierta(establecimientoId)
        if (sesion != null) {
            val sesionId = sesion[Sesiones.id]
            EventoService.registrar(
                TipoEvento.ACCESO_SESION, establecimientoId, sesionId = sesionId, dispositivoHash = dispositivoHash,
                datos = buildJsonObject { put("nuevo", nuevo) },
                idIdempotencia = idAcceso(sesionId, dispositivoHash),
                ocurridoEn = ahora,
                soloUnaVez = true,
            )
        }

        EstablecimientoPublico(
            nombre = establecimiento[Establecimientos.nombre],
            sesionActiva = sesion?.let { SesionPublica(it[Sesiones.id].toString(), Modo.valueOf(it[Sesiones.modoActual])) },
        )
    }

    /** Mismo dispositivo y misma sesión producen siempre el mismo id: así el evento no se repite. */
    private fun idAcceso(sesionId: UUID, dispositivoHash: String): UUID =
        UUID.nameUUIDFromBytes("ACCESO_SESION:$sesionId:$dispositivoHash".toByteArray())
}