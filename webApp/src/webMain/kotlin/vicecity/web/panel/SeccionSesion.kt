package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.browser.window
import vicecity.compartido.Bloqueo
import vicecity.compartido.ElementoActual
import vicecity.compartido.EstadoReproduccion
import vicecity.compartido.EstadoReproductor
import vicecity.compartido.ItemCola
import vicecity.compartido.MotivoEliminacion
import vicecity.compartido.Origen
import vicecity.web.busqueda.formatoDuracion
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.BotonSecundario
import vicecity.web.componentes.Miniatura
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.HoraColombia
import vicecity.web.tema.Colores

/**
 * Sesión de esta noche (Figura 14 del diseño): abrir y cerrar la sesión, pausar las solicitudes,
 * controlar la música (iniciar, pausar, reanudar, saltar) y eliminar o bloquear contenido inapropiado.
 */
@Composable
fun SeccionSesion(modelo: ModeloPanel, miniaturas: ClienteApi, ancho: Boolean) {
    val estado = modelo.reproduccion

    if (estado == null) {
        Text("Cargando…", color = Colores.TextoSecundario, fontSize = 15.sp)
        return
    }
    val sesion = estado.sesion

    // Encabezado
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sesión de esta noche", color = Colores.Texto, fontSize = if (ancho) 30.sp else 24.sp, fontWeight = FontWeight.Black)
            if (sesion != null) {
                val origen = if (sesion.automatica) " · abierta por el horario" else ""
                Etiqueta("Abierta desde las ${HoraColombia.hora(sesion.inicio)}$origen · ${estado.cola.size} en cola", Colores.Cian)
            } else {
                Etiqueta("Sin sesión abierta: los clientes solo pueden buscar", Colores.Apagado)
            }
        }
        if (sesion != null) {
            BotonSecundario("Abrir vista pública", alPresionar = { abrirVistaPublica() })
            BotonSecundario(if (modelo.ocupado == "cerrar") "Cerrando…" else "Cerrar sesión", alPresionar = { modelo.hoja = HojaPanel.CerrarSesion })
        } else {
            BotonNeon(if (modelo.ocupado == "abrir") "Abriendo…" else "Abrir sesión", alPresionar = modelo::abrirSesion)
        }
    }

    if (sesion == null) {
        Tarjeta {
            // El DJ no ve la sección Operación
            val horario = if (modelo.esAdmin) {
                "Con la programación automática (sección Operación) se abre y se cierra sola según el horario."
            } else {
                "Si el administrador programó un horario, se abre y se cierra sola."
            }
            Text(
                "Abre la sesión para que los clientes pidan y voten canciones. $horario",
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        Bloqueos(modelo, miniaturas)
        return
    }

    Tarjeta {
        Interruptor(
            titulo = if (sesion.solicitudesPausadas) "Solicitudes en pausa" else "Solicitudes abiertas",
            explicacion = "Pausa las solicitudes y los votos sin cerrar la sesión, por ejemplo durante un show. La música sigue sonando.",
            activo = !sesion.solicitudesPausadas,
            alCambiar = { abiertas -> modelo.pausarSolicitudes(!abiertas) },
            habilitado = modelo.ocupado == null,
        )
    }

    SuenaAhora(estado, modelo, miniaturas, ancho, alEliminar = { modelo.hoja = it })
    Cola(estado.cola, estado.cancionesListaDefecto, miniaturas, ancho, alEliminar = { modelo.hoja = it }, ocupado = modelo.ocupado != null)
    Bloqueos(modelo, miniaturas)
}

/** Abre la vista pública en otra pestaña: comparte la cuenta guardada en el navegador. */
private fun abrirVistaPublica() {
    val base = window.location.href.substringBefore('#')
    window.open("$base#pantalla", "_blank")
}

@Composable
private fun SuenaAhora(
    estado: EstadoReproduccion, modelo: ModeloPanel, miniaturas: ClienteApi, ancho: Boolean, alEliminar: (HojaPanel.Eliminar) -> Unit,
) {
    val reproductor = estado.sesion?.estadoReproductor ?: EstadoReproductor.DETENIDO
    val actual = estado.actual
    Tarjeta {
        if (actual != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Miniatura(miniaturas, actual.videoId, "Miniatura de ${actual.titulo}", Modifier.width(if (ancho) 160.dp else 96.dp).aspectRatio(16f / 9f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (reproductor == EstadoReproductor.PAUSADO) "EN PAUSA" else "SUENA AHORA",
                            color = if (reproductor == EstadoReproductor.PAUSADO) Colores.Aviso else Colores.Cian,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                        )
                        Text(origenDe(actual), color = Colores.TextoSecundario, fontSize = 12.sp)
                    }
                    Text(actual.titulo, color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${actual.artista} · ${formatoDuracion(actual.duracionSeg)}", color = Colores.TextoSecundario, fontSize = 14.sp)
                }
            }
        } else {
            Text("SUENA AHORA", color = Colores.Apagado, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(
                when {
                    reproductor == EstadoReproductor.DETENIDO -> "La música está detenida. Inicia la música y abre la vista pública en el equipo conectado al sonido."
                    estado.cancionesListaDefecto == 0 -> "En silencio: no hay solicitudes ni lista por defecto. La primera solicitud sonará enseguida."
                    else -> "Buscando la siguiente canción…"
                },
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ocupado = modelo.ocupado
            when (reproductor) {
                EstadoReproductor.DETENIDO -> BotonNeon(if (ocupado == "iniciar") "Iniciando…" else "Iniciar música", alPresionar = modelo::iniciarMusica)
                EstadoReproductor.REPRODUCIENDO -> BotonSecundario(if (ocupado == "pausar") "Pausando…" else "Pausar", alPresionar = modelo::pausarMusica)
                EstadoReproductor.PAUSADO -> BotonNeon(if (ocupado == "reanudar") "Reanudando…" else "Reanudar", alPresionar = modelo::reanudarMusica)
            }
            if (actual != null) {
                BotonSecundario(if (ocupado == "saltar") "Saltando…" else "Saltar canción", alPresionar = modelo::saltar)
                if (actual.origen == Origen.SOLICITUD && actual.solicitudId != null) {
                    BotonSecundario("Eliminar", alPresionar = { alEliminar(HojaPanel.Eliminar(actual.solicitudId!!, actual.titulo)) })
                }
            }
        }
    }
}

private fun origenDe(actual: ElementoActual): String =
    if (actual.origen == Origen.LISTA_DEFECTO) "Lista por defecto" else "Pedida por el público"

@Composable
private fun Cola(
    cola: List<ItemCola>, cancionesLista: Int, miniaturas: ClienteApi, ancho: Boolean, alEliminar: (HojaPanel.Eliminar) -> Unit, ocupado: Boolean,
) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cola", color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("Ordenada por votos; en empate, por orden de llegada", color = Colores.Apagado, fontSize = 12.sp, textAlign = TextAlign.End)
        }
        if (cola.isEmpty()) {
            Text(
                if (cancionesLista > 0) "La cola está vacía: suena la lista por defecto ($cancionesLista canciones)."
                else "La cola está vacía y la lista por defecto también. Arma la lista en la sección Lista por defecto.",
                color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        cola.forEachIndexed { i, item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${i + 1}", color = Colores.Apagado, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(22.dp))
                Miniatura(miniaturas, item.videoId, "Miniatura de ${item.titulo}", Modifier.width(64.dp).aspectRatio(16f / 9f))
                Column(Modifier.weight(1f)) {
                    Text(item.titulo, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (ancho) item.artista else "${item.votos} ${if (item.votos == 1) "voto" else "votos"} · ${HoraColombia.minutosDesde(item.solicitadaEn)} min",
                        color = Colores.TextoSecundario, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ancho) {
                    Text("${item.votos}", color = Colores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                    Text("${HoraColombia.minutosDesde(item.solicitadaEn)} min", color = Colores.TextoSecundario, fontSize = 14.sp, modifier = Modifier.width(80.dp))
                }
                BotonFila("Eliminar", alPresionar = { alEliminar(HojaPanel.Eliminar(item.solicitudId, item.titulo)) }, color = Colores.Rosa, habilitado = !ocupado)
            }
        }
        Text(
            "Eliminar es para contenido que no es musical o es inapropiado. Pide un motivo, que queda registrado.",
            color = Colores.Apagado, fontSize = 12.sp,
        )
    }
}

@Composable
private fun Bloqueos(modelo: ModeloPanel, miniaturas: ClienteApi) {
    val bloqueos = modelo.bloqueos?.bloqueos.orEmpty()
    if (bloqueos.isEmpty()) return
    Tarjeta("Canciones bloqueadas") {
        Text("No se pueden pedir ni aparecen en las búsquedas hasta la hora indicada.", color = Colores.TextoSecundario, fontSize = 13.sp)
        bloqueos.forEach { FilaBloqueo(it, miniaturas, alDesbloquear = { modelo.desbloquear(it.videoId) }, habilitado = modelo.ocupado == null) }
    }
}

@Composable
private fun FilaBloqueo(bloqueo: Bloqueo, miniaturas: ClienteApi, alDesbloquear: () -> Unit, habilitado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Miniatura(miniaturas, bloqueo.videoId, "Miniatura de ${bloqueo.titulo}", Modifier.width(64.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f)) {
            Text(bloqueo.titulo, color = Colores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Hasta ${HoraColombia.diaYHora(bloqueo.hasta)} · ${nombreMotivo(bloqueo.motivo)}", color = Colores.TextoSecundario, fontSize = 13.sp)
        }
        BotonFila("Desbloquear", alPresionar = alDesbloquear, habilitado = habilitado)
    }
}

internal fun nombreMotivo(motivo: MotivoEliminacion) = when (motivo) {
    MotivoEliminacion.INAPROPIADO -> "Contenido inapropiado"
    MotivoEliminacion.NO_MUSICAL -> "No es música"
    MotivoEliminacion.OTRO -> "Otro motivo"
}