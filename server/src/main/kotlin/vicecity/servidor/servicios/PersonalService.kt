package vicecity.servidor.servicios

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.CambioCuentaPersonal
import vicecity.compartido.CodigosError
import vicecity.compartido.CuentaPersonal
import vicecity.compartido.LimitesPersonal
import vicecity.compartido.ListaPersonal
import vicecity.compartido.NuevaClave
import vicecity.compartido.NuevaCuentaPersonal
import vicecity.compartido.Rol
import vicecity.servidor.BaseDatos
import vicecity.servidor.conflicto
import vicecity.servidor.datos.Personal
import vicecity.servidor.datosInvalidos
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.Claves
import vicecity.servidor.seguridad.UsuarioPersonal
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/**
 * Cuentas del personal del establecimiento (HU-18, RF-14): crear, cambiar nombre, rol y estado, y restablecer la contraseña.
 * El administrador no puede desactivarse ni quitarse el rol de administrador; así siempre queda al menos un administrador activo.
 * Las contraseñas se guardan con bcrypt ([Claves]) y nunca salen del servidor.
 */
class PersonalService(private val bd: BaseDatos) {

    suspend fun listar(usuario: UsuarioPersonal): ListaPersonal = bd.tx {
        val cuentas = Personal.selectAll().where { Personal.establecimientoId eq usuario.establecimientoId }
            .map { cuenta(it, usuario) }
            .sortedWith(compareByDescending<CuentaPersonal> { it.activo }.thenBy { it.rol.ordinal }.thenBy { it.nombre.lowercase() })
        ListaPersonal(cuentas)
    }

    suspend fun crear(usuario: UsuarioPersonal, nueva: NuevaCuentaPersonal): CuentaPersonal {
        val nombre = nombreValido(nueva.nombre)
        val email = emailValido(nueva.email)
        claveValida(nueva.clave)
        // bcrypt tarda: se calcula fuera de la transacción
        val hash = Claves.hash(nueva.clave)
        val id = UUID.randomUUID()
        try {
            return bd.tx {
                if (Personal.selectAll().where { Personal.email eq email }.any()) throw emailEnUso()
                Personal.insert {
                    it[Personal.id] = id
                    it[establecimientoId] = usuario.establecimientoId
                    it[Personal.nombre] = nombre
                    it[Personal.email] = email
                    it[passwordHash] = hash
                    it[rol] = nueva.rol.name
                    it[activo] = true
                    it[creadoEn] = Instant.now()
                }
                cuenta(Personal.selectAll().where { Personal.id eq id }.single(), usuario)
            }
        } catch (e: SQLException) {
            // Dos altas simultáneas con el mismo email: la segunda choca con el UNIQUE de la tabla
            if (esDuplicado(e)) throw emailEnUso()
            throw e
        }
    }

    suspend fun cambiar(usuario: UsuarioPersonal, personalId: UUID, cambio: CambioCuentaPersonal): CuentaPersonal {
        val nombre = nombreValido(cambio.nombre)
        if (personalId == usuario.personalId) {
            if (!cambio.activo) throw datosInvalidos("activo", "No puedes desactivar tu propia cuenta")
            if (cambio.rol != Rol.ADMIN) throw datosInvalidos("rol", "No puedes quitarte el rol de administrador")
        }
        return bd.tx {
            val filas = Personal.update({ (Personal.id eq personalId) and (Personal.establecimientoId eq usuario.establecimientoId) }) {
                it[Personal.nombre] = nombre
                it[rol] = cambio.rol.name
                it[activo] = cambio.activo
            }
            if (filas == 0) throw noEncontrado("la cuenta")
            cuenta(Personal.selectAll().where { Personal.id eq personalId }.single(), usuario)
        }
    }

    suspend fun cambiarClave(usuario: UsuarioPersonal, personalId: UUID, nueva: NuevaClave): CuentaPersonal {
        claveValida(nueva.clave)
        val hash = Claves.hash(nueva.clave)
        return bd.tx {
            val filas = Personal.update({ (Personal.id eq personalId) and (Personal.establecimientoId eq usuario.establecimientoId) }) {
                it[passwordHash] = hash
            }
            if (filas == 0) throw noEncontrado("la cuenta")
            cuenta(Personal.selectAll().where { Personal.id eq personalId }.single(), usuario)
        }
    }

    /**
     * La cuenta del token tal como está ahora en la base de datos: null si fue desactivada o ya no existe.
     * El JWT la consulta en cada petición; es una búsqueda por clave primaria.
     */
    suspend fun vigente(delToken: UsuarioPersonal): UsuarioPersonal? = bd.tx {
        Personal.selectAll()
            .where { (Personal.id eq delToken.personalId) and (Personal.establecimientoId eq delToken.establecimientoId) }
            .singleOrNull()
            ?.takeIf { it[Personal.activo] }
            ?.let { delToken.copy(rol = Rol.valueOf(it[Personal.rol])) }
    }

    private fun cuenta(fila: ResultRow, usuario: UsuarioPersonal) = CuentaPersonal(
        id = fila[Personal.id].toString(),
        nombre = fila[Personal.nombre],
        email = fila[Personal.email],
        rol = Rol.valueOf(fila[Personal.rol]),
        activo = fila[Personal.activo],
        creadoEn = fila[Personal.creadoEn].toString(),
        esUsted = fila[Personal.id] == usuario.personalId,
    )

    private fun nombreValido(nombre: String): String {
        val limpio = nombre.trim().replace(ESPACIOS, " ")
        val n = LimitesPersonal.NOMBRE
        if (limpio.length !in n) throw datosInvalidos("nombre", "El nombre debe tener entre ${n.first} y ${n.last} caracteres")
        return limpio
    }

    private fun emailValido(email: String): String {
        val limpio = email.trim().lowercase()
        if (limpio.length > LimitesPersonal.EMAIL_MAX || !EMAIL.matches(limpio)) {
            throw datosInvalidos("email", "Escribe un email válido, por ejemplo dj@tubar.com")
        }
        return limpio
    }

    private fun claveValida(clave: String) {
        val c = LimitesPersonal.CLAVE
        if (clave.isBlank() || clave.length < c.first || clave.encodeToByteArray().size > c.last) {
            throw datosInvalidos("clave", "La contraseña debe tener entre ${c.first} y ${c.last} caracteres")
        }
    }

    /** 23505 = unique_violation en PostgreSQL. Exposed envuelve la excepción del driver. */
    private fun esDuplicado(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is SQLException && it.sqlState == "23505" }

    private fun emailEnUso() = conflicto(CodigosError.EMAIL_EN_USO, "Ya existe una cuenta con ese email")

    private companion object {
        val ESPACIOS = Regex("\\s+")
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}