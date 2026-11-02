package vicecity.web

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import vicecity.compartido.Rutas
import vicecity.compartido.Salud

/** Cliente HTTP hacia el backend Ktor. La URL base viene de config.js. */
class ClienteApi(private val base: String = ViceCityConfig.apiBase.trimEnd('/')) {
    private val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Render tarda hasta ~1 minuto en despertar el servicio gratuito
        install(HttpTimeout) { requestTimeoutMillis = 90_000 }
    }

    val urlBase: String get() = base

    suspend fun salud(): Salud = http.get(base + Rutas.SALUD).body()
}
