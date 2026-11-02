# Vice City App

PMV para pedir y votar la música de un establecimiento mediante un código QR. Proyecto nuclear de Construcción de Aplicaciones Móviles y Big Data.

Estado: **paso 5, estructura base**. La web muestra una pantalla provisional que verifica la cadena web → servidor → base de datos. La app se construye desde la iteración 1.

## Arquitectura

| Módulo | Tecnología | Despliegue |
| --- | --- | --- |
| `webApp` | Kotlin/Wasm + respaldo JS, Compose Multiplatform, PWA | GitHub Pages |
| `server` | Ktor (JVM 21), Exposed, HikariCP | Render (Docker, plan gratuito) |
| `shared` | Modelos y rutas comunes (kotlinx.serialization) | Lo usan los dos anteriores |
| Base de datos | PostgreSQL | Neon (plan gratuito); en local, Docker |

```
vice-city-app/
├── shared/src/commonMain/kotlin/vicecity/compartido/   # Salud.kt, Api.kt (rutas)
├── server/src/main/kotlin/vicecity/servidor/           # Application.kt, Configuracion.kt, BaseDatos.kt
├── server/src/test/kotlin/vicecity/servidor/           # SaludTest.kt
├── webApp/src/webMain/kotlin/vicecity/web/             # App.kt, ClienteApi.kt, Configuracion.kt, main.kt
├── webApp/src/webMain/resources/                       # index.html, config.js, manifest, sw.js, íconos
├── .github/workflows/ci.yml                            # pruebas del servidor en cada push
├── .github/workflows/pages.yml                         # publica la web en GitHub Pages
├── Dockerfile, render.yaml                             # despliegue del servidor en Render
└── docker-compose.yml                                  # PostgreSQL local
```

Versiones: Kotlin 2.4.21 · Compose Multiplatform 1.12.2 · Ktor 3.6.1 · Exposed 1.6.0 · PostgreSQL JDBC 42.7.14 · Gradle 8.14.3 · JDK 21.

## Variables de entorno del servidor

| Variable | Ejemplo | Para qué |
| --- | --- | --- |
| `DATABASE_URL` | `postgresql://usuario:clave@ep-xxxx.neon.tech/vicecity?sslmode=require` | Conexión a PostgreSQL. Si falta, el servidor arranca igual y `/salud` dice `NO_CONFIGURADA`. |
| `CORS_ORIGENES` | `https://virtualviking.github.io` | Orígenes de la web separados por coma. En local, por defecto `http://localhost:8080`. |
| `YOUTUBE_API_KEY` | `AIza…` | Clave de la YouTube Data API. Se usa desde la iteración 1. |
| `PORT` | `10000` | La asigna Render. En local, por defecto `8081`. |

Nunca subas estos valores al repositorio: van en Render (o en tu configuración local de IntelliJ).

## Ejecutar en local

1. **PostgreSQL**, con [Docker Desktop](https://www.docker.com/products/docker-desktop/):
   ```powershell
   docker compose up -d
   ```
   Crea la base `vicecity` (usuario `vicecity`, clave `vicecity`) en el puerto 5432.

2. **Servidor**, en la terminal de IntelliJ (PowerShell):
   ```powershell
   $env:DATABASE_URL = "postgresql://vicecity:vicecity@localhost:5432/vicecity"
   ./gradlew :server:run
   ```
   Comprueba en el navegador: <http://localhost:8081/api/v1/salud> → `"baseDatos":"CONECTADA"`.

3. **Web**, en otra terminal:
   ```powershell
   ./gradlew :webApp:wasmJsBrowserDevelopmentRun
   ```
   Abre <http://localhost:8080>. Debe mostrar Servidor "Responde" y Base de datos "Conectada".

4. **Pruebas del servidor:** `./gradlew :server:test`

## Configurar la nube (una sola vez)

### 1. Neon (PostgreSQL)

1. Crea una cuenta en <https://neon.com> (no pide tarjeta).
2. Crea un proyecto llamado `vice-city`, con región **AWS US East (N. Virginia)**, la misma del servidor en Render.
3. En el proyecto, crea la base de datos `vicecity` o usa la que viene por defecto.
4. En **Connect**, copia la *connection string* (`postgresql://…?sslmode=require`). Es el valor de `DATABASE_URL`.

### 2. Render (servidor)

1. Crea una cuenta en <https://render.com> con tu cuenta de GitHub.
2. **New → Blueprint** → elige el repositorio `vice-city-app`. Render lee `render.yaml` y crea el servicio `vice-city-api`.
3. Te pedirá `DATABASE_URL` (la de Neon) y `YOUTUBE_API_KEY` (si aún no la tienes, déjala vacía y agrégala después en *Environment*).
4. Espera el primer despliegue y copia la URL del servicio, por ejemplo `https://vice-city-api.onrender.com`. Pruébala: `…/api/v1/salud`.

Se redespliega solo cuando cambian `server/`, `shared/` o la configuración de Gradle, y solo si el CI de GitHub pasa.

> El plan gratuito se duerme tras 15 minutos sin tráfico. La primera petición después tarda cerca de un minuto. Durante una sesión no se duerme, porque la vista pública enviará un mensaje periódico por WebSocket (iteración 1).

### 3. GitHub Pages (web)

1. En el repositorio: **Settings → Pages → Source: GitHub Actions**.
2. **Settings → Secrets and variables → Actions → pestaña Variables → New repository variable**:
   - Name: `API_BASE`
   - Value: la URL de Render, sin `/` al final (por ejemplo `https://vice-city-api.onrender.com`).
3. **Settings → Environments → github-pages**: en *Deployment branches and tags* verifica que `main` esté permitida (es el error que tuvimos en la PoC).
4. Ejecuta **Actions → Publicar web en GitHub Pages → Run workflow**. La web queda en `https://virtualviking.github.io/vice-city-app/`.

### 4. API key de YouTube

1. Entra a <https://console.cloud.google.com> con tu cuenta de Google.
2. Crea un proyecto llamado `vice-city-app` (selector de proyectos, arriba → *Proyecto nuevo*).
3. **APIs y servicios → Biblioteca** → busca **YouTube Data API v3** → **Habilitar**.
4. **APIs y servicios → Credenciales → Crear credenciales → Clave de API**.
5. En la clave creada, **Editar**: en *Restricciones de API* elige **Restringir clave** y marca solo **YouTube Data API v3**. Deja *Restricciones de aplicación* en "Ninguna", porque Render no tiene una IP fija.
6. Copia la clave y guárdala como `YOUTUBE_API_KEY` en Render (**Environment**). Nunca la pongas en el código ni en la web.

Cuota por defecto: 10.000 unidades diarias, pero las búsquedas (`search.list`) tienen un cupo propio de unas **100 llamadas al día**. Por eso la caché de búsquedas (RNF-14) es obligatoria desde la iteración 1.

## Verificación de esta estructura base

- Servidor: 3 pruebas automáticas (salud, CORS, conversión de la URL de Neon) y prueba manual contra un PostgreSQL real con los tres estados: conectada, error y no configurada.
- Web (versión JavaScript): probada en Chromium contra el servidor y PostgreSQL. Muestra "Conectada".
- Web (versión WebAssembly): compila. El empaquetado final lo hace GitHub Actions; en la PoC ya se comprobó que funciona.
- Dockerfile: usa el mismo comando de compilación probado (`:server:installDist`), pero la imagen no se construyó antes de la entrega. Si falla en Render, el log de *Events* indica el paso.
