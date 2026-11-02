package vicecity.servidor

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import vicecity.compartido.Rutas
import vicecity.compartido.Salud
import kotlin.time.Duration.Companion.seconds

const val VERSION = "0.1.0"

fun main() {
    // Render asigna el puerto en la variable PORT; en local se usa 8081
    // (el 8080 lo ocupa el servidor de desarrollo de la web)
    val puerto = System.getenv("PORT")?.toIntOrNull() ?: 8081
    val config = Configuracion.desdeEntorno()
    embeddedServer(Netty, port = puerto, host = "0.0.0.0") {
        modulo(config, BaseDatos.conectar(config.urlBaseDatos))
    }.start(wait = true)
}

/** Configura plugins y rutas. Recibe la base de datos para poder probarlo sin PostgreSQL. */
fun Application.modulo(config: Configuracion, baseDatos: BaseDatos?) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { llamada, causa ->
            llamada.application.environment.log.error("Error no controlado", causa)
            llamada.respond(HttpStatusCode.InternalServerError, mapOf("codigo" to "ERROR_INTERNO", "mensaje" to "Error interno del servidor"))
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

    routing {
        get(Rutas.SALUD) {
            call.respond(Salud(estado = "ok", baseDatos = BaseDatos.estado(baseDatos), version = VERSION))
        }
    }
}
