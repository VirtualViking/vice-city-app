package vicecity.servidor.servicios

import vicecity.compartido.ListaBloqueos
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.RepositorioBloqueos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.UsuarioPersonal
import java.time.Instant
import java.util.UUID

/** Canciones bloqueadas por contenido inapropiado. Se bloquean al eliminarlas de la cola (ReproductorService.eliminar). */
class BloqueoService(private val bd: BaseDatos) {

    suspend fun listar(usuario: UsuarioPersonal): ListaBloqueos = bd.tx { listaDe(usuario.establecimientoId) }

    /** Quita el bloqueo antes de tiempo: la canción se puede volver a pedir. */
    suspend fun desbloquear(usuario: UsuarioPersonal, videoId: String): ListaBloqueos = bd.tx {
        if (!RepositorioBloqueos.desbloquear(usuario.establecimientoId, videoId)) throw noEncontrado("el bloqueo")
        listaDe(usuario.establecimientoId)
    }

    private fun listaDe(establecimientoId: UUID) =
        ListaBloqueos(RepositorioBloqueos.vigentes(establecimientoId, Instant.now()).map(RepositorioBloqueos::aBloqueo))
}