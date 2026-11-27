package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vicecity.compartido.CalidadDatos
import vicecity.compartido.CodigosError
import vicecity.compartido.ElementoRanking
import vicecity.compartido.OrigenReproduccion
import vicecity.compartido.ReproduccionHistorial
import vicecity.compartido.ResultadoReproduccion
import vicecity.compartido.SesionAnalitica
import vicecity.compartido.TableroAnalitico
import vicecity.web.componentes.BotonSecundario
import vicecity.web.componentes.IconoListo
import vicecity.web.componentes.Miniatura
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ErrorDeApi
import vicecity.web.datos.HoraColombia
import vicecity.web.tema.Colores

/** Estado del tablero: filtros, datos y calidad. Al abrir la sección muestra la última sesión de los últimos 30 días. */
private class ModeloAnalitica(private val panel: ModeloPanel, private val alcance: CoroutineScope) {
    var desde by mutableStateOf(HoraColombia.fecha(29))
    var hasta by mutableStateOf(HoraColombia.fecha())
    var sesiones by mutableStateOf<List<SesionAnalitica>>(emptyList())
    /** Sesiones del periodo que el administrador sacó del tablero. */
    var excluidas by mutableStateOf<List<SesionAnalitica>>(emptyList())
    var verExcluidas by mutableStateOf(false)
    /** Id de la sesión que se está excluyendo o incluyendo. */
    var cambiando by mutableStateOf<String?>(null)
    /** null: todas las sesiones del periodo. */
    var sesion by mutableStateOf<String?>(null)
    var tablero by mutableStateOf<TableroAnalitico?>(null)
    var calidad by mutableStateOf<CalidadDatos?>(null)
    var cargando by mutableStateOf(false)
    var actualizando by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var ranking by mutableStateOf(0)
    var verTodoHistorial by mutableStateOf(false)
    private var primeraCarga = true

    fun cargar() {
        cargando = true
        error = null
        alcance.launch {
            try {
                val respuesta = panel.api.sesionesAnaliticas(desde.trim(), hasta.trim())
                sesiones = respuesta.sesiones
                excluidas = respuesta.excluidas
                if (excluidas.isEmpty()) verExcluidas = false
                if (primeraCarga) {
                    sesion = sesiones.firstOrNull()?.id
                    primeraCarga = false
                } else if (sesion != null && sesiones.none { it.id == sesion }) {
                    sesion = null
                }
                tablero = panel.api.tablero(desde.trim(), hasta.trim(), sesion)
                calidad = panel.api.calidad()
            } catch (e: ErrorDeApi) {
                if (e.codigo == CodigosError.DATOS_INVALIDOS) error = e.message else panel.alFallar(e)
            } finally {
                cargando = false
            }
        }
    }

    fun elegirSesion(id: String?) {
        sesion = id
        verTodoHistorial = false
        cargar()
    }

    /**
     * Saca la sesión del tablero o la vuelve a incluir (por ejemplo, una sesión de prueba).
     * Al excluir la sesión elegida, el tablero pasa a mostrar todas las del periodo.
     */
    fun cambiarExclusion(s: SesionAnalitica, excluir: Boolean) {
        if (cambiando != null) return
        cambiando = s.id
        alcance.launch {
            try {
                panel.api.excluirSesion(s.id, excluir)
                panel.aviso = AvisoPanel(
                    if (excluir) "Sesión excluida del tablero. Puedes volver a incluirla en \"Sesiones excluidas\"."
                    else "Sesión incluida de nuevo en el tablero.",
                )
                if (excluir && sesion == s.id) sesion = null
                cargar()
            } catch (e: ErrorDeApi) {
                panel.alFallar(e)
            } finally {
                cambiando = null
            }
        }
    }

    /** Ejecuta el pipeline ahora y vuelve a cargar el tablero. */
    fun actualizarDatos() {
        if (actualizando) return
        actualizando = true
        alcance.launch {
            try {
                val ejecucion = panel.api.ejecutarPipeline()
                panel.aviso = AvisoPanel("Datos actualizados: ${ejecucion.eventosLeidos} eventos procesados, ${ejecucion.eventosRechazados} apartados.")
                cargar()
            } catch (e: ErrorDeApi) {
                panel.alFallar(e)
            } finally {
                actualizando = false
            }
        }
    }
}

