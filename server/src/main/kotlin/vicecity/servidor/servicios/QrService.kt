package vicecity.servidor.servicios

import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.dominio.EnlaceQr
import vicecity.servidor.integraciones.GeneradorQr
import vicecity.servidor.noEncontrado
import java.util.UUID

/** Código QR del establecimiento, en PNG para imprimir (RF-15). */
class QrEstablecimiento(val codigoQr: String, val enlace: String, val png: ByteArray)

class QrService(private val bd: BaseDatos, private val webUrl: String) {

    suspend fun deEstablecimiento(establecimientoId: UUID, tamano: Int): QrEstablecimiento {
        val codigo = bd.tx { RepositorioEstablecimientos.porId(establecimientoId)?.get(Establecimientos.codigoQr) }
            ?: throw noEncontrado("el establecimiento")
        val enlace = EnlaceQr.de(webUrl, codigo)
        return QrEstablecimiento(codigo, enlace, GeneradorQr.png(enlace, tamano))
    }
}