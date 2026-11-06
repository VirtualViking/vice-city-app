package vicecity.servidor.datos

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/** Tabla DISPOSITIVO: solo guarda el hash del identificador anónimo, nunca el original (RNF-17). */
object RepositorioDispositivos {

    /** Crea el dispositivo la primera vez que se ve; después solo actualiza su última visita. Devuelve true si es nuevo. */
    fun registrar(hash: String, ahora: Instant): Boolean {
        val nuevo = Dispositivos.insertIgnore {
            it[Dispositivos.hash] = hash
            it[primeraVisita] = ahora
            it[ultimaVisita] = ahora
        }.insertedCount > 0
        if (!nuevo) {
            Dispositivos.update({ Dispositivos.hash eq hash }) { it[ultimaVisita] = ahora }
        }
        return nuevo
    }
}