/**
 * Tablero analítico (Figura 17 del diseño y Entregable 6). Solo para el administrador.
 * Los datos vienen de la capa analítica: limpios y anónimos.
 */
@Composable
fun SeccionAnalitica(panel: ModeloPanel, miniaturas: ClienteApi, ancho: Boolean) {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloAnalitica(panel, alcance) }
    LaunchedEffect(Unit) { modelo.cargar() }
    val t = modelo.tablero

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Analítica", color = Colores.Texto, fontSize = if (ancho) 30.sp else 24.sp, fontWeight = FontWeight.Black)
            Etiqueta(
                t?.datosHasta?.let { "Datos procesados hasta ${HoraColombia.fechaCorta(it)}" } ?: "Procesando datos…",
                Colores.Cian,
            )
        }
        BotonSecundario(if (modelo.actualizando) "Actualizando…" else "Actualizar datos", alPresionar = modelo::actualizarDatos)
    }

    Filtros(modelo)

    if (t == null) {
        Text(if (modelo.cargando) "Cargando…" else "", color = Colores.TextoSecundario, fontSize = 15.sp)
        return
    }
    val deLaSesion = t.sesionId != null

    // Indicadores principales
    val r = t.resumen
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = if (ancho) 6 else 2,
    ) {
        Indicador("Usuarios únicos", formatoNumero(r.usuariosUnicos), "${r.visitantes} visitantes: ${r.nuevos} nuevos y ${r.recurrentes} recurrentes", Modifier.weight(1f))
        Indicador("Solicitudes", formatoNumero(r.solicitudes), porUsuario(r.solicitudes, r.usuariosUnicos), Modifier.weight(1f))
        Indicador("Votos", formatoNumero(r.votos), "Incluye el voto de cada solicitud", Modifier.weight(1f))
        Indicador("Espera promedio", HoraColombia.duracion(r.esperaPromedioSeg?.toLong()), "De la solicitud a la reproducción", Modifier.weight(1f))
        Indicador("Reproducidas", formatoNumero(r.reproducidas), "Desde la cola; ${r.saltadas} saltadas", Modifier.weight(1f))
        Indicador("No reproducidas", formatoNumero(r.noReproducidas), "En cola al cerrar; ${r.eliminadas} eliminadas", Modifier.weight(1f))
    }

    // Canción más popular
    Tarjeta(if (deLaSesion) "Canción más popular de la sesión" else "Canción más popular del periodo") {
        val popular = t.masPopular
        if (popular == null) {
            Text("Todavía no hay solicitudes.", color = Colores.TextoSecundario, fontSize = 14.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Miniatura(miniaturas, popular.videoId, "Miniatura de ${popular.titulo}", Modifier.width(if (ancho) 180.dp else 110.dp).aspectRatio(16f / 9f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(popular.titulo, color = Colores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(popular.artista, color = Colores.TextoSecundario, fontSize = 15.sp)
                    Text(
                        "${popular.votos} votos · ${popular.solicitudes} ${if (popular.solicitudes == 1) "solicitud" else "solicitudes"} · " +
                                if (popular.reproducida) "Sonó" else "No alcanzó a sonar",
                        color = Colores.Cian, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    DosColumnas(
        ancho,
        izquierda = { m ->
            Tarjeta("Solicitudes por franja horaria", m) {
                val pico = t.franjas.maxByOrNull { it.solicitudes }?.takeIf { it.solicitudes > 0 }
                val detallePico = pico?.let {
                    " Pico a las ${horaCorta(it.hora)} con ${it.solicitudes} ${if (it.solicitudes == 1) "solicitud" else "solicitudes"}."
                }.orEmpty()
                Text("Canciones pedidas en cada hora.$detallePico", color = Colores.TextoSecundario, fontSize = 13.sp)
                BarrasFranjas(t.franjas, ancho)
            }
        },
        derecha = { m -> Ranking(t, modelo, m) },
    )

    DosColumnas(
        ancho,
        izquierda = { m ->
            Tarjeta("Tiempos de espera", m) {
                val e = t.esperas
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Dato("Promedio", HoraColombia.duracion(e.promedioSeg?.toLong()))
                    Dato("Mediana", HoraColombia.duracion(e.medianaSeg?.toLong()))
                    Dato("Máxima", HoraColombia.duracion(e.maximoSeg?.toLong()))
                }
                val maximo = e.tramos.maxOfOrNull { it.solicitudes }?.coerceAtLeast(1) ?: 1
                e.tramos.forEach { tramo ->
                    val nombre = tramo.hastaMin?.let { "${tramo.desdeMin} a $it min" } ?: "Más de ${tramo.desdeMin} min"
                    BarraHorizontal(nombre, "${tramo.solicitudes}", tramo.solicitudes.toFloat() / maximo, Colores.Coral)
                }
                e.noReproducidasPromedioSeg?.let {
                    Text("Las que no sonaron esperaron en promedio ${HoraColombia.duracion(it.toLong())} hasta el cierre.", color = Colores.Apagado, fontSize = 13.sp)
                }
            }
        },
        derecha = { m ->
            Tarjeta("Cola vs. lista por defecto", m) {
                val l = t.listaDefecto
                BarraDividida(l.porcentajeCola / 100f, Colores.Cian, Colores.Apagado.copy(alpha = 0.55f))
                Leyenda("Cola: ${l.porcentajeCola} % · ${HoraColombia.duracion(l.segundosCola)}", Colores.Cian)
                Leyenda("Lista por defecto: ${l.porcentajeLista} % · ${HoraColombia.duracion(l.segundosLista)}", Colores.Apagado)
                Text(
                    "${l.transiciones} transiciones entre la cola y la lista. Sonaron ${l.reproduccionesCola} canciones de la cola y ${l.reproduccionesLista} de la lista.",
                    color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        },
    )

    Historial(t.historial, deLaSesion, ancho, modelo)

    DosColumnas(
        ancho,
        izquierda = { m ->
            Tarjeta("Concentración de votos", m) {
                val c = t.concentracion
                val porcentaje = c.porcentajeVotosTop
                if (porcentaje == null) {
                    Text("Sin votos en este periodo.", color = Colores.TextoSecundario, fontSize = 14.sp)
                } else {
                    Text(
                        "El 10 % de participantes más activos (${c.participantesTop}) dio el $porcentaje % de los votos.",
                        color = Colores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp,
                    )
                    BarraProporcion(porcentaje / 100f, Colores.Coral, alto = 12)
                    Text(
                        "Sobre ${c.participantesQueVotaron} participantes que votaron. Un valor alto indica que pocas personas definen la música.",
                        color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
        },
        derecha = { m -> NoReproducidas(t, m) },
    )

    modelo.calidad?.let { CalidadYPrivacidad(it) }
}

@Composable
private fun Filtros(modelo: ModeloAnalitica) {
    Tarjeta {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.Bottom) {
            CampoTexto(modelo.desde, { modelo.desde = it }, "Desde", Modifier.width(150.dp), marcador = "2026-11-01")
            CampoTexto(modelo.hasta, { modelo.hasta = it }, "Hasta", Modifier.width(150.dp), marcador = "2026-11-30", alEnviar = modelo::cargar)
            BotonFila(if (modelo.cargando) "Cargando…" else "Aplicar fechas", alPresionar = modelo::cargar, habilitado = !modelo.cargando)
        }
        Text("Sesión", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Todas las del periodo", elegido = modelo.sesion == null, alElegir = { modelo.elegirSesion(null) })
            modelo.sesiones.forEach { s ->
                Chip("${HoraColombia.fechaCorta(s.inicio)} · ${textoSolicitudes(s.solicitudes)}", elegido = modelo.sesion == s.id, alElegir = { modelo.elegirSesion(s.id) })
            }
        }
        if (modelo.sesiones.isEmpty() && !modelo.cargando) {
            Text("No hay sesiones en estas fechas.", color = Colores.Apagado, fontSize = 13.sp)
        }

        // Excluir la sesión elegida (solo si ya cerró) y volver a incluir las excluidas
        val elegida = modelo.sesiones.firstOrNull { it.id == modelo.sesion }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (elegida != null && elegida.fin != null) {
                BotonFila(
                    if (modelo.cambiando == elegida.id) "Excluyendo…" else "Excluir esta sesión del tablero",
                    alPresionar = { modelo.cambiarExclusion(elegida, excluir = true) }, color = Colores.Aviso, habilitado = modelo.cambiando == null,
                )
            }
            if (modelo.excluidas.isNotEmpty()) {
                BotonFila(
                    if (modelo.verExcluidas) "Ocultar sesiones excluidas" else "Sesiones excluidas (${modelo.excluidas.size})",
                    alPresionar = { modelo.verExcluidas = !modelo.verExcluidas },
                )
            }
        }
        if (elegida != null && elegida.fin != null) {
            Text(
                "Para sesiones de prueba: deja de contar en los indicadores, pero no se borra y la puedes volver a incluir.",
                color = Colores.Apagado, fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
        if (modelo.verExcluidas) {
            modelo.excluidas.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${HoraColombia.fechaCorta(s.inicio)} · ${textoSolicitudes(s.solicitudes)}",
                        color = Colores.TextoSecundario, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    BotonFila(
                        if (modelo.cambiando == s.id) "Incluyendo…" else "Volver a incluir",
                        alPresionar = { modelo.cambiarExclusion(s, excluir = false) }, color = Colores.Cian, habilitado = modelo.cambiando == null,
                    )
                }
            }
        }
        TextoError(modelo.error)
    }
}

@Composable
private fun Chip(texto: String, elegido: Boolean, alElegir: () -> Unit) {
    Text(
        texto,
        color = if (elegido) Colores.TextoSobreNeon else Colores.Texto,
        fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier
            .background(if (elegido) Colores.Cian else Colores.TarjetaAlta, RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = alElegir)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Ranking(t: TableroAnalitico, modelo: ModeloAnalitica, modifier: Modifier) {
    Tarjeta(modifier = modifier) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text("Más solicitadas", color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pestanas(listOf("Canción", "Artista", "Género"), modelo.ranking, alElegir = { modelo.ranking = it })
        }
        val lista: List<ElementoRanking> = when (modelo.ranking) {
            0 -> t.ranking.canciones
            1 -> t.ranking.artistas
            else -> t.ranking.generos
        }
        if (lista.isEmpty()) {
            Text(
                if (modelo.ranking == 2) "Las canciones de este periodo no tienen género en YouTube." else "Todavía no hay solicitudes.",
                color = Colores.TextoSecundario, fontSize = 14.sp,
            )
        }
        val maximo = lista.maxOfOrNull { it.votos }?.coerceAtLeast(1) ?: 1
        lista.forEach {
            BarraHorizontal(it.nombre, "${it.votos} ${if (it.votos == 1) "voto" else "votos"} · ${textoSolicitudes(it.solicitudes)}", it.votos.toFloat() / maximo)
        }
    }
}

@Composable
private fun Historial(historial: List<ReproduccionHistorial>, deLaSesion: Boolean, ancho: Boolean, modelo: ModeloAnalitica) {
    Tarjeta("Historial de canciones reproducidas") {
        Text("Lo más reciente primero. Un fin inferido es el que el pipeline dedujo, por ejemplo, del cierre de la sesión.", color = Colores.TextoSecundario, fontSize = 13.sp)
        if (historial.isEmpty()) {
            Text("Todavía no ha sonado nada en este periodo.", color = Colores.TextoSecundario, fontSize = 14.sp)
            return@Tarjeta
        }
        val columnas = if (ancho) {
            listOf("Hora" to columna(if (deLaSesion) 90 else 170), "Canción" to Modifier.weight(1f), "Origen" to columna(130),
                "Votos" to columna(60), "Espera" to columna(80), "Resultado" to columna(150))
        } else {
            listOf("Hora" to columna(78), "Canción" to Modifier.weight(1f), "Resultado" to columna(100))
        }
        EncabezadoTabla(columnas)
        val visibles = if (modelo.verTodoHistorial) historial else historial.take(MAX_FILAS)
        visibles.forEach { h ->
            Separador()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CeldaTabla(if (deLaSesion) HoraColombia.hora(h.inicio) else HoraColombia.fechaCorta(h.inicio), columnas[0].second, color = Colores.TextoSecundario)
                Column(columnas[1].second.padding(end = 8.dp)) {
                    Text(h.titulo, color = Colores.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (ancho) h.artista else "${h.artista} · ${nombreOrigen(h.origen)}",
                        color = Colores.Apagado, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ancho) {
                    CeldaTabla(nombreOrigen(h.origen), columnas[2].second)
                    CeldaTabla(h.votos?.toString() ?: "", columnas[3].second)
                    CeldaTabla(h.esperaSeg?.let { HoraColombia.duracion(it.toLong()) } ?: "", columnas[4].second)
                    CeldaTabla(nombreResultado(h), columnas[5].second, color = colorResultado(h.resultado))
                } else {
                    CeldaTabla(nombreResultado(h), columnas[2].second, color = colorResultado(h.resultado))
                }
            }
        }
        if (historial.size > MAX_FILAS) {
            BotonFila(
                if (modelo.verTodoHistorial) "Ver menos" else "Ver las ${historial.size}",
                alPresionar = { modelo.verTodoHistorial = !modelo.verTodoHistorial },
            )
        }
    }
}

@Composable
private fun NoReproducidas(t: TableroAnalitico, modifier: Modifier) {
    Tarjeta("Muy solicitadas que no alcanzaron a sonar", modifier) {
        Text("Seguían en cola al cerrar la sesión, ordenadas por votos.", color = Colores.TextoSecundario, fontSize = 13.sp)
        if (t.noReproducidas.isEmpty()) {
            Text("Ninguna: todas las solicitudes sonaron o se eliminaron.", color = Colores.TextoSecundario, fontSize = 14.sp)
            return@Tarjeta
        }
        val columnas = listOf("Canción" to Modifier.weight(1f), "Votos" to columna(56), "En cola" to columna(72), "Sesión" to columna(110))
        EncabezadoTabla(columnas)
        t.noReproducidas.forEach { s ->
            Separador()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CeldaTabla(s.titulo, columnas[0].second, negrita = true)
                CeldaTabla("${s.votos}", columnas[1].second)
                CeldaTabla(HoraColombia.duracion(s.enColaSeg.toLong()), columnas[2].second)
                CeldaTabla(HoraColombia.fechaCorta(s.sesionInicio).substringBefore(" · "), columnas[3].second, color = Colores.TextoSecundario)
            }
        }
    }
}

/** Evidencia del pipeline y de la anonimización (Entregable 6). */
@Composable
private fun CalidadYPrivacidad(c: CalidadDatos) {
    Tarjeta("Calidad de datos y privacidad") {
        val ultima = c.ejecuciones.firstOrNull()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Dato("Última ejecución del pipeline", ultima?.fin?.let { "${HoraColombia.fechaCorta(it)} (${nombreOrigenEjecucion(ultima.origen)})" } ?: "Aún no se ejecuta")
            Dato("Eventos de esa ejecución", ultima?.let { "${it.eventosLeidos} leídos · ${it.eventosCargados} cargados · ${it.eventosRechazados} apartados" } ?: "Sin datos")
            Dato("Sesiones", "${c.sesionesConsolidadas} consolidadas · ${c.sesionesEnCurso} en curso")
        }
        Separador()
        val limpio = c.identificadoresEnCapaAnalitica == 0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (limpio) IconoListo(Colores.Cian)
            Text(
                "Identificadores de dispositivo o de solicitud en la capa analítica: ${c.identificadoresEnCapaAnalitica}",
                color = if (limpio) Colores.Cian else Colores.Aviso, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Cada participante tiene un código que solo vale dentro de su sesión. Al consolidar una sesión cerrada se borra su clave " +
                    "y el código ya no se puede relacionar con el celular. Claves temporales que existen ahora: ${c.clavesTemporales} " +
                    "(solo de sesiones en curso). Por eso los usuarios únicos se cuentan por sesión.",
            color = Colores.TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp,
        )
        Separador()
        Text("Eventos apartados por la limpieza: ${c.eventosRechazados}", color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        c.rechazosPorMotivo.forEach { (motivo, n) ->
            Text("${nombreMotivoRechazo(motivo)}: $n", color = Colores.TextoSecundario, fontSize = 13.sp)
        }
        val normalizaciones = ultima?.normalizaciones.orEmpty()
        if (normalizaciones.isNotEmpty()) {
            Text("Correcciones de la última ejecución", color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            normalizaciones.forEach { (clave, n) ->
                Text("${nombreNormalizacion(clave)}: $n", color = Colores.TextoSecundario, fontSize = 13.sp)
            }
        }
        ultima?.retencion?.let {
            Text(
                "Retención aplicada: ${it.solicitudes} solicitudes, ${it.votos} votos, ${it.dispositivos} dispositivos y ${it.eventos} eventos vencidos borrados.",
                color = Colores.TextoSecundario, fontSize = 13.sp,
            )
        }
    }
}

// ---------- Apoyo

/** Dos tarjetas lado a lado en pantallas anchas, una debajo de otra en las angostas. */
@Composable
private fun DosColumnas(ancho: Boolean, izquierda: @Composable (Modifier) -> Unit, derecha: @Composable (Modifier) -> Unit) {
    if (ancho) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            izquierda(Modifier.weight(1f))
            derecha(Modifier.weight(1f))
        }
    } else {
        izquierda(Modifier)
        derecha(Modifier)
    }
}

@Composable
private fun Dato(titulo: String, valor: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, color = Colores.Apagado, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** "1 solicitud", "13 solicitudes" */
private fun textoSolicitudes(n: Int) = if (n == 1) "1 solicitud" else "$n solicitudes"

/** "1,6 por usuario" */
private fun porUsuario(solicitudes: Int, usuarios: Int): String {
    if (usuarios == 0) return "Sin participantes"
    val decimas = (solicitudes * 10 + usuarios / 2) / usuarios
    return "${decimas / 10},${decimas % 10} por usuario"
}

private fun nombreOrigen(origen: OrigenReproduccion) = when (origen) {
    OrigenReproduccion.COLA -> "Pedida"
    OrigenReproduccion.LISTA_DEFECTO -> "Lista por defecto"
}

private fun nombreResultado(h: ReproduccionHistorial): String {
    val base = when (h.resultado) {
        ResultadoReproduccion.TERMINADA -> "Completa"
        ResultadoReproduccion.SALTADA -> "Saltada"
        ResultadoReproduccion.FALLIDA -> "No cargó"
        ResultadoReproduccion.ELIMINADA -> "Eliminada"
        ResultadoReproduccion.CIERRE -> "Al cierre"
        ResultadoReproduccion.SONANDO -> "Sonando"
    }
    return if (h.finInferido) "$base (inferido)" else base
}

private fun colorResultado(resultado: ResultadoReproduccion) = when (resultado) {
    ResultadoReproduccion.TERMINADA, ResultadoReproduccion.SONANDO -> Colores.Cian
    ResultadoReproduccion.SALTADA, ResultadoReproduccion.CIERRE -> Colores.TextoSecundario
    ResultadoReproduccion.FALLIDA, ResultadoReproduccion.ELIMINADA -> Colores.Aviso
}

private fun nombreOrigenEjecucion(origen: String) = when (origen) {
    "AUTOMATICA" -> "automática"
    "MANUAL" -> "manual"
    "TABLERO" -> "al abrir el tablero"
    else -> origen.lowercase()
}

private fun nombreMotivoRechazo(motivo: String) = when (motivo) {
    "TIPO_DESCONOCIDO" -> "Tipo de evento desconocido"
    "ESTABLECIMIENTO_DISTINTO" -> "De otro establecimiento"
    "FECHA_FUTURA" -> "Con fecha futura"
    "FUERA_DE_SESION" -> "Fuera del horario de la sesión"
    "FALTA_DISPOSITIVO" -> "Sin dispositivo"
    "FALTA_SOLICITUD" -> "Sin solicitud"
    "FALTA_VIDEO" -> "Sin video"
    "FALTA_ORIGEN" -> "Sin origen de reproducción"
    "SOLICITUD_DESCONOCIDA" -> "Se refieren a una solicitud inexistente"
    "CANCION_DESCONOCIDA" -> "Canción desconocida"
    "REPRODUCCION_DESCONOCIDA" -> "Fin de una reproducción que no empezó"
    "DUPLICADO" -> "Duplicados"
    else -> motivo
}

private fun nombreNormalizacion(clave: String) = when (clave) {
    "ORIGEN_VOTO_DERIVADO" -> "Origen del voto derivado"
    "FIN_INFERIDO" -> "Fin de reproducción inferido"
    "ESTADO_INFERIDO" -> "Estado final inferido"
    "DURACION_NEGATIVA_CORREGIDA" -> "Duración negativa corregida"
    "ARTISTA_NORMALIZADO" -> "Artista normalizado"
    else -> clave
}

private const val MAX_FILAS = 30