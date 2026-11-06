package vicecity.servidor.servicios

import vicecity.compartido.RespuestaSugerencias
import vicecity.compartido.Sugerencia
import vicecity.compartido.TipoSugerencia
import vicecity.servidor.BaseDatos
import vicecity.servidor.ReglasBusqueda
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioCanciones
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datos.RepositorioTerminos
import vicecity.servidor.dominio.Textos
import vicecity.servidor.noEncontrado

/**
 * Autocompletado de la caja de búsqueda con datos propios: nunca llama a YouTube.
 * Orden: lo que más se ha buscado en el establecimiento, después artistas y después canciones ya vistas.
 */
class SugerenciaService(private val bd: BaseDatos, private val reglas: ReglasBusqueda) {

    suspend fun sugerir(codigoQr: String, texto: String): RespuestaSugerencias {
        val prefijo = Textos.recortar(Textos.normalizar(texto), LARGO_MAXIMO)
        return bd.tx {
            val establecimiento = RepositorioEstablecimientos.porCodigoQr(codigoQr) ?: throw noEncontrado("el establecimiento")
            if (prefijo.length < LARGO_MINIMO) return@tx RespuestaSugerencias(emptyList())

            val maximo = reglas.maxSugerencias
            val candidatas =
                RepositorioTerminos.sugerir(establecimiento[Establecimientos.id], prefijo, maximo / 2)
                    .map { Sugerencia(it, TipoSugerencia.BUSQUEDA) } +
                        RepositorioCanciones.sugerirArtistas(prefijo, MAX_ARTISTAS).map { Sugerencia(it, TipoSugerencia.ARTISTA) } +
                        RepositorioCanciones.sugerirTitulos(prefijo, maximo).map { Sugerencia(it, TipoSugerencia.CANCION) }

            // Sin repetidos aunque cambien mayúsculas o tildes ("Karol G" y "KAROL G")
            RespuestaSugerencias(candidatas.distinctBy { Textos.normalizar(it.texto) }.take(maximo))
        }
    }

    private companion object {
        const val LARGO_MINIMO = 2
        const val LARGO_MAXIMO = 100
        const val MAX_ARTISTAS = 3
    }
}