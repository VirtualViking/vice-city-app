package vicecity.compartido

import kotlinx.serialization.Serializable

/** Respuesta de GET /api/v1/salud: permite verificar la cadena web → servidor → base de datos. */
@Serializable
data class Salud(
    val estado: String,        // "ok" si el servidor responde
    val baseDatos: EstadoBaseDatos,
    val version: String,
)

@Serializable
enum class EstadoBaseDatos { CONECTADA, NO_CONFIGURADA, ERROR }
