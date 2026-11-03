package vicecity.compartido

/** Prefijo común de todos los endpoints REST (contrato de la API). */
const val API_PREFIJO = "/api/v1"

/** Rutas usadas tanto por el servidor como por la web. */
object Rutas {
    const val SALUD = "$API_PREFIJO/salud"
    const val LOGIN = "$API_PREFIJO/auth/login"
    const val SESIONES = "$API_PREFIJO/admin/sesiones"
    const val SESION_ACTIVA = "$SESIONES/activa"

    fun cerrarSesion(sesionId: String) = "$SESIONES/$sesionId/cerrar"
}