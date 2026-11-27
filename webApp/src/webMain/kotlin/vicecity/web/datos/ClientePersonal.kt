package vicecity.web.datos

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import vicecity.compartido.CalidadDatos
import vicecity.compartido.CambioConfiguracion
import vicecity.compartido.CambioCuentaPersonal
import vicecity.compartido.CambioExclusion
import vicecity.compartido.CambioMantenimiento
import vicecity.compartido.CierreSesion
import vicecity.compartido.CodigosError
import vicecity.compartido.ConfiguracionEstablecimiento
import vicecity.compartido.ConfiguracionOperacion
import vicecity.compartido.CuentaPersonal
import vicecity.compartido.EjecucionPipeline
import vicecity.compartido.EliminacionSolicitud
import vicecity.compartido.ErrorApi
import vicecity.compartido.EstadoOperacion
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.FinReproduccion
import vicecity.compartido.ListaBloqueos
import vicecity.compartido.ListaDefecto
import vicecity.compartido.ListaPersonal
import vicecity.compartido.LoginRespuesta
import vicecity.compartido.LoginSolicitud
import vicecity.compartido.NuevaClave
import vicecity.compartido.NuevaCuentaPersonal
import vicecity.compartido.NuevaListaDefecto
import vicecity.compartido.PausaSolicitudes
import vicecity.compartido.ReproduccionFallida
import vicecity.compartido.RespuestaBusqueda
import vicecity.compartido.ResultadoLimpieza
import vicecity.compartido.Rutas
import vicecity.compartido.SesionAnalitica
import vicecity.compartido.SesionDto
import vicecity.compartido.SesionesAnaliticas
import vicecity.compartido.TableroAnalitico
import vicecity.web.ViceCityConfig

/**
 * Cliente HTTP del panel del personal y de la vista pública. Cada petición lleva el token de [Almacen.cuenta].
 * Igual que [ClienteApi], cualquier falla se convierte en [ErrorDeApi]; un token vencido llega como NO_AUTENTICADO.
 */
