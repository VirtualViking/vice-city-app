// Service worker: red primero y caché como respaldo, solo para archivos de la propia app.
// config.js nunca se sirve desde caché para que un cambio de backend se aplique de inmediato.
const CACHE = 'vice-city-v1';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('fetch', (e) => {
  const solicitud = e.request;
  const url = new URL(solicitud.url);
  // El backend (Render), YouTube y otros orígenes no se cachean
  if (solicitud.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.endsWith('/config.js')) return;
  e.respondWith(
    fetch(solicitud)
      .then((respuesta) => {
        const copia = respuesta.clone();
        caches.open(CACHE).then((cache) => cache.put(solicitud, copia));
        return respuesta;
      })
      .catch(() => caches.match(solicitud))
  );
});
