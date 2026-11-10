package vicecity.compartido

import kotlinx.serialization.Serializable

/** Cuerpo de todos los errores de la API (contrato, sección "Códigos de error"). */
@Serializable
data class ErrorApi(
    val codigo: String,
    val mensaje: String,
    val detalles: Map<String, String> = emptyMap(),
)

/** Códigos estables que el frontend usa para decidir qué mostrar. */
object CodigosError {
    const val DATOS_INVALIDOS = "DATOS_INVALIDOS"
    const val NO_AUTENTICADO = "NO_AUTENTICADO"
    const val CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS"
    const val SIN_PERMISO = "SIN_PERMISO"
    const val NO_ENCONTRADO = "NO_ENCONTRADO"
    const val SESION_CERRADA = "SESION_CERRADA"
    const val SESION_YA_ABIERTA = "SESION_YA_ABIERTA"

    // Solicitudes y votos
    const val YA_VOTADO = "YA_VOTADO"
    const val SOLICITUD_NO_ACTIVA = "SOLICITUD_NO_ACTIVA"
    const val CANCION_RECIENTE = "CANCION_RECIENTE"
    const val VIDEO_NO_VALIDO = "VIDEO_NO_VALIDO"
    const val LIMITE_SOLICITUDES = "LIMITE_SOLICITUDES"
    /** El dispositivo ya usó sus votos de la sesión en canciones que pidieron otros. */
    const val LIMITE_VOTOS = "LIMITE_VOTOS"

    const val LIMITE_BUSQUEDAS = "LIMITE_BUSQUEDAS"

    const val YOUTUBE_NO_DISPONIBLE = "YOUTUBE_NO_DISPONIBLE"
    const val BASE_DATOS_NO_DISPONIBLE = "BASE_DATOS_NO_DISPONIBLE"
    const val ERROR_INTERNO = "ERROR_INTERNO"
}