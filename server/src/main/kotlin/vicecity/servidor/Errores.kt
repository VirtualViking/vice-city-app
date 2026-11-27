package vicecity.servidor

import io.ktor.http.HttpStatusCode
import vicecity.compartido.CodigosError
import java.time.Instant
import java.util.UUID

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

fun limiteBusquedas(reintentarEn: Instant) =
    ApiExcepcion(
        HttpStatusCode.TooManyRequests, CodigosError.LIMITE_BUSQUEDAS,
        "Hiciste muchas búsquedas nuevas seguidas. Prueba con las sugerencias o espera un momento.",
        mapOf("reintentarEn" to reintentarEn.toString()),
    )

fun youtubeNoDisponible() =
    ApiExcepcion(
        HttpStatusCode.ServiceUnavailable, CodigosError.YOUTUBE_NO_DISPONIBLE,
        "La búsqueda en YouTube no está disponible en este momento. Prueba con las sugerencias.",
    )

fun sesionCerrada() =
    conflicto(CodigosError.SESION_CERRADA, "La sesión de esta noche ya terminó: no se pueden pedir ni votar canciones")

fun yaVotado(solicitudId: UUID) =
    conflicto(CodigosError.YA_VOTADO, "Ya votaste por esta canción", mapOf("solicitudId" to solicitudId.toString()))

fun solicitudNoActiva(estado: String) =
    conflicto(CodigosError.SOLICITUD_NO_ACTIVA, "Esta canción ya no está en la cola", mapOf("estado" to estado))

fun cancionReciente(disponibleEn: Instant) =
    conflicto(
        CodigosError.CANCION_RECIENTE, "Esta canción sonó hace poco. Podrás pedirla de nuevo más tarde.",
        mapOf("disponibleEn" to disponibleEn.toString()),
    )

/** [motivo]: NO_DISPONIBLE (no salió en ninguna búsqueda) o DURACION_EXCEDIDA (RF-26). */
fun videoNoValido(videoId: String, motivo: String) =
    ApiExcepcion(
        HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO, "Esta canción no se puede pedir en este establecimiento",
        mapOf("videoId" to videoId, "motivo" to motivo),
    )

/** YouTube no deja reproducir el video dentro de la app (bloqueado para incrustar, privado o eliminado). */
fun videoNoReproducible(videoId: String) =
    ApiExcepcion(
        HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_NO_VALIDO, "Esta canción no se puede reproducir en la app",
        mapOf("videoId" to videoId, "motivo" to "NO_REPRODUCIBLE"),
    )

fun limiteSolicitudes(limite: Int, ventanaMin: Int, reintentarEn: Instant) =
    ApiExcepcion(
        HttpStatusCode.TooManyRequests, CodigosError.LIMITE_SOLICITUDES,
        "Ya pediste $limite canciones. Puedes pedir hasta $limite cada $ventanaMin minutos.",
        mapOf("reintentarEn" to reintentarEn.toString()),
    )

fun limiteVotos(limite: Int) =
    conflicto(
        CodigosError.LIMITE_VOTOS, "Ya usaste tus $limite votos de esta noche",
        mapOf("limite" to limite.toString()),
    )

fun videoBloqueado(videoId: String, hasta: Instant) =
    ApiExcepcion(
        HttpStatusCode.UnprocessableEntity, CodigosError.VIDEO_BLOQUEADO, "Esta canción no está disponible en este establecimiento por ahora",
        mapOf("videoId" to videoId, "hasta" to hasta.toString()),
    )

fun solicitudesPausadas() =
    conflicto(CodigosError.SOLICITUDES_PAUSADAS, "El DJ pausó las solicitudes por un momento. Vuelve a intentarlo en unos minutos.")

fun enMantenimiento() =
    conflicto(CodigosError.EN_MANTENIMIENTO, "El establecimiento está en mantenimiento: desactívalo para abrir la sesión")

fun sinSesionAbierta() =
    conflicto(CodigosError.SIN_SESION_ABIERTA, "Abre la sesión de esta noche para usar el reproductor")

fun sesionEnCurso() =
    conflicto(CodigosError.SESION_EN_CURSO, "Cierra la sesión de esta noche antes de activar el mantenimiento")

fun mantenimientoInactivo() =
    conflicto(CodigosError.MANTENIMIENTO_INACTIVO, "Activa el mantenimiento antes de limpiar")