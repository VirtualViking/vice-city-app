package vicecity.servidor.servicios

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import vicecity.compartido.CalidadDatos
import vicecity.compartido.CambioExclusion
import vicecity.compartido.CancionPopular
import vicecity.compartido.CodigosError
import vicecity.compartido.ConcentracionVotos
import vicecity.compartido.CumplimientoListaDefecto
import vicecity.compartido.EjecucionPipeline
import vicecity.compartido.ElementoRanking
import vicecity.compartido.FranjaHoraria
import vicecity.compartido.OrigenReproduccion
import vicecity.compartido.RankingPopularidad
import vicecity.compartido.ReproduccionHistorial
import vicecity.compartido.ResultadoReproduccion
import vicecity.compartido.ResumenAnalitico
import vicecity.compartido.SesionAnalitica
import vicecity.compartido.SesionesAnaliticas
import vicecity.compartido.SolicitudNoReproducida
import vicecity.compartido.TableroAnalitico
import vicecity.compartido.TiemposEspera
import vicecity.compartido.TramoEspera
import vicecity.servidor.BaseDatos
import vicecity.servidor.analitica.PipelineDatos
import vicecity.servidor.conflicto
import vicecity.servidor.datos.AnCancionGeneros
import vicecity.servidor.datos.AnCanciones
import vicecity.servidor.datos.AnEjecuciones
import vicecity.servidor.datos.AnEventosRechazados
import vicecity.servidor.datos.AnParticipaciones
import vicecity.servidor.datos.AnReproducciones
import vicecity.servidor.datos.AnSales
import vicecity.servidor.datos.AnSesiones
import vicecity.servidor.datos.AnSesionesExcluidas
import vicecity.servidor.datos.AnSolicitudes
import vicecity.servidor.datos.AnVotos
import vicecity.servidor.datosInvalidos
import vicecity.servidor.dominio.Agenda
import vicecity.servidor.noEncontrado
import vicecity.servidor.seguridad.UsuarioPersonal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Indicadores del tablero analítico (RF-21 a RF-23, RF-29, RF-30 y Entregable 6).
 * Todo se calcula sobre el esquema analitica, que solo tiene datos limpios y anónimos. Sin datos, los indicadores dan cero.
 * Si los datos tienen más de un minuto al abrir el tablero, primero se ejecuta el pipeline.
 * Las sesiones que el administrador excluye (por ejemplo, las de prueba) no cuentan en el periodo ni salen en el selector.
 */
