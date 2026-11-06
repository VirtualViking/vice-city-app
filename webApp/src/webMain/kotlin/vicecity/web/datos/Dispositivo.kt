package vicecity.web.datos

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identificador anónimo del celular (RNF-17): un UUID aleatorio que se crea la primera vez y se guarda en el navegador.
 * No contiene datos personales y el servidor solo guarda su hash. Cambia si el usuario borra los datos del navegador.
 */
object Dispositivo {
    @OptIn(ExperimentalUuidApi::class)
    val id: String by lazy {
        Almacen.dispositivo ?: Uuid.random().toString().also { Almacen.dispositivo = it }
    }
}