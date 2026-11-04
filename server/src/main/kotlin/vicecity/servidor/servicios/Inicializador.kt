package vicecity.servidor.servicios

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import vicecity.compartido.Rol
import vicecity.servidor.BaseDatos
import vicecity.servidor.Configuracion
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.Personal
import vicecity.servidor.seguridad.Claves
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * Datos iniciales del PMV (el MVP no tiene registro público de establecimientos).
 * Solo actúa si las tablas están vacías, así que es seguro ejecutarlo en cada arranque.
 */
object Inicializador {
    private val log = LoggerFactory.getLogger(Inicializador::class.java)
    private const val ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789"   // sin caracteres ambiguos (l, 1, o, 0, i)

    fun ejecutar(bd: BaseDatos, config: Configuracion) = transaction(bd.database) {
        if (Establecimientos.selectAll().count() == 0L) {
            val codigo = generarCodigo()
            Establecimientos.insert {
                it[id] = UUID.randomUUID()
                it[nombre] = config.establecimientoNombre
                it[codigoQr] = codigo
                it[creadoEn] = Instant.now()
            }
            log.info("Establecimiento creado: '{}' con código QR '{}'", config.establecimientoNombre, codigo)
        }

        if (Personal.selectAll().count() == 0L) {
            val email = config.adminEmail
            val clave = config.adminPassword
            if (email == null || clave == null) {
                log.warn("No hay personal registrado: define ADMIN_EMAIL y ADMIN_PASSWORD para crear la cuenta de administrador")
                return@transaction
            }
            val establecimientoId = Establecimientos.selectAll().first()[Establecimientos.id]
            Personal.insert {
                it[id] = UUID.randomUUID()
                it[Personal.establecimientoId] = establecimientoId
                it[nombre] = config.adminNombre
                it[Personal.email] = email.trim().lowercase()
                it[passwordHash] = Claves.hash(clave)
                it[rol] = Rol.ADMIN.name
                it[activo] = true
                it[creadoEn] = Instant.now()
            }
            log.info("Cuenta de administrador creada para {}", email)
        }
    }

    private fun generarCodigo(): String {
        val aleatorio = SecureRandom()
        return (1..8).map { ALFABETO[aleatorio.nextInt(ALFABETO.length)] }.joinToString("")
    }
}