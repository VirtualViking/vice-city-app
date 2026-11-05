package vicecity.compartido

import kotlinx.serialization.Serializable

/** Video musical encontrado en YouTube, listo para mostrar (RF-04). */
@Serializable
data class ResultadoBusqueda(
    val videoId: String,
    val titulo: String,
    val artista: String,           // deducido del canal o del título: YouTube no tiene un campo "artista"
    val canal: String,
    val miniaturaUrl: String,
    val duracionSeg: Int,
)

/** De dónde salieron los resultados. LOCAL: YouTube no estaba disponible y se buscó en las canciones ya guardadas. */
@Serializable
enum class FuenteBusqueda { YOUTUBE, CACHE, LOCAL }

/** Respuesta de GET /api/v1/establecimientos/{codigoQr}/busqueda?q= */
@Serializable
data class RespuestaBusqueda(
    val resultados: List<ResultadoBusqueda>,
    val fuente: FuenteBusqueda,
)

@Serializable
enum class TipoSugerencia { BUSQUEDA, ARTISTA, CANCION }

/** Texto que se propone mientras el cliente escribe; al elegirlo se busca ese texto. */
@Serializable
data class Sugerencia(
    val texto: String,
    val tipo: TipoSugerencia,
)

/** Respuesta de GET /api/v1/establecimientos/{codigoQr}/sugerencias?q= (autocompletado con datos propios) */
@Serializable
data class RespuestaSugerencias(
    val sugerencias: List<Sugerencia>,
)