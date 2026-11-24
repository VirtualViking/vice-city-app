package vicecity.servidor.analitica

import vicecity.compartido.OrigenReproduccion
import vicecity.compartido.ResultadoReproduccion
import vicecity.servidor.dominio.Agenda
import vicecity.servidor.servicios.TipoEvento
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/* Hechos de una sesión, ya anónimos: es lo que se guarda en el esquema analitica. */

data class HechoParticipacion(val participante: String, val accedio: Boolean, val nuevo: Boolean, val solicitudes: Int, val votos: Int)

data class HechoSolicitud(
    val solicitudRef: String,
    val participante: String,
    val videoId: String,
    val solicitadaEn: Instant,
    val horaLocal: Int,
    val estadoFinal: String,
    val votos: Int,
    val esperaSeg: Int?,
    val enColaSeg: Int?,
)

data class HechoVoto(val solicitudRef: String, val participante: String, val origen: String, val ocurridoEn: Instant, val horaLocal: Int)

data class HechoReproduccion(
    val orden: Int,
    val videoId: String,
    val origen: OrigenReproduccion,
    val solicitudRef: String?,
    val inicio: Instant,
    val fin: Instant?,
    val segundos: Int,
    val resultado: ResultadoReproduccion,
    val finInferido: Boolean,
    val votos: Int?,
    val esperaSeg: Int?,
)

data class HechosSesion(
    val participaciones: List<HechoParticipacion>,
    val solicitudes: List<HechoSolicitud>,
    val votos: List<HechoVoto>,
    val reproducciones: List<HechoReproduccion>,
    val transiciones: Int,
    /** Cuántas correcciones hizo la transformación, por tipo (evidencia de la limpieza). */
    val normalizaciones: Map<String, Int>,
)

/**
 * Etapa de transformación y anonimización: reconstruye lo que pasó en la sesión a partir de sus eventos limpios
 * y reemplaza los identificadores por códigos de la sesión ([Anonimizador]).
 * - Normaliza: el origen de cada voto (INICIAL, DIRECTO o SOLICITUD_REPETIDA), duraciones negativas a cero, hora de Colombia.
 * - Completa: una reproducción sin evento de fin se cierra con la hora del cierre de la sesión (fin inferido),
 *   y una solicitud que quedó en cola en una sesión cerrada queda como no reproducida.
 * - Generaliza: las fechas de solicitudes, votos y reproducciones se redondean al minuto.
 */
object Transformacion {

