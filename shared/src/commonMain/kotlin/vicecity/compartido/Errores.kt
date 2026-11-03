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
    const val BASE_DATOS_NO_DISPONIBLE = "BASE_DATOS_NO_DISPONIBLE"
    const val ERROR_INTERNO = "ERROR_INTERNO"
}