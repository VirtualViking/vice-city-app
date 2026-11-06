package vicecity.servidor.dominio

import java.net.URLEncoder

/** Dirección que guarda el código QR impreso: la app web con el código del establecimiento. */
object EnlaceQr {
    const val PARAMETRO = "e"

    /** "https://virtualviking.github.io/vice-city-app/" + "pfbztzm9" → ".../vice-city-app/?e=pfbztzm9" */
    fun de(webUrl: String, codigoQr: String): String =
        webUrl.trimEnd('/') + "/?$PARAMETRO=" + URLEncoder.encode(codigoQr, Charsets.UTF_8)
}