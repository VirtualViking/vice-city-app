package vicecity.servidor.servicios

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.ListaDefecto
import vicecity.compartido.NuevaListaDefecto
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.Canciones
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.RepositorioListaDefecto
import vicecity.servidor.datos.Sesiones
import vicecity.servidor.datosInvalidos
import vicecity.servidor.servicios.Reproduccion.siEstaLibre
import vicecity.servidor.seguridad.UsuarioPersonal
import vicecity.servidor.videoNoValido
import java.time.Instant
import java.util.UUID

/** Lista por defecto del establecimiento (RF-18, HU-11): lo que suena cuando no hay solicitudes. */
class ListaDefectoService(private val bd: BaseDatos) {

    suspend fun obtener(usuario: UsuarioPersonal): ListaDefecto = bd.tx { listaDe(usuario.establecimientoId) }

    /**
     * Reemplaza la lista completa. Cada canción debe haber salido en una búsqueda y no superar la duración máxima.
     * El nuevo orden se usa desde la siguiente canción: si suena una canción de la lista, la rotación sigue desde ella;
     * si no, empieza por la primera.
     */
    suspend fun guardar(usuario: UsuarioPersonal, nueva: NuevaListaDefecto): ListaDefecto = bd.tx {
        val videoIds = nueva.videoIds.map { it.trim() }
        if (videoIds.size > MAXIMO) throw datosInvalidos("videoIds", "La lista puede tener hasta $MAXIMO canciones")
        if (videoIds.any { !FORMATO_VIDEO.matches(it) }) throw datosInvalidos("videoIds", "Hay un identificador de video que no es válido")
        if (videoIds.toSet().size != videoIds.size) throw datosInvalidos("videoIds", "Una canción está dos veces en la lista")

        val establecimientoId = usuario.establecimientoId
        val duracionMax = RepositorioEstablecimientos.porId(establecimientoId)!![Establecimientos.duracionMaxSeg]
        val duraciones = Canciones.selectAll().where { Canciones.videoId inList videoIds }
            .associate { it[Canciones.videoId] to it[Canciones.duracionSeg] }
        videoIds.forEach { videoId ->
            val duracion = duraciones[videoId] ?: throw videoNoValido(videoId, "NO_DISPONIBLE")
            if (duracion > duracionMax) throw videoNoValido(videoId, "DURACION_EXCEDIDA")
        }

        RepositorioListaDefecto.reemplazar(establecimientoId, videoIds)
        val sesion = RepositorioEstablecimientos.sesionAbierta(establecimientoId)
        val sonando = sesion?.get(Sesiones.videoActual)
        val posicion = sonando?.let { videoIds.indexOf(it) }?.takeIf { it >= 0 }?.plus(1)
        Establecimientos.update({ Establecimientos.id eq establecimientoId }) { it[posicionListaDefecto] = posicion }
        if (sesion != null) {
            val sesionId = sesion[Sesiones.id]
            Sesiones.update({ Sesiones.id eq sesionId }) { it[posicionListaDefecto] = posicion }
            // Si el reproductor estaba encendido en silencio, ya hay algo que poner
            siEstaLibre(sesionId, Instant.now())
        }
        listaDe(establecimientoId)
    }

    private fun listaDe(establecimientoId: UUID) =
        ListaDefecto(RepositorioListaDefecto.canciones(establecimientoId).map(RepositorioListaDefecto::aItem))

    private companion object {
        const val MAXIMO = 200
        val FORMATO_VIDEO = Regex("^[A-Za-z0-9_-]{11}$")
    }
}
