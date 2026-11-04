package vicecity.servidor.servicios

import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.CodigosError
import vicecity.compartido.LoginRespuesta
import vicecity.compartido.LoginSolicitud
import vicecity.compartido.Rol
import vicecity.servidor.ApiExcepcion
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.Personal
import vicecity.servidor.datosInvalidos
import vicecity.servidor.seguridad.Claves
import vicecity.servidor.seguridad.ServicioJwt

/** Login del personal (RF-14, HU-02). */
class AuthService(private val bd: BaseDatos, private val jwt: ServicioJwt) {

    suspend fun login(solicitud: LoginSolicitud): LoginRespuesta {
        val email = solicitud.email.trim().lowercase()
        if (email.isEmpty()) throw datosInvalidos("email")
        if (solicitud.password.isEmpty()) throw datosInvalidos("password")

        val fila = bd.tx {
            Personal.selectAll()
                .where { (Personal.email eq email) and (Personal.activo eq true) }
                .singleOrNull()
        }
        // Se verifica siempre una contraseña, exista o no el email, para no revelar cuáles existen
        val hash = fila?.get(Personal.passwordHash) ?: Claves.HASH_RELLENO
        val coincide = Claves.coincide(solicitud.password, hash)
        if (fila == null || !coincide) {
            throw ApiExcepcion(HttpStatusCode.Unauthorized, CodigosError.CREDENCIALES_INVALIDAS, "Email o contraseña incorrectos")
        }

        val rol = Rol.valueOf(fila[Personal.rol])
        val (token, expira) = jwt.emitir(fila[Personal.id], fila[Personal.establecimientoId], rol)
        return LoginRespuesta(
            token = token,
            expiraEn = expira.toString(),
            rol = rol,
            establecimientoId = fila[Personal.establecimientoId].toString(),
            nombre = fila[Personal.nombre],
        )
    }
}