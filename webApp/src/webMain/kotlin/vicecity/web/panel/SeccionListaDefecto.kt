package vicecity.web.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vicecity.compartido.ItemListaDefecto
import vicecity.compartido.ResultadoBusqueda
import vicecity.web.busqueda.formatoDuracion
import vicecity.web.componentes.BotonNeon
import vicecity.web.componentes.IconoCerrar
import vicecity.web.componentes.IconoVoto
import vicecity.web.componentes.Miniatura
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.ErrorDeApi
import vicecity.web.tema.Colores

/** Canción de la lista mientras se edita (la búsqueda y la lista guardada usan clases distintas). */
private data class CancionLista(val videoId: String, val titulo: String, val artista: String, val duracionSeg: Int)

/** Edición local de la lista por defecto: los cambios se envían todos juntos con "Guardar lista". */
private class ModeloListaDefecto(private val panel: ModeloPanel, private val alcance: CoroutineScope) {
    var canciones by mutableStateOf<List<CancionLista>>(emptyList())
    var guardada by mutableStateOf<List<CancionLista>>(emptyList())
    var cargando by mutableStateOf(true)
    var guardando by mutableStateOf(false)
    var texto by mutableStateOf("")
    var buscando by mutableStateOf(false)
    var resultados by mutableStateOf<List<ResultadoBusqueda>?>(null)
    var error by mutableStateOf<String?>(null)

    val cambios: Boolean get() = canciones != guardada

    fun cargar() = alcance.launch {
        try {
            guardada = panel.api.listaDefecto().canciones.map(::deLista)
            canciones = guardada
        } catch (e: ErrorDeApi) {
            panel.alFallar(e)
        } finally {
            cargando = false
        }
    }

    /** Con menos de 2 letras no hace nada: el servidor no busca textos tan cortos. */
    fun buscar() {
        if (texto.trim().length < 2) {
            error = null
            return
        }
        buscando = true
        error = null
        alcance.launch {
            try {
                resultados = panel.api.buscar(texto.trim()).resultados
            } catch (e: ErrorDeApi) {
                error = e.message
            } finally {
                buscando = false
            }
        }
    }

    fun agregar(r: ResultadoBusqueda) {
        if (canciones.none { it.videoId == r.videoId }) canciones = canciones + CancionLista(r.videoId, r.titulo, r.artista, r.duracionSeg)
    }

    fun quitar(i: Int) {
        canciones = canciones.filterIndexed { j, _ -> j != i }
    }

    /** Mueve la canción [i] una posición arriba (-1) o abajo (+1). */
    fun mover(i: Int, paso: Int) {
        val j = i + paso
        if (j !in canciones.indices) return
        val nueva = canciones.toMutableList()
        nueva[i] = canciones[j]
        nueva[j] = canciones[i]
        canciones = nueva
    }

    fun guardar() {
        guardando = true
        alcance.launch {
            try {
                guardada = panel.api.guardarLista(canciones.map { it.videoId }).canciones.map(::deLista)
                canciones = guardada
                panel.aviso = AvisoPanel("Lista guardada: el nuevo orden se usa desde la siguiente canción.")
                panel.refrescar()
            } catch (e: ErrorDeApi) {
                panel.alFallar(e)
            } finally {
                guardando = false
            }
        }
    }

    private fun deLista(item: ItemListaDefecto) = CancionLista(item.videoId, item.titulo, item.artista, item.duracionSeg)
}

/** Lista por defecto (Figura 15): suena en este orden cuando la cola está vacía y vuelve a empezar al terminar. */
@Composable
fun SeccionListaDefecto(panel: ModeloPanel, miniaturas: ClienteApi, ancho: Boolean) {
    val alcance = rememberCoroutineScope()
    val modelo = remember { ModeloListaDefecto(panel, alcance) }
    LaunchedEffect(Unit) { modelo.cargar() }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Lista por defecto", color = Colores.Texto, fontSize = if (ancho) 30.sp else 24.sp, fontWeight = FontWeight.Black)
            Text("Suena en este orden cuando la cola está vacía y vuelve a empezar al terminar.", color = Colores.TextoSecundario, fontSize = 14.sp)
        }
        if (modelo.cambios) Text("Cambios sin guardar", color = Colores.Aviso, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BotonNeon(if (modelo.guardando) "Guardando…" else "Guardar lista", alPresionar = { if (!modelo.guardando && modelo.cambios) modelo.guardar() })
    }

    val buscador: @Composable (Modifier) -> Unit = { modifier ->
        Tarjeta("Agregar canciones", modifier) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoTexto(modelo.texto, { modelo.texto = it }, "Buscar en YouTube", Modifier.weight(1f), marcador = "Canción o artista", alEnviar = modelo::buscar)
                BotonFila(if (modelo.buscando) "Buscando…" else "Buscar", alPresionar = modelo::buscar, habilitado = !modelo.buscando)
            }
            TextoError(modelo.error)
            val resultados = modelo.resultados
            if (resultados != null && resultados.isEmpty()) {
                Text("Sin resultados para esa búsqueda.", color = Colores.TextoSecundario, fontSize = 14.sp)
            }
            resultados.orEmpty().forEach { r ->
                val yaEsta = modelo.canciones.any { it.videoId == r.videoId }
                FilaCancion(miniaturas, r.videoId, r.titulo, "${r.artista} · ${formatoDuracion(r.duracionSeg)}") {
                    BotonFila(if (yaEsta) "En la lista" else "Agregar", alPresionar = { modelo.agregar(r) }, habilitado = !yaEsta)
                }
            }
        }
    }

    val lista: @Composable (Modifier) -> Unit = { modifier ->
        Tarjeta(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lista actual", color = Colores.Texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                val minutos = modelo.canciones.sumOf { it.duracionSeg } / 60
                Text("${modelo.canciones.size} canciones · $minutos min", color = Colores.Apagado, fontSize = 13.sp)
            }
            when {
                modelo.cargando -> Text("Cargando…", color = Colores.TextoSecundario, fontSize = 14.sp)
                modelo.canciones.isEmpty() -> Text(
                    "La lista está vacía. Busca canciones y agrégalas: sin lista, cuando no hay solicitudes no suena nada.",
                    color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
            modelo.canciones.forEachIndexed { i, c ->
                FilaCancion(miniaturas, c.videoId, c.titulo, "${c.artista} · ${formatoDuracion(c.duracionSeg)}", numero = i + 1) {
                    BotonIcono("Subir", alPresionar = { modelo.mover(i, -1) }, habilitado = i > 0) { IconoVoto(it) }
                    BotonIcono("Bajar", alPresionar = { modelo.mover(i, 1) }, habilitado = i < modelo.canciones.lastIndex) {
                        Box(Modifier.rotate(180f)) { IconoVoto(it) }
                    }
                    BotonIcono("Quitar", alPresionar = { modelo.quitar(i) }) { IconoCerrar(Colores.Rosa, tamano = 16.dp) }
                }
            }
        }
    }

    if (ancho) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            buscador(Modifier.weight(1f))
            lista(Modifier.weight(1f))
        }
    } else {
        lista(Modifier)
        buscador(Modifier)
    }
}

@Composable
private fun FilaCancion(
    miniaturas: ClienteApi, videoId: String, titulo: String, detalle: String, numero: Int? = null,
    acciones: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Colores.TarjetaAlta, RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (numero != null) Text("$numero", color = Colores.Apagado, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(22.dp))
        Miniatura(miniaturas, videoId, "Miniatura de $titulo", Modifier.width(64.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Colores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalle, color = Colores.TextoSecundario, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { acciones() }
    }
}