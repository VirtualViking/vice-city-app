// Reproductor de YouTube para la vista pública (API IFrame de YouTube).
// La app en Kotlin lo controla con el objeto ViceCityReproductor (pantalla/Reproductor.kt) y recibe sus eventos.
(function () {
    var reproductor = null;
    var listo = false;
    var pendiente = null;   // video pedido antes de que el reproductor estuviera listo

    function cargarApi() {
        return new Promise(function (resolver, rechazar) {
            if (window.YT && window.YT.Player) { resolver(); return; }
            var anterior = window.onYouTubeIframeAPIReady;
            window.onYouTubeIframeAPIReady = function () { if (anterior) anterior(); resolver(); };
            var script = document.createElement('script');
            script.src = 'https://www.youtube.com/iframe_api';
            script.onerror = function () { rechazar('sin conexión con youtube.com'); };
            document.head.appendChild(script);
        });
    }

    window.ViceCityReproductor = {
        // alEvento(tipo, detalle): listo, reproduciendo, pausado, terminado, error (detalle = código de YouTube) o sin-api
        crear: function (contenedorId, alEvento) {
            cargarApi().then(function () {
                var destino = document.getElementById(contenedorId);
                if (!destino) { alEvento('sin-api', 'no se encontró el contenedor'); return; }
                reproductor = new YT.Player(destino, {
                    width: '100%',
                    height: '100%',
                    playerVars: { playsinline: 1, rel: 0, controls: 0, disablekb: 1, iv_load_policy: 3 },
                    events: {
                        onReady: function () {
                            listo = true;
                            alEvento('listo', '');
                            if (pendiente) { var p = pendiente; pendiente = null; window.ViceCityReproductor.cargar(p.id, p.desde); }
                        },
                        onStateChange: function (e) {
                            if (e.data === 0) alEvento('terminado', '');
                            else if (e.data === 1) alEvento('reproduciendo', '');
                            else if (e.data === 2) alEvento('pausado', '');
                        },
                        onError: function (e) { alEvento('error', String(e.data)); }
                    }
                });
            }).catch(function (motivo) { alEvento('sin-api', String(motivo)); });
        },
        cargar: function (videoId, desdeSeg) {
            if (!listo) { pendiente = { id: videoId, desde: desdeSeg }; return; }
            reproductor.loadVideoById({ videoId: videoId, startSeconds: desdeSeg });
        },
        reproducir: function () { if (listo) reproductor.playVideo(); },
        pausar: function () { if (listo) reproductor.pauseVideo(); },
        detener: function () { if (listo) reproductor.stopVideo(); },
        // -2 sin crear, -1 sin empezar, 0 terminado, 1 reproduciendo, 2 pausado, 3 cargando, 5 en espera
        estado: function () { return listo ? reproductor.getPlayerState() : -2; },
        // Segundos del video cargado; 0 mientras YouTube no lo conoce (por ejemplo, si muestra un mensaje de error)
        duracion: function () { return listo ? (reproductor.getDuration() || 0) : 0; },
        // Muestra u oculta el reproductor sin quitarlo de la página
        mostrar: function (visible) {
            var marco = listo ? reproductor.getIframe() : null;
            if (marco) marco.style.visibility = visible ? 'visible' : 'hidden';
        }
    };
})();