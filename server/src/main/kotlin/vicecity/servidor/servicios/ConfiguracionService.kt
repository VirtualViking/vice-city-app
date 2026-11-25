package vicecity.servidor.servicios

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import vicecity.compartido.CambioConfiguracion
import vicecity.compartido.ConfiguracionEstablecimiento
import vicecity.compartido.LimitesConfiguracion
import vicecity.compartido.ReglasCola
import vicecity.servidor.BaseDatos
import vicecity.servidor.datos.Establecimientos
import vicecity.servidor.datos.RepositorioEstablecimientos
import vicecity.servidor.datosInvalidos
import vicecity.servidor.dominio.EnlaceQr
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.UsuarioPersonal
import java.util.UUID

/**
 * Configuración del establecimiento (HU-15, HU-13, RF-15, RF-19): nombre, código QR y reglas de la cola.
 * El código QR no se cambia, para que los QR impresos sigan funcionando.
 * Las reglas se leen en cada solicitud ([ColaService]), así que un cambio aplica desde la siguiente.
 */
class ConfiguracionService(private val bd: BaseDatos, private val webUrl: String) {

    suspend fun leer(usuario: UsuarioPersonal): ConfiguracionEstablecimiento = bd.tx { actual(usuario.establecimientoId) }

    suspend fun guardar(usuario: UsuarioPersonal, cambio: CambioConfiguracion): ConfiguracionEstablecimiento {
        // Sin espacios al inicio ni al final, y uno solo entre palabras
        val nombre = cambio.nombre.trim().replace(ESPACIOS, " ")
        val reglas = cambio.reglas
        validar(nombre, reglas)
        return bd.tx {
            val filas = Establecimientos.update({ Establecimientos.id eq usuario.establecimientoId }) {
                it[Establecimientos.nombre] = nombre
                it[limiteSolicitudes] = reglas.limiteSolicitudes
                it[ventanaLimiteMin] = reglas.ventanaLimiteMin
                it[antirepeticionMin] = reglas.antirepeticionMin
                it[duracionMaxSeg] = reglas.duracionMaxMin * 60
            }
            if (filas == 0) throw noEncontrado("el establecimiento")
            actual(usuario.establecimientoId)
        }
    }

    private fun actual(establecimientoId: UUID): ConfiguracionEstablecimiento {
        val e = RepositorioEstablecimientos.porId(establecimientoId) ?: throw noEncontrado("el establecimiento")
        val codigo = e[Establecimientos.codigoQr]
        return ConfiguracionEstablecimiento(
            nombre = e[Establecimientos.nombre],
            codigoQr = codigo,
            enlace = EnlaceQr.de(webUrl, codigo),
            reglas = ReglasCola(
                limiteSolicitudes = e[Establecimientos.limiteSolicitudes],
                ventanaLimiteMin = e[Establecimientos.ventanaLimiteMin],
                antirepeticionMin = e[Establecimientos.antirepeticionMin],
                // En la base está en segundos; en la pantalla, en minutos
                duracionMaxMin = (e[Establecimientos.duracionMaxSeg] + 30) / 60,
            ),
        )
    }

    private fun validar(nombre: String, reglas: ReglasCola) {
        val n = LimitesConfiguracion.NOMBRE
        if (nombre.length !in n) throw datosInvalidos("nombre", "El nombre debe tener entre ${n.first} y ${n.last} caracteres")
        enRango(reglas.limiteSolicitudes, LimitesConfiguracion.LIMITE_SOLICITUDES, "limiteSolicitudes", "El límite de solicitudes", "")
        enRango(reglas.ventanaLimiteMin, LimitesConfiguracion.VENTANA_LIMITE_MIN, "ventanaLimiteMin", "La ventana del límite", " minutos")
        enRango(reglas.antirepeticionMin, LimitesConfiguracion.ANTIREPETICION_MIN, "antirepeticionMin", "La anti-repetición", " minutos")
        enRango(reglas.duracionMaxMin, LimitesConfiguracion.DURACION_MAX_MIN, "duracionMaxMin", "La duración máxima", " minutos")
    }

    private fun enRango(valor: Int, rango: IntRange, campo: String, que: String, unidad: String) {
        if (valor !in rango) throw datosInvalidos(campo, "$que debe estar entre ${rango.first} y ${rango.last}$unidad")
    }

    private companion object {
        val ESPACIOS = Regex("\\s+")
    }
}