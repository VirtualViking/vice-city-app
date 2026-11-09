package vicecity.web.datos

/** Función de index.html que abre la ventana de instalación guardada del navegador ("beforeinstallprompt"). */
private external fun viceCityInstalar(): Boolean

/** Instalación de la app (PWA) en la pantalla de inicio del celular. */
object Instalacion {
    /**
     * Abre la ventana de instalación del navegador (Chrome o Edge en Android).
     * Devuelve false si el navegador no la ofrece: iPhone, otro navegador, o la app ya está instalada.
     */
    fun abrir(): Boolean = try {
        viceCityInstalar()
    } catch (e: Throwable) {
        false
    }
}