class AnaliticaService(
    private val bd: BaseDatos,
    private val pipeline: PipelineDatos,
    private val reloj: () -> Instant = Instant::now,
) {
    /** Filtro ya validado: fechas de Colombia (ambas incluidas) o una sesión. */
    private data class Filtro(val desde: LocalDate, val hasta: LocalDate, val sesionId: UUID?)

    suspend fun tablero(usuario: UsuarioPersonal, desde: String?, hasta: String?, sesion: String?): TableroAnalitico {
        val filtro = filtro(desde, hasta, sesion)
        refrescarSiHaceFalta()
        return bd.tx { calcular(usuario.establecimientoId, filtro) }
    }

    suspend fun sesiones(usuario: UsuarioPersonal, desde: String?, hasta: String?): SesionesAnaliticas {
        val filtro = filtro(desde, hasta, null)
        refrescarSiHaceFalta()
        return bd.tx {
            val filas = AnSesiones.selectAll()
                .where { (AnSesiones.establecimientoId eq usuario.establecimientoId) and (AnSesiones.fechaLocal.between(filtro.desde, filtro.hasta)) }
                .orderBy(AnSesiones.inicio, SortOrder.DESC)
                .toList()
            val ids = filas.map { it[AnSesiones.sesionId] }
            val solicitudes = solicitudesPorSesion(ids)
            val excluidas = excluidas(ids)
            val (fuera, dentro) = filas.partition { it[AnSesiones.sesionId] in excluidas }
            SesionesAnaliticas(
                sesiones = dentro.map { sesionAnalitica(it, solicitudes, excluida = false) },
                excluidas = fuera.map { sesionAnalitica(it, solicitudes, excluida = true) },
            )
        }
    }

    /**
     * Saca una sesión cerrada del tablero o la vuelve a incluir. No borra datos: los eventos son inmutables (RNF-15)
     * y la sesión sigue en la capa analítica, así que se puede deshacer en cualquier momento.
     */
    suspend fun excluir(usuario: UsuarioPersonal, sesion: String, cambio: CambioExclusion): SesionAnalitica {
        val sesionId = runCatching { UUID.fromString(sesion) }.getOrElse { throw noEncontrado("la sesión") }
        return bd.tx {
            val fila = AnSesiones.selectAll()
                .where { (AnSesiones.sesionId eq sesionId) and (AnSesiones.establecimientoId eq usuario.establecimientoId) }
                .singleOrNull() ?: throw noEncontrado("la sesión")
            if (fila[AnSesiones.fin] == null) throw conflicto(CodigosError.SESION_EN_CURSO, "Solo se pueden excluir sesiones cerradas")
            val yaExcluida = sesionId in excluidas(listOf(sesionId))
            if (cambio.excluida && !yaExcluida) {
                AnSesionesExcluidas.insert {
                    it[AnSesionesExcluidas.sesionId] = sesionId
                    it[excluidaEn] = reloj()
                    it[excluidaPor] = usuario.personalId
                }
            } else if (!cambio.excluida && yaExcluida) {
                AnSesionesExcluidas.deleteWhere { AnSesionesExcluidas.sesionId eq sesionId }
            }
            sesionAnalitica(fila, solicitudesPorSesion(listOf(sesionId)), cambio.excluida)
        }
    }

    suspend fun ejecutarPipeline(): EjecucionPipeline = pipeline.ejecutar("MANUAL")

    /** Calidad de los datos y evidencia de anonimización. */
    suspend fun calidad(usuario: UsuarioPersonal): CalidadDatos = bd.tx {
        val sesiones = AnSesiones.select(AnSesiones.sesionId, AnSesiones.consolidada)
            .where { AnSesiones.establecimientoId eq usuario.establecimientoId }.toList()
        val ids = sesiones.map { it[AnSesiones.sesionId] }
        val motivo = AnEventosRechazados.motivo
        val conteo = motivo.count()
        val rechazos = AnEventosRechazados.select(motivo, conteo).groupBy(motivo).associate { it[motivo] to it[conteo].toInt() }
        CalidadDatos(
            ejecuciones = AnEjecuciones.selectAll().orderBy(AnEjecuciones.id, SortOrder.DESC).limit(10).map(PipelineDatos::aEjecucion),
            sesionesConsolidadas = sesiones.count { it[AnSesiones.consolidada] },
            sesionesEnCurso = sesiones.count { !it[AnSesiones.consolidada] },
            clavesTemporales = if (ids.isEmpty()) 0 else AnSales.selectAll().where { AnSales.sesionId inList ids }.count().toInt(),
            identificadoresEnCapaAnalitica = identificadoresEnCapaAnalitica(),
            eventosRechazados = rechazos.values.sum(),
            rechazosPorMotivo = rechazos.toSortedMap(),
        )
    }

    // ---------- Indicadores

    private fun JdbcTransaction.calcular(establecimientoId: UUID, filtro: Filtro): TableroAnalitico {
        val delFiltro = AnSesiones.selectAll().where {
            val delEstablecimiento = AnSesiones.establecimientoId eq establecimientoId
            if (filtro.sesionId != null) delEstablecimiento and (AnSesiones.sesionId eq filtro.sesionId)
            else delEstablecimiento and (AnSesiones.fechaLocal.between(filtro.desde, filtro.hasta))
        }.toList()
        if (filtro.sesionId != null && delFiltro.isEmpty()) throw noEncontrado("la sesión")
        // En el periodo no cuentan las sesiones excluidas; una sesión pedida por su id sí se muestra
        val sesiones = if (filtro.sesionId != null) delFiltro else {
            val excluidas = excluidas(delFiltro.map { it[AnSesiones.sesionId] })
            delFiltro.filterNot { it[AnSesiones.sesionId] in excluidas }
        }
        val ids = sesiones.map { it[AnSesiones.sesionId] }
        val inicioSesion = sesiones.associate { it[AnSesiones.sesionId] to it[AnSesiones.inicio] }

        val solicitudes = if (ids.isEmpty()) emptyList() else AnSolicitudes.selectAll().where { AnSolicitudes.sesionId inList ids }.toList()
        val votos = if (ids.isEmpty()) emptyList() else AnVotos.selectAll().where { AnVotos.sesionId inList ids }.toList()
        val participaciones = if (ids.isEmpty()) emptyList() else AnParticipaciones.selectAll().where { AnParticipaciones.sesionId inList ids }.toList()
        val reproducciones = if (ids.isEmpty()) emptyList() else AnReproducciones.selectAll().where { AnReproducciones.sesionId inList ids }.toList()

        val videos = (solicitudes.map { it[AnSolicitudes.videoId] } + reproducciones.map { it[AnReproducciones.videoId] }).toSet()
        val canciones = if (videos.isEmpty()) emptyMap() else
            AnCanciones.selectAll().where { AnCanciones.videoId inList videos }.associate { it[AnCanciones.videoId] to (it[AnCanciones.titulo] to it[AnCanciones.artista]) }
        val generos = if (videos.isEmpty()) emptyMap() else
            AnCancionGeneros.selectAll().where { AnCancionGeneros.videoId inList videos }.groupBy({ it[AnCancionGeneros.videoId] }, { it[AnCancionGeneros.genero] })
        fun titulo(videoId: String) = canciones[videoId]?.first ?: videoId
        fun artista(videoId: String) = canciones[videoId]?.second ?: ""

        // Participación
        val esperas = solicitudes.mapNotNull { it[AnSolicitudes.esperaSeg] }.sorted()
        val estados = solicitudes.groupingBy { it[AnSolicitudes.estadoFinal] }.eachCount()
        val resultados = reproducciones.groupingBy { it[AnReproducciones.resultado] }.eachCount()
        val resumen = ResumenAnalitico(
            sesiones = sesiones.size,
            usuariosUnicos = participaciones.count { it[AnParticipaciones.solicitudes] > 0 || it[AnParticipaciones.votos] > 0 },
            visitantes = participaciones.count { it[AnParticipaciones.accedio] },
            nuevos = participaciones.count { it[AnParticipaciones.accedio] && it[AnParticipaciones.nuevo] },
            recurrentes = participaciones.count { it[AnParticipaciones.accedio] && !it[AnParticipaciones.nuevo] },
            solicitudes = solicitudes.size,
            votos = votos.size,
            esperaPromedioSeg = esperas.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            reproducidas = estados["REPRODUCIDA"] ?: 0,
            noReproducidas = estados["NO_REPRODUCIDA"] ?: 0,
            saltadas = resultados[ResultadoReproduccion.SALTADA.name] ?: 0,
            fallidas = resultados[ResultadoReproduccion.FALLIDA.name] ?: 0,
            eliminadas = estados["ELIMINADA"] ?: 0,
        )

        // Popularidad: más votos; con empate, más solicitudes y luego la pedida primero
        data class PorCancion(val videoId: String, val solicitudes: Int, val votos: Int, val primera: Instant, val reproducida: Boolean)
        val porCancion = solicitudes.groupBy { it[AnSolicitudes.videoId] }.map { (videoId, filas) ->
            PorCancion(
                videoId, filas.size, filas.sumOf { it[AnSolicitudes.votos] },
                filas.minOf { it[AnSolicitudes.solicitadaEn] }, filas.any { it[AnSolicitudes.estadoFinal] == "REPRODUCIDA" },
            )
        }.sortedWith(compareByDescending<PorCancion> { it.votos }.thenByDescending { it.solicitudes }.thenBy { it.primera })
        val masPopular = porCancion.firstOrNull()?.let {
            CancionPopular(it.videoId, titulo(it.videoId), artista(it.videoId), it.solicitudes, it.votos, it.reproducida)
        }
        fun ranking(grupos: Map<String, List<PorCancion>>) = grupos
            .map { (nombre, lista) -> ElementoRanking(nombre, lista.sumOf { it.solicitudes }, lista.sumOf { it.votos }) }
            .sortedWith(compareByDescending<ElementoRanking> { it.votos }.thenByDescending { it.solicitudes }.thenBy { it.nombre })
            .take(TOP)
        val rankingPopularidad = RankingPopularidad(
            canciones = porCancion.take(TOP).map { ElementoRanking(titulo(it.videoId), it.solicitudes, it.votos, it.videoId) },
            artistas = ranking(porCancion.groupBy { artista(it.videoId).ifBlank { "Sin artista" } }),
            generos = ranking(porCancion.flatMap { c -> generos[c.videoId].orEmpty().map { it to c } }.groupBy({ it.first }, { it.second })),
        )

        // Franjas horarias (hora de Colombia)
        val solicitudesPorHora = solicitudes.groupingBy { it[AnSolicitudes.horaLocal].toInt() }.eachCount()
        val votosPorHora = votos.groupingBy { it[AnVotos.horaLocal].toInt() }.eachCount()
        val franjas = (0..23).map { FranjaHoraria(it, solicitudesPorHora[it] ?: 0, votosPorHora[it] ?: 0) }

        // Tiempos de espera
        val noSonaron = solicitudes.filter { it[AnSolicitudes.estadoFinal] == "NO_REPRODUCIDA" }
        val tiempos = TiemposEspera(
            promedioSeg = resumen.esperaPromedioSeg,
            medianaSeg = mediana(esperas),
            maximoSeg = esperas.lastOrNull(),
            tramos = TRAMOS.map { (desde, hasta) ->
                TramoEspera(desde, hasta, esperas.count { it >= desde * 60 && (hasta == null || it < hasta * 60) })
            },
            noReproducidasPromedioSeg = noSonaron.mapNotNull { it[AnSolicitudes.enColaSeg] }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )

        // Cumplimiento de la lista por defecto
        val segundosCola = reproducciones.filter { it[AnReproducciones.origen] == OrigenReproduccion.COLA.name }.sumOf { it[AnReproducciones.segundos].toLong() }
        val segundosLista = reproducciones.filter { it[AnReproducciones.origen] == OrigenReproduccion.LISTA_DEFECTO.name }.sumOf { it[AnReproducciones.segundos].toLong() }
        val total = segundosCola + segundosLista
        val porcentajeCola = if (total == 0L) 0 else (segundosCola * 100.0 / total).roundToInt()
        val listaDefecto = CumplimientoListaDefecto(
            segundosCola = segundosCola,
            segundosLista = segundosLista,
            porcentajeCola = porcentajeCola,
            porcentajeLista = if (total == 0L) 0 else 100 - porcentajeCola,
            transiciones = sesiones.sumOf { it[AnSesiones.transiciones] },
            reproduccionesCola = reproducciones.count { it[AnReproducciones.origen] == OrigenReproduccion.COLA.name },
            reproduccionesLista = reproducciones.count { it[AnReproducciones.origen] == OrigenReproduccion.LISTA_DEFECTO.name },
        )

        // Historial: lo más reciente primero
        val historial = reproducciones
            .sortedWith(compareByDescending<ResultRow> { it[AnReproducciones.inicio] }.thenByDescending { it[AnReproducciones.orden] })
            .take(MAX_HISTORIAL)
            .map {
                val videoId = it[AnReproducciones.videoId]
                ReproduccionHistorial(
                    sesionId = it[AnReproducciones.sesionId].toString(),
                    inicio = it[AnReproducciones.inicio].toString(),
                    fin = it[AnReproducciones.fin]?.toString(),
                    videoId = videoId,
                    titulo = titulo(videoId),
                    artista = artista(videoId),
                    origen = OrigenReproduccion.valueOf(it[AnReproducciones.origen]),
                    segundos = it[AnReproducciones.segundos],
                    resultado = ResultadoReproduccion.valueOf(it[AnReproducciones.resultado]),
                    finInferido = it[AnReproducciones.finInferido],
                    votos = it[AnReproducciones.votos],
                    esperaSeg = it[AnReproducciones.esperaSeg],
                )
            }

        // Diagnóstico: muy pedidas que no sonaron y concentración de votos
        val noReproducidas = noSonaron
            .sortedWith(compareByDescending<ResultRow> { it[AnSolicitudes.votos] }.thenByDescending { it[AnSolicitudes.enColaSeg] ?: 0 })
            .take(MAX_NO_REPRODUCIDAS)
            .map {
                val videoId = it[AnSolicitudes.videoId]
                SolicitudNoReproducida(
                    videoId = videoId, titulo = titulo(videoId), artista = artista(videoId),
                    votos = it[AnSolicitudes.votos], enColaSeg = it[AnSolicitudes.enColaSeg] ?: 0,
                    sesionId = it[AnSolicitudes.sesionId].toString(),
                    sesionInicio = inicioSesion[it[AnSolicitudes.sesionId]].toString(),
                )
            }
        val votosPorParticipante = votos.groupingBy { it[AnVotos.sesionId] to it[AnVotos.participante] }.eachCount().values.sortedDescending()
        val top = if (votosPorParticipante.isEmpty()) 0 else ceil(votosPorParticipante.size * 0.10).toInt().coerceAtLeast(1)
        val concentracion = ConcentracionVotos(
            participantesQueVotaron = votosPorParticipante.size,
            participantesTop = top,
            porcentajeVotosTop = if (votos.isEmpty()) null else (votosPorParticipante.take(top).sum() * 100.0 / votos.size).roundToInt(),
        )

        val fin = AnEjecuciones.fin.max()
        return TableroAnalitico(
            desde = filtro.desde.toString(),
            hasta = filtro.hasta.toString(),
            sesionId = filtro.sesionId?.toString(),
            resumen = resumen,
            masPopular = masPopular,
            ranking = rankingPopularidad,
            franjas = franjas,
            esperas = tiempos,
            listaDefecto = listaDefecto,
            historial = historial,
            noReproducidas = noReproducidas,
            concentracion = concentracion,
            datosHasta = AnEjecuciones.select(fin).single()[fin]?.toString(),
        )
    }

    /**
     * Evidencia de anonimización: cuenta los valores de texto del esquema analitica que contienen un identificador de dispositivo
     * (el hash, o sus primeros 16 caracteres) o un id de solicitud, de las tablas operativas o de los eventos crudos.
     */
    private fun JdbcTransaction.identificadoresEnCapaAnalitica(): Int {
        val columnas = exec(
            """
            SELECT table_name, column_name FROM information_schema.columns
            WHERE table_schema = 'analitica' AND data_type IN ('character', 'character varying', 'text', 'jsonb')
            """.trimIndent(),
        ) { rs -> buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) } }.orEmpty()
        return columnas.sumOf { (tabla, columna) ->
            exec(
                """
                SELECT count(*) FROM analitica."$tabla" x
                WHERE EXISTS (
                    SELECT 1 FROM (
                        SELECT trim(hash) AS valor FROM dispositivo
                        UNION SELECT trim(dispositivo_hash) FROM evento WHERE dispositivo_hash IS NOT NULL
                        UNION SELECT id::text FROM solicitud
                        UNION SELECT solicitud_id::text FROM evento WHERE solicitud_id IS NOT NULL
                    ) i
                    WHERE position(i.valor IN x."$columna"::text) > 0 OR x."$columna"::text = left(i.valor, 16)
                )
                """.trimIndent(),
            ) { rs -> if (rs.next()) rs.getInt(1) else 0 } ?: 0
        }
    }

    // ---------- Apoyo

    private fun sesionAnalitica(fila: ResultRow, solicitudes: Map<UUID, Int>, excluida: Boolean) = SesionAnalitica(
        id = fila[AnSesiones.sesionId].toString(),
        inicio = fila[AnSesiones.inicio].toString(),
        fin = fila[AnSesiones.fin]?.toString(),
        automatica = fila[AnSesiones.automatica],
        solicitudes = solicitudes[fila[AnSesiones.sesionId]] ?: 0,
        excluida = excluida,
    )

    private fun solicitudesPorSesion(ids: List<UUID>): Map<UUID, Int> {
        if (ids.isEmpty()) return emptyMap()
        val conteo = AnSolicitudes.sesionId.count()
        return AnSolicitudes.select(AnSolicitudes.sesionId, conteo).where { AnSolicitudes.sesionId inList ids }
            .groupBy(AnSolicitudes.sesionId).associate { it[AnSolicitudes.sesionId] to it[conteo].toInt() }
    }

    /** Cuáles de [ids] están excluidas del tablero. */
    private fun excluidas(ids: List<UUID>): Set<UUID> =
        if (ids.isEmpty()) emptySet()
        else AnSesionesExcluidas.select(AnSesionesExcluidas.sesionId).where { AnSesionesExcluidas.sesionId inList ids }
            .map { it[AnSesionesExcluidas.sesionId] }.toSet()

    /** Si los datos tienen más de un minuto, ejecuta el pipeline antes de calcular. */
    private suspend fun refrescarSiHaceFalta() {
        val ultima = pipeline.ultimaEjecucion()
        if (ultima == null || Duration.between(ultima, reloj()) > FRESCURA) pipeline.ejecutar("TABLERO")
    }

    private fun filtro(desde: String?, hasta: String?, sesion: String?): Filtro {
        val hoy = reloj().atZone(Agenda.ZONA_COLOMBIA).toLocalDate()
        val fin = hasta?.takeIf { it.isNotBlank() }?.let { fecha(it, "hasta") } ?: hoy
        val inicio = desde?.takeIf { it.isNotBlank() }?.let { fecha(it, "desde") } ?: fin.minusDays(DIAS_POR_DEFECTO - 1)
        if (inicio > fin) throw datosInvalidos("desde", "La fecha inicial no puede ser posterior a la final")
        if (inicio < fin.minusDays(MAX_DIAS)) throw datosInvalidos("desde", "El periodo puede ser de hasta $MAX_DIAS días")
        val sesionId = sesion?.takeIf { it.isNotBlank() }?.let {
            runCatching { UUID.fromString(it) }.getOrElse { throw datosInvalidos("sesion", "Sesión inválida") }
        }
        return Filtro(inicio, fin, sesionId)
    }

    private fun fecha(texto: String, campo: String): LocalDate = try {
        LocalDate.parse(texto.trim())
    } catch (e: DateTimeParseException) {
        throw datosInvalidos(campo, "Fecha inválida en '$campo': usa el formato yyyy-MM-dd, por ejemplo 2026-11-20")
    }

    private fun mediana(ordenados: List<Int>): Int? = when {
        ordenados.isEmpty() -> null
        ordenados.size % 2 == 1 -> ordenados[ordenados.size / 2]
        else -> ((ordenados[ordenados.size / 2 - 1] + ordenados[ordenados.size / 2]) / 2.0).roundToInt()
    }

    private companion object {
        const val TOP = 10
        const val MAX_HISTORIAL = 200
        const val MAX_NO_REPRODUCIDAS = 20
        const val DIAS_POR_DEFECTO = 30L
        const val MAX_DIAS = 366L
        val FRESCURA: Duration = Duration.ofMinutes(1)
        /** Tramos de espera en minutos: 0–5, 5–10, 10–20 y más de 20. */
        val TRAMOS = listOf(0 to 5, 5 to 10, 10 to 20, 20 to null)
    }
}