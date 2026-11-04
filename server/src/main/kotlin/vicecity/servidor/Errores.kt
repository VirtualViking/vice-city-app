package vicecity.servidor

import io.ktor.http.HttpStatusCode
import vicecity.compartido.CodigosError

/** Error de negocio que se convierte en la respuesta estándar ErrorApi (contrato de la API). */
class ApiExcepcion(
    val estado: HttpStatusCode,
    val codigo: String,
    override val message: String,
    val detalles: Map<String, String> = emptyMap(),
) : RuntimeException(message)

fun datosInvalidos(campo: String, mensaje: String = "Dato inválido: $campo") =
    ApiExcepcion(HttpStatusCode.BadRequest, CodigosError.DATOS_INVALIDOS, mensaje, mapOf("campo" to campo))

fun noEncontrado(que: String) =
    ApiExcepcion(HttpStatusCode.NotFound, CodigosError.NO_ENCONTRADO, "No se encontró $que")

fun sinPermiso() =
    ApiExcepcion(HttpStatusCode.Forbidden, CodigosError.SIN_PERMISO, "Tu rol no tiene permiso para esta acción")

fun conflicto(codigo: String, mensaje: String, detalles: Map<String, String> = emptyMap()) =
    ApiExcepcion(HttpStatusCode.Conflict, codigo, mensaje, detalles)

fun baseDatosNoDisponible() =
    ApiExcepcion(HttpStatusCode.ServiceUnavailable, CodigosError.BASE_DATOS_NO_DISPONIBLE, "La base de datos no está configurada")