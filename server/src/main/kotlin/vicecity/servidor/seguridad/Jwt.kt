package vicecity.servidor.seguridad

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import vicecity.compartido.CodigosError
import vicecity.compartido.ErrorApi
import vicecity.compartido.Rol
import vicecity.servidor.sinPermiso
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/** Usuario del personal autenticado, tomado del token. */
data class UsuarioPersonal(val personalId: UUID, val establecimientoId: UUID, val rol: Rol)

const val AUTH_PERSONAL = "personal"

/** Emite y valida los JWT del personal (RNF-09). El establecimiento siempre se toma del token. */
class ServicioJwt(secreto: String?, private val duracion: Duration = 12.hours) {
    private val algoritmo: Algorithm =
        Algorithm.HMAC256(Secretos.oTemporal(secreto, "JWT_SECRETO", "los tokens dejarán de valer al reiniciar"))
    private val emisor = "vice-city-app"

    fun emitir(personalId: UUID, establecimientoId: UUID, rol: Rol): Pair<String, Instant> {
        val expira = Instant.now().plus(duracion.toJavaDuration())
        val token = JWT.create()
            .withIssuer(emisor)
            .withSubject(personalId.toString())
            .withClaim("est", establecimientoId.toString())
            .withClaim("rol", rol.name)
            .withExpiresAt(expira)
            .sign(algoritmo)
        return token to expira
    }

    fun configurar(config: AuthenticationConfig) {
        config.jwt(AUTH_PERSONAL) {
            verifier(JWT.require(algoritmo).withIssuer(emisor).build())
            validate { credencial ->
                val p = credencial.payload
                runCatching {
                    UsuarioPersonal(
                        personalId = UUID.fromString(p.subject),
                        establecimientoId = UUID.fromString(p.getClaim("est").asString()),
                        rol = Rol.valueOf(p.getClaim("rol").asString()),
                    )
                }.getOrNull()
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorApi(CodigosError.NO_AUTENTICADO, "Inicia sesión para continuar"))
            }
        }
    }
}

/** Usuario autenticado de la llamada. Solo se usa dentro de authenticate(AUTH_PERSONAL). */
fun ApplicationCall.usuario(): UsuarioPersonal = principal<UsuarioPersonal>()!!

/** Lanza 403 SIN_PERMISO si el rol del usuario no está entre los permitidos. */
fun UsuarioPersonal.exigirRol(vararg permitidos: Rol) {
    if (rol !in permitidos) throw sinPermiso()
}