package vicecity.servidor

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ContenidoCliente
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import vicecity.compartido.CodigosError
import vicecity.compartido.ErrorApi
import vicecity.compartido.Rutas
import vicecity.compartido.Salud
import vicecity.servidor.integraciones.CatalogoSinConfigurar
import vicecity.servidor.integraciones.CatalogoVideos
import vicecity.servidor.integraciones.YouTubeCatalogo
import vicecity.servidor.rutas.rutasAuth
import vicecity.servidor.rutas.rutasCliente
import vicecity.servidor.rutas.rutasEstablecimiento
import vicecity.servidor.rutas.rutasSesiones
import vicecity.servidor.seguridad.HuellaDispositivo
import vicecity.servidor.seguridad.ServicioJwt
import vicecity.servidor.servicios.AccesoService
import vicecity.servidor.servicios.AuthService
import vicecity.servidor.servicios.BusquedaService
import vicecity.servidor.servicios.Inicializador
import vicecity.servidor.servicios.QrService
import vicecity.servidor.servicios.SesionService
import vicecity.servidor.servicios.SugerenciaService
import kotlin.time.Duration.Companion.seconds

const val VERSION = "0.3.0"

fun main() {
    // Render asigna el puerto en la variable PORT; en local se usa 8081
    // (el 8080 lo ocupa el servidor de desarrollo de la web)
    val puerto = System.getenv("PORT")?.toIntOrNull() ?: 8081
    val config = Configuracion.desdeEntorno()
    val baseDatos = BaseDatos.conectar(config.urlBaseDatos)?.also {
        it.migrar()
        Inicializador.ejecutar(it, config)
    }
    embeddedServer(Netty, port = puerto, host = "0.0.0.0") {
        modulo(config, baseDatos)
    }.start(wait = true)
}

/**
 * Configura plugins y rutas. Recibe la base de datos y el catálogo de videos para poder probarlo
 * sin PostgreSQL y sin gastar cupo de YouTube. Sin [catalogo], usa YouTube si hay YOUTUBE_API_KEY.
 */
fun Application.modulo(config: Configuracion, baseDatos: BaseDatos?, catalogo: CatalogoVideos? = null) {
    val jwt = ServicioJwt(config.jwtSecreto)
    val huella = HuellaDispositivo(config.dispositivoSecreto)
    // Sin base de datos, las rutas que la necesitan responden 503
    fun bd(): BaseDatos = baseDatos ?: throw baseDatosNoDisponible()
    val auth by lazy { AuthService(bd(), jwt) }
    val sesiones by lazy { SesionService(bd()) }
    val acceso by lazy { AccesoService(bd()) }
    val catalogoVideos = catalogo ?: catalogoDesde(config)
    val busqueda by lazy { BusquedaService(bd(), catalogoVideos, config.reglasBusqueda) }
    val sugerencias by lazy { SugerenciaService(bd(), config.reglasBusqueda) }
    val qr by lazy { QrService(bd(), config.webUrl) }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(CallLogging) {
        // Las comprobaciones de Render a /salud llenarían el log
        filter { it.request.path() != Rutas.SALUD }
    }
    install(StatusPages) {
        exception<ApiExcepcion> { llamada, e ->
            llamada.respond(e.estado, ErrorApi(e.codigo, e.message, e.detalles))
        }
        exception<BadRequestException> { llamada, _ ->
            // JSON mal formado o con campos faltantes
            llamada.respond(HttpStatusCode.BadRequest, ErrorApi(CodigosError.DATOS_INVALIDOS, "El cuerpo de la petición no es válido"))
        }
        exception<Throwable> { llamada, causa ->
            llamada.application.environment.log.error("Error no controlado en ${llamada.request.httpMethod.value} ${llamada.request.path()}", causa)
            llamada.respond(HttpStatusCode.InternalServerError, ErrorApi(CodigosError.ERROR_INTERNO, "Error interno del servidor"))
        }
    }
    install(CORS) {
        // Orígenes del frontend (GitHub Pages y desarrollo local)
        config.origenesPermitidos.forEach { allowHost(it.host, schemes = listOf(it.esquema)) }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Dispositivo-Id")
        allowHeader("Idempotency-Key")
    }
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
    }
    install(Authentication) {
        jwt.configurar(this)
    }

    routing {
        get(Rutas.SALUD) {
            call.respond(Salud(estado = "ok", baseDatos = BaseDatos.estado(baseDatos), version = VERSION))
        }
        rutasAuth { auth }
        rutasSesiones { sesiones }
        rutasCliente(huella, acceso = { acceso }, busqueda = { busqueda }, sugerencias = { sugerencias })
        rutasEstablecimiento { qr }
    }
}

/** YouTube si hay clave; si no, un catálogo que siempre responde "no disponible". */
private fun Application.catalogoDesde(config: Configuracion): CatalogoVideos {
    val clave = config.youtubeApiKey ?: return CatalogoSinConfigurar().also {
        environment.log.warn("YOUTUBE_API_KEY no está definida: la búsqueda solo usará las canciones ya guardadas")
    }
    val http = HttpClient(CIO) {
        install(ContenidoCliente) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }
    monitor.subscribe(ApplicationStopped) { http.close() }
    return YouTubeCatalogo(clave, http)
}