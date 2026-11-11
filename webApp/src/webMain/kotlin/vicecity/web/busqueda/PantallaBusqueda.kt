package vicecity.web.busqueda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import vicecity.compartido.EstablecimientoPublico
import vicecity.compartido.FuenteBusqueda
import vicecity.compartido.ResultadoBusqueda
import vicecity.web.componentes.AvisoInstalarIos
import vicecity.web.componentes.FondoAtardecer
import vicecity.web.componentes.MensajeCentral
import vicecity.web.componentes.TituloNeon
import vicecity.web.componentes.brilloNeon
import vicecity.web.datos.Almacen
import vicecity.web.datos.ClienteApi
import vicecity.web.datos.Navegador
import vicecity.web.tema.Colores
import vicecity.web.tema.Medidas

/**
 * Pantalla principal del cliente: encabezado con el atardecer, caja de búsqueda con autocompletado y resultados.
 * Se adapta al ancho: 1 columna en celular, 2 en tableta vertical y 3 en pantallas anchas.
 * [modelo] y [lista] vienen de afuera para que los resultados y el desplazamiento sigan ahí al volver de la cola.
 */
@Composable
fun PantallaBusqueda(
    api: ClienteApi,
    modelo: ModeloBusqueda,
    lista: LazyGridState,
    establecimiento: EstablecimientoPublico,
    alElegir: (ResultadoBusqueda) -> Unit,
) {
    val estado = modelo.estado
    val foco = LocalFocusManager.current
    var mostrarAvisoIos by remember { mutableStateOf(Navegador.esIos && !Navegador.estaInstalada && !Almacen.avisoInstalarCerrado) }

    val buscar = { texto: String ->
        foco.clearFocus()   // cierra el teclado del celular para ver los resultados
        modelo.buscar(texto)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Colores.Fondo)) {
        val celular = maxWidth < 600.dp
        val columnas = columnasPara(maxWidth)
        // En pantallas anchas el contenido no pasa de AnchoMaximo y queda centrado
        val lateral = max(if (celular) 16.dp else 24.dp, (maxWidth - Medidas.AnchoMaximo) / 2 + 24.dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnas),
            modifier = Modifier.fillMaxSize(),
            state = lista,
            contentPadding = PaddingValues(start = lateral, end = lateral, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filaCompleta {
                Encabezado(
                    establecimiento,
                    alto = if (celular) 240.dp else 270.dp,
                    tamanoTitulo = if (celular) 30 else 34,
                    lateral = lateral,
                )
            }
            filaCompleta { Subtitulo(abierta = establecimiento.sesionActiva != null) }
            if (mostrarAvisoIos) {
                filaCompleta {
                    AvisoInstalarIos(alCerrar = {
                        Almacen.avisoInstalarCerrado = true
                        mostrarAvisoIos = false
                    })
                }
            }
            filaCompleta {
                CajaBusqueda(
                    texto = estado.texto,
                    sugerencias = estado.sugerencias,
                    alCambiar = modelo::cambiarTexto,
                    alBuscar = buscar,
                    alLimpiar = modelo::limpiar,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            contenido(estado, api, alElegir)
        }
    }
}

/** Celular: 1 columna. Tableta vertical: 2. Tableta horizontal o computador: 3. */
private fun columnasPara(ancho: Dp): Int = when {
    ancho < 600.dp -> 1
    ancho < 900.dp -> 2
    else -> 3
}

private fun LazyGridScope.filaCompleta(contenido: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { contenido() }
}

private fun LazyGridScope.contenido(estado: EstadoBusqueda, api: ClienteApi, alElegir: (ResultadoBusqueda) -> Unit) {
    val resultados = estado.resultados
    when {
        estado.cargando -> filaCompleta {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Colores.Rosa)
            }
        }
        estado.error != null -> filaCompleta {
            MensajeCentral(titulo = "No pudimos buscar", texto = estado.error)
        }
        resultados == null -> filaCompleta {
            MensajeCentral(titulo = "¿Qué quieres escuchar?", texto = "Escribe al menos 2 letras para buscar.")
        }
        resultados.isEmpty() -> filaCompleta {
            MensajeCentral(
                titulo = "Sin resultados",
                texto = "No encontramos videos musicales para \"${estado.buscado}\". Prueba con otro nombre o con el artista.",
            )
        }
        else -> {
            filaCompleta { TituloResultados(estado.fuente) }
            items(resultados, key = { it.videoId }) { TarjetaResultado(api, it, alPresionar = { alElegir(it) }) }
        }
    }
}

/**
 * Atardecer de borde a borde con el estado de la sesión y el nombre del establecimiento.
 * [lateral] es el margen de la grilla: el encabezado lo "desborda" para ocupar todo el ancho.
 */
@Composable
private fun Encabezado(establecimiento: EstablecimientoPublico, alto: Dp, tamanoTitulo: Int, lateral: Dp) {
    val abierta = establecimiento.sesionActiva != null
    Box(Modifier.desbordar(lateral).height(alto)) {
        FondoAtardecer(Modifier.fillMaxSize())
        Column(
            Modifier.padding(start = lateral, end = lateral, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.background(CapsulaOscura, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val punto = if (abierta) Colores.Cian else Colores.Apagado
                Box(Modifier.size(8.dp).then(if (abierta) Modifier.brilloNeon(punto, radio = 8.dp, esquina = 4.dp) else Modifier).background(punto, CircleShape))
                Text(
                    if (abierta) "SESIÓN ABIERTA" else "SESIÓN SIN ABRIR",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                )
            }
            TituloNeon(establecimiento.nombre, tamano = tamanoTitulo.sp)
        }
    }
}

@Composable
private fun Subtitulo(abierta: Boolean) {
    Text(
        if (abierta) "Busca una canción y tócala para pedirla" else "Ya puedes buscar canciones. Las solicitudes se habilitan cuando el DJ abra la sesión.",
        color = Colores.TextoSecundario, fontSize = 14.sp, lineHeight = 20.sp,
    )
}

@Composable
private fun TituloResultados(fuente: FuenteBusqueda?) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RESULTADOS", color = Colores.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (fuente == FuenteBusqueda.LOCAL) {
            Text(
                "La búsqueda en YouTube no está disponible ahora: te mostramos canciones que ya se buscaron aquí.",
                color = Colores.Aviso, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().background(Colores.AvisoSuave, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        }
    }
}

/** Fondo semitransparente de la etiqueta de sesión, para que se lea sobre el cielo claro. */
private val CapsulaOscura = Color(0x8C211936)

/** Ensancha el elemento [lateral] a cada lado, por encima del margen de la grilla, para que llegue de borde a borde. */
private fun Modifier.desbordar(lateral: Dp) = layout { medible, restricciones ->
    val extra = lateral.roundToPx()
    val ancho = restricciones.maxWidth + 2 * extra
    val colocable = medible.measure(restricciones.copy(minWidth = ancho, maxWidth = ancho))
    layout(restricciones.maxWidth, colocable.height) { colocable.place(-extra, 0) }
}