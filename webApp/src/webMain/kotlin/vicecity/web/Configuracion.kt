package vicecity.web

/**
 * Configuración en tiempo de ejecución, definida en `config.js`.
 * En GitHub Pages el workflow reescribe ese archivo con la URL del backend en Render,
 * así no hay que recompilar para cambiarla.
 */
external object ViceCityConfig {
    val apiBase: String
}

/** "WebAssembly" o "JavaScript (respaldo)": qué versión cargó el modo de compatibilidad. */
expect val plataforma: String