    fun transformar(sesion: SesionCruda, eventos: List<EventoLimpio>, sal: ByteArray, ahora: Instant): HechosSesion {
        val normalizaciones = mutableMapOf<String, Int>()
        fun contar(clave: String) { normalizaciones[clave] = (normalizaciones[clave] ?: 0) + 1 }
        fun seg(desde: Instant, hasta: Instant): Int {
            val s = Duration.between(desde, hasta).seconds
            if (s < 0) contar("DURACION_NEGATIVA_CORREGIDA")
            return s.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        }

        val participantes = linkedMapOf<String, Participante>()
        fun participante(hash: String) = participantes.getOrPut(hash) { Participante() }
        val solicitudes = linkedMapOf<UUID, Solicitud>()
        val votos = mutableListOf<Voto>()
        val reproducciones = mutableListOf<Reproduccion>()
        var abierta: Reproduccion? = null
        var transiciones = 0

        fun cerrar(fin: Instant, resultado: ResultadoReproduccion, inferido: Boolean, segundos: Int? = null) {
            val r = abierta ?: return
            r.fin = fin
            r.resultado = resultado
            r.finInferido = inferido
            r.segundos = segundos?.let { if (it < 0) 0.also { contar("DURACION_NEGATIVA_CORREGIDA") } else it } ?: seg(r.inicio, fin)
            if (inferido) contar("FIN_INFERIDO")
            r.solicitudId?.let { id ->
                solicitudes[id]?.let { s ->
                    if (s.estado == ESTADO_REPRODUCIENDO) {
                        s.estado = when (resultado) {
                            ResultadoReproduccion.FALLIDA -> "FALLIDA"
                            ResultadoReproduccion.ELIMINADA -> "ELIMINADA"
                            else -> "REPRODUCIDA"
                        }
                    }
                }
            }
            abierta = null
        }

        for ((e, tipo) in eventos) {
            val t = e.ocurridoEn
            when (tipo) {
                TipoEvento.ACCESO_SESION -> participante(e.dispositivoHash!!).also {
                    it.accedio = true
                    it.nuevo = e.booleano("nuevo") == true
                }
                TipoEvento.SOLICITUD_CREADA -> {
                    solicitudes[e.solicitudId!!] = Solicitud(e.solicitudId, e.dispositivoHash!!, e.videoId!!, t)
                    participante(e.dispositivoHash).solicitudes++
                }
                TipoEvento.VOTO_REGISTRADO -> {
                    val origen = e.texto("origen") ?: when {
                        e.booleano("propio") == true -> "INICIAL"
                        e.booleano("desde_solicitud") == true -> "SOLICITUD_REPETIDA"
                        else -> "DIRECTO"
                    }.also { contar("ORIGEN_VOTO_DERIVADO") }
                    solicitudes[e.solicitudId!!]!!.votos++
                    participante(e.dispositivoHash!!).votos++
                    votos += Voto(e.solicitudId, e.dispositivoHash, origen, t)
                }
                TipoEvento.REPRODUCCION_INICIADA -> {
                    // Si algo seguía sonando sin evento de fin, terminó cuando empezó lo siguiente
                    cerrar(t, ResultadoReproduccion.TERMINADA, inferido = true)
                    val cola = e.texto("origen") == Limpieza.ORIGEN_COLA
                    val solicitud = if (cola) solicitudes[e.solicitudId!!] else null
                    solicitud?.let {
                        it.estado = ESTADO_REPRODUCIENDO
                        it.esperaSeg = seg(it.solicitadaEn, t)
                    }
                    abierta = Reproduccion(
                        orden = reproducciones.size + 1, videoId = e.videoId!!,
                        origen = if (cola) OrigenReproduccion.COLA else OrigenReproduccion.LISTA_DEFECTO,
                        solicitudId = solicitud?.id, inicio = t,
                        votos = if (cola) e.entero("votos") ?: solicitud?.votos else null,
                        esperaSeg = solicitud?.esperaSeg,
                    ).also { reproducciones += it }
                }
                TipoEvento.REPRODUCCION_FINALIZADA -> cerrar(
                    t, if (e.booleano("saltada") == true) ResultadoReproduccion.SALTADA else ResultadoReproduccion.TERMINADA,
                    inferido = false, segundos = e.entero("segundos_reproducidos"),
                )
                TipoEvento.REPRODUCCION_FALLIDA -> cerrar(t, ResultadoReproduccion.FALLIDA, inferido = false)
                TipoEvento.SOLICITUD_ELIMINADA -> {
                    val s = solicitudes[e.solicitudId!!]!!
                    if (abierta?.solicitudId == s.id) cerrar(t, ResultadoReproduccion.ELIMINADA, inferido = false)
                    s.estado = "ELIMINADA"
                }
                TipoEvento.SOLICITUD_NO_REPRODUCIDA -> solicitudes[e.solicitudId!!]!!.let {
                    it.estado = "NO_REPRODUCIDA"
                    it.enColaSeg = e.entero("espera_seg") ?: seg(it.solicitadaEn, t)
                }
                TipoEvento.MODO_CAMBIADO -> transiciones++
                TipoEvento.SESION_CERRADA -> cerrar(t, ResultadoReproduccion.CIERRE, inferido = true)
                else -> {}
            }
        }

        // Sesión cerrada sin todos sus eventos de cierre: se completa con la hora de cierre
        if (sesion.cerrada && sesion.fin != null) {
            cerrar(sesion.fin, ResultadoReproduccion.CIERRE, inferido = true)
            solicitudes.values.filter { it.estado == "EN_COLA" }.forEach {
                it.estado = "NO_REPRODUCIDA"
                it.enColaSeg = seg(it.solicitadaEn, sesion.fin)
                contar("ESTADO_INFERIDO")
            }
        }
        // Sesión en curso: lo que suena sigue abierto
        abierta?.let { it.segundos = seg(it.inicio, ahora) }

        val codigo = { hash: String -> Anonimizador.participante(sal, hash) }
        val referencia = { id: UUID -> Anonimizador.solicitud(sal, id) }
        return HechosSesion(
            participaciones = participantes.map { (hash, p) -> HechoParticipacion(codigo(hash), p.accedio, p.nuevo, p.solicitudes, p.votos) },
            solicitudes = solicitudes.values.map { s ->
                HechoSolicitud(
                    solicitudRef = referencia(s.id), participante = codigo(s.dispositivoHash), videoId = s.videoId,
                    solicitadaEn = minuto(s.solicitadaEn), horaLocal = horaLocal(s.solicitadaEn), estadoFinal = s.estado,
                    votos = s.votos, esperaSeg = s.esperaSeg, enColaSeg = s.enColaSeg,
                )
            },
            votos = votos.map { v -> HechoVoto(referencia(v.solicitudId), codigo(v.dispositivoHash), v.origen, minuto(v.ocurridoEn), horaLocal(v.ocurridoEn)) },
            reproducciones = reproducciones.map { r ->
                HechoReproduccion(
                    orden = r.orden, videoId = r.videoId, origen = r.origen, solicitudRef = r.solicitudId?.let(referencia),
                    inicio = minuto(r.inicio), fin = r.fin?.let(::minuto), segundos = r.segundos, resultado = r.resultado,
                    finInferido = r.finInferido, votos = r.votos, esperaSeg = r.esperaSeg,
                )
            },
            transiciones = transiciones,
            normalizaciones = normalizaciones,
        )
    }

    fun horaLocal(momento: Instant): Int = momento.atZone(Agenda.ZONA_COLOMBIA).hour

    /** Generalización: la fecha exacta se redondea al minuto (hacia abajo). */
    fun minuto(momento: Instant): Instant = momento.truncatedTo(ChronoUnit.MINUTES)

    private const val ESTADO_REPRODUCIENDO = "REPRODUCIENDO"

    private class Participante(var accedio: Boolean = false, var nuevo: Boolean = false, var solicitudes: Int = 0, var votos: Int = 0)

    private class Solicitud(val id: UUID, val dispositivoHash: String, val videoId: String, val solicitadaEn: Instant) {
        var estado = "EN_COLA"
        var votos = 0
        var esperaSeg: Int? = null
        var enColaSeg: Int? = null
    }

    private class Voto(val solicitudId: UUID, val dispositivoHash: String, val origen: String, val ocurridoEn: Instant)

    private class Reproduccion(
        val orden: Int, val videoId: String, val origen: OrigenReproduccion, val solicitudId: UUID?, val inicio: Instant,
        val votos: Int?, val esperaSeg: Int?,
    ) {
        var fin: Instant? = null
        var segundos = 0
        var resultado = ResultadoReproduccion.SONANDO
        var finInferido = false
    }
}