class ClientePersonal(private val base: String = ViceCityConfig.apiBase.trimEnd('/')) {
    private val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Render tarda hasta un minuto en despertar el servicio gratuito
        install(HttpTimeout) { requestTimeoutMillis = 90_000 }
    }

    suspend fun entrar(email: String, clave: String): LoginRespuesta =
        pedir(Rutas.LOGIN, HttpMethod.Post, conToken = false) { json(LoginSolicitud(email, clave)) }

    // Sesión de la noche
    suspend fun abrirSesion(): SesionDto = pedir(Rutas.SESIONES, HttpMethod.Post)
    suspend fun cerrarSesion(sesionId: String): CierreSesion = pedir(Rutas.cerrarSesion(sesionId), HttpMethod.Post)
    suspend fun pausarSolicitudes(sesionId: String, pausadas: Boolean): SesionDto =
        pedir(Rutas.pausaSolicitudes(sesionId), HttpMethod.Put) { json(PausaSolicitudes(pausadas)) }

    // Reproductor
    suspend fun reproductor(): EstadoReproduccion = pedir(Rutas.REPRODUCTOR)
    suspend fun iniciar(): EstadoReproduccion = pedir(Rutas.REPRODUCTOR_INICIAR, HttpMethod.Post)
    suspend fun pausar(): EstadoReproduccion = pedir(Rutas.REPRODUCTOR_PAUSAR, HttpMethod.Post)
    suspend fun reanudar(): EstadoReproduccion = pedir(Rutas.REPRODUCTOR_REANUDAR, HttpMethod.Post)
    suspend fun saltar(): EstadoReproduccion = pedir(Rutas.REPRODUCTOR_SALTAR, HttpMethod.Post)
    suspend fun terminada(fin: FinReproduccion): EstadoReproduccion =
        pedir(Rutas.REPRODUCTOR_TERMINADA, HttpMethod.Post) { json(fin) }
    suspend fun fallida(falla: ReproduccionFallida): EstadoReproduccion =
        pedir(Rutas.REPRODUCTOR_FALLIDA, HttpMethod.Post) { json(falla) }
    suspend fun eliminar(solicitudId: String, eliminacion: EliminacionSolicitud): EstadoReproduccion =
        pedir(Rutas.eliminarSolicitud(solicitudId), HttpMethod.Post) { json(eliminacion) }

    // Lista por defecto y bloqueos
    suspend fun listaDefecto(): ListaDefecto = pedir(Rutas.LISTA_DEFECTO)
    suspend fun guardarLista(videoIds: List<String>): ListaDefecto =
        pedir(Rutas.LISTA_DEFECTO, HttpMethod.Put) { json(NuevaListaDefecto(videoIds)) }
    suspend fun buscar(texto: String): RespuestaBusqueda = pedir(Rutas.BUSQUEDA_PERSONAL) { parameter("q", texto) }
    suspend fun bloqueos(): ListaBloqueos = pedir(Rutas.BLOQUEOS)
    suspend fun desbloquear(videoId: String): ListaBloqueos = pedir(Rutas.bloqueo(videoId), HttpMethod.Delete)

    // Operación
    suspend fun operacion(): EstadoOperacion = pedir(Rutas.OPERACION)
    suspend fun configurarOperacion(configuracion: ConfiguracionOperacion): EstadoOperacion =
        pedir(Rutas.OPERACION, HttpMethod.Put) { json(configuracion) }
    suspend fun mantenimiento(activo: Boolean): EstadoOperacion =
        pedir(Rutas.MANTENIMIENTO, HttpMethod.Put) { json(CambioMantenimiento(activo)) }
    suspend fun limpiar(): ResultadoLimpieza = pedir(Rutas.LIMPIEZA, HttpMethod.Post)

    // Configuración del establecimiento (solo administrador)
    suspend fun configuracion(): ConfiguracionEstablecimiento = pedir(Rutas.CONFIGURACION)
    suspend fun guardarConfiguracion(cambio: CambioConfiguracion): ConfiguracionEstablecimiento =
        pedir(Rutas.CONFIGURACION, HttpMethod.Put) { json(cambio) }
    /** Imagen PNG del código QR del establecimiento, de [tamano] píxeles de lado. */
    suspend fun qr(tamano: Int): ByteArray = pedir(Rutas.ESTABLECIMIENTO_QR) { parameter("tamano", tamano) }

    // Cuentas del personal (solo administrador)
    suspend fun personal(): ListaPersonal = pedir(Rutas.PERSONAL)
    suspend fun crearCuenta(nueva: NuevaCuentaPersonal): CuentaPersonal =
        pedir(Rutas.PERSONAL, HttpMethod.Post) { json(nueva) }
    suspend fun cambiarCuenta(personalId: String, cambio: CambioCuentaPersonal): CuentaPersonal =
        pedir(Rutas.cuentaPersonal(personalId), HttpMethod.Put) { json(cambio) }
    suspend fun cambiarClave(personalId: String, clave: String): CuentaPersonal =
        pedir(Rutas.clavePersonal(personalId), HttpMethod.Put) { json(NuevaClave(clave)) }

    // Analítica (solo administrador). Fechas yyyy-MM-dd de Colombia; [sesion] null: todas las del periodo.
    suspend fun tablero(desde: String, hasta: String, sesion: String?): TableroAnalitico = pedir(Rutas.ANALITICA) {
        parameter("desde", desde)
        parameter("hasta", hasta)
        if (sesion != null) parameter("sesion", sesion)
    }
    suspend fun sesionesAnaliticas(desde: String, hasta: String): SesionesAnaliticas = pedir(Rutas.ANALITICA_SESIONES) {
        parameter("desde", desde)
        parameter("hasta", hasta)
    }
    /** Saca una sesión cerrada del tablero ([excluida] = true) o la vuelve a incluir. No borra datos. */
    suspend fun excluirSesion(sesionId: String, excluida: Boolean): SesionAnalitica =
        pedir(Rutas.exclusionSesionAnalitica(sesionId), HttpMethod.Put) { json(CambioExclusion(excluida)) }
    suspend fun ejecutarPipeline(): EjecucionPipeline = pedir(Rutas.ANALITICA_PIPELINE, HttpMethod.Post)
    suspend fun calidad(): CalidadDatos = pedir(Rutas.ANALITICA_CALIDAD)

    private inline fun <reified T : Any> HttpRequestBuilder.json(cuerpo: T) {
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    /* Se captura Throwable y no solo Exception: en el navegador una falla de red llega como un error de JavaScript. */
    private suspend inline fun <reified T> pedir(
        ruta: String,
        metodo: HttpMethod = HttpMethod.Get,
        conToken: Boolean = true,
        crossinline extra: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token = if (conToken) Almacen.cuenta?.token ?: throw ErrorDeApi(CodigosError.NO_AUTENTICADO, "Inicia sesión para continuar") else null
        val respuesta: HttpResponse = try {
            http.request(base + ruta) {
                method = metodo
                if (token != null) bearerAuth(token)
                extra()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ErrorDeApi(ClienteApi.SIN_CONEXION, "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.")
        }
        if (!respuesta.status.isSuccess()) {
            val error = try { respuesta.body<ErrorApi>() } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
            throw ErrorDeApi(error?.codigo ?: CodigosError.ERROR_INTERNO, error?.mensaje ?: "Algo salió mal. Intenta de nuevo.", error?.detalles.orEmpty())
        }
        return try {
            respuesta.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ErrorDeApi(CodigosError.ERROR_INTERNO, "Algo salió mal. Intenta de nuevo.")
        }
    }
}