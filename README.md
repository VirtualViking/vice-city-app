# Vice City App

PMV para pedir y votar la música de un establecimiento mediante un código QR. Proyecto nuclear de Construcción de Aplicaciones Móviles y Big Data.

Estado: **módulo de acceso y búsqueda para clientes**. El servidor ya resuelve el QR, identifica el dispositivo, busca en YouTube con caché y sugiere mientras se escribe; las pantallas de la web llegan en el siguiente Pull Request.

## Arquitectura

| Módulo | Tecnología | Despliegue |
| --- | --- | --- |
| `webApp` | Kotlin/Wasm + respaldo JS, Compose Multiplatform, PWA | GitHub Pages |
| `server` | Ktor (JVM 21), Exposed, HikariCP | Render (Docker, plan gratuito) |
| `shared` | Modelos y rutas comunes (kotlinx.serialization) | Lo usan los dos anteriores |
| Base de datos | PostgreSQL | Neon (plan gratuito); en local, Docker |

```
vice-city-app/
├── shared/src/commonMain/kotlin/vicecity/compartido/   # rutas (Api.kt) y modelos del contrato de la API
├── server/src/main/kotlin/vicecity/servidor/
│   ├── Application.kt, Configuracion.kt, BaseDatos.kt, Errores.kt
│   ├── datos/          # tablas de Exposed y repositorios (consultas SQL)
│   ├── dominio/        # reglas puras: normalizar textos, deducir el artista, enlace del QR
│   ├── integraciones/  # servicios externos detrás de interfaces: YouTube, generador de QR
│   ├── seguridad/      # contraseñas (bcrypt), tokens JWT y huella del dispositivo (HMAC)
│   ├── servicios/      # reglas de negocio: acceso, búsqueda, sugerencias, QR, sesiones, eventos
│   └── rutas/          # endpoints REST
├── server/src/main/resources/db/migracion/            # migraciones Flyway (esquema SQL)
├── server/src/test/kotlin/vicecity/servidor/           # pruebas (unitarias y de integración)
├── webApp/src/webMain/kotlin/vicecity/web/             # App.kt, ClienteApi.kt, Configuracion.kt, main.kt
├── webApp/src/webMain/resources/                       # index.html, config.js, manifest, sw.js, íconos
├── .github/workflows/ci.yml                            # pruebas del servidor en cada push
├── .github/workflows/pages.yml                         # publica la web en GitHub Pages
├── Dockerfile, render.yaml                             # despliegue del servidor en Render
└── docker-compose.yml                                  # PostgreSQL local
```

Versiones: Kotlin 2.4.21 · Compose Multiplatform 1.12.2 · Ktor 3.6.1 · Exposed 1.6.0 · Flyway 13.12.0 · ZXing 3.5.4 · PostgreSQL JDBC 42.7.14 · Gradle 8.14.3 · JDK 21.

Netty (4.2.20) y java-jwt (4.6.2) se fuerzan en `server/build.gradle.kts` porque Ktor 3.6.1 trae versiones con vulnerabilidades conocidas. Se puede quitar cuando Ktor las actualice.

## Flujo de trabajo con Git

- `main` es la rama publicada: sus cambios se despliegan en Render (servidor) y GitHub Pages (web).
- Cada entrega se trabaja en una rama `feature/<tema>`, por ejemplo `feature/acceso-busqueda-api`, con commits pequeños.
- Mensajes de commit con [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/): `feat`, `fix`, `test`, `docs`, `chore`, `refactor`.
- Al terminar, se abre un Pull Request hacia `main` y se fusiona solo con el CI en verde.

## Base de datos

- El esquema está en `server/src/main/resources/db/migracion/` y lo aplica **Flyway** al arrancar el servidor. Nunca se modifica, reformatea ni se vuelve a pegar una migración ya desplegada: Flyway compara su contenido y el servidor no arranca si cambió. Los cambios van en un archivo nuevo (`V3__…sql`, `V4__…sql`).
- `V1`: esquema inicial. `V2`: artista y textos normalizados en `cancion`, tabla `termino_buscado` para el autocompletado.
- En el primer arranque con la base vacía, el servidor crea el establecimiento y la cuenta de administrador con `ESTABLECIMIENTO_NOMBRE`, `ADMIN_EMAIL` y `ADMIN_PASSWORD`. El código QR del establecimiento aparece en el log (*Establecimiento creado … con código QR …*). Después ya no hace nada: cambiar esas variables no cambia la cuenta existente.
- La tabla `evento` es de solo inserción (RNF-15): un *trigger* rechaza cualquier `UPDATE` o `DELETE`.

## Endpoints disponibles

| Método y ruta | Quién | Respuesta |
| --- | --- | --- |
| `GET /api/v1/salud` | Público | Estado del servidor y de la base |
| `POST /api/v1/auth/login` | Público | Token JWT (12 h), rol y nombre (HU-02) |
| `POST /api/v1/admin/sesiones` | ADMIN, DJ | 201 con la sesión abierta; 409 `SESION_YA_ABIERTA` si ya hay una (HU-03) |
| `GET /api/v1/admin/sesiones/activa` | ADMIN, DJ | `{"sesion": …}` o `{}` si no hay sesión abierta |
| `POST /api/v1/admin/sesiones/{id}/cerrar` | ADMIN, DJ | Sesión cerrada y cuántas canciones quedaron sin sonar; 409 `SESION_CERRADA` |
| `GET /api/v1/admin/sesiones?desde=…&hasta=…` | Solo ADMIN | Historial de sesiones (fechas ISO 8601, opcionales) |
| `GET /api/v1/admin/establecimiento/qr?tamano=1024` | Solo ADMIN | Imagen PNG del código QR para imprimir (256 a 2048 px) |
| `GET /api/v1/establecimientos/{codigoQr}` | Cliente | Nombre del establecimiento y su sesión abierta, si hay (RF-01) |
| `GET /api/v1/establecimientos/{codigoQr}/busqueda?q=` | Cliente | Videos musicales con título, artista, canal, duración y miniatura (RF-04) |
| `GET /api/v1/establecimientos/{codigoQr}/sugerencias?q=` | Cliente | Autocompletado con datos propios, sin llamar a YouTube |

Las rutas de cliente exigen el encabezado `X-Dispositivo-Id` con un UUID (lo genera la app). Las rutas `/admin` exigen el encabezado `Authorization: Bearer <token>`. Los errores siguen el formato `{"codigo": "...", "mensaje": "..."}` del contrato.

## Variables de entorno del servidor

| Variable | Ejemplo | Para qué |
| --- | --- | --- |
| `DATABASE_URL` | `postgresql://usuario:clave@ep-xxxx.neon.tech/vicecity?sslmode=require` | Conexión a PostgreSQL. Si falta, el servidor arranca igual y `/salud` dice `NO_CONFIGURADA`. |
| `CORS_ORIGENES` | `https://virtualviking.github.io` | Orígenes de la web separados por coma. En local, por defecto `http://localhost:8080`. |
| `YOUTUBE_API_KEY` | `AIza…` | Clave de la YouTube Data API. Si falta, la búsqueda solo responde con canciones ya guardadas. |
| `JWT_SECRETO` | 40 o más caracteres aleatorios | Firma los tokens del personal. Si falta, se usa una clave temporal y los tokens dejan de valer en cada reinicio. |
| `DISPOSITIVO_SECRETO` | 40 o más caracteres aleatorios | Clave del HMAC que convierte el identificador del celular en `dispositivo_hash`. Si falta, se usa una temporal y los celulares se ven como nuevos en cada reinicio. **No la cambies después**: los mismos celulares tendrían otro hash. |
| `WEB_URL` | `https://virtualviking.github.io/vice-city-app/` | Opcional. Dirección de la app web que va dentro del QR. Por defecto, la de GitHub Pages. |
| `ADMIN_EMAIL` | `tu-correo@ejemplo.com` | Cuenta de administrador que se crea en el primer arranque. |
| `ADMIN_PASSWORD` | una contraseña segura | Contraseña de esa cuenta (se guarda cifrada con bcrypt). |
| `ADMIN_NOMBRE` | `Sebas` | Opcional. Por defecto, "Administrador". |
| `ESTABLECIMIENTO_NOMBRE` | `Vice City Bar` | Opcional. Nombre del establecimiento que se crea en el primer arranque. |
| `PORT` | `10000` | La asigna Render. En local, por defecto `8081`. |

Nunca subas estos valores al repositorio: van en Render (o en tu configuración local de IntelliJ).

## Búsqueda y autocompletado

- **Caché:** cada término se guarda normalizado (minúsculas, sin tildes) durante 7 días, así "Tití" y "titi" no gastan cupo dos veces.
- **Cupo de YouTube:** la API permite unas 100 búsquedas al día. El servidor permite como máximo 10 búsquedas nuevas por dispositivo por hora y 90 al día en total; las de la caché no cuentan. Los valores están en `ReglasBusqueda` (`Configuracion.kt`).
- **Respaldo:** si se llega a un límite o YouTube no responde, busca en las canciones ya guardadas (`"fuente": "LOCAL"`). Solo si no hay ninguna responde `429 LIMITE_BUSQUEDAS` o `503 YOUTUBE_NO_DISPONIBLE`.
- **Filtros:** solo videos de la categoría Música, que se pueden incrustar, públicos, que no sean transmisiones en vivo y disponibles en Colombia. Los que superan la duración máxima del establecimiento (8 min) no se muestran.
- **Artista:** YouTube no tiene ese campo. Se deduce del canal ("Bad Bunny - Topic", "KarolGVEVO") o del título ("Artista - Canción"). Funciona con la mayoría de los videos oficiales, no con todos.
- **Autocompletado:** sugiere lo más buscado en el establecimiento, después artistas y después canciones ya vistas. Nunca llama a YouTube.

## Descargar e imprimir el código QR

El QR lleva a `https://virtualviking.github.io/vice-city-app/?e=<código del establecimiento>`. Mientras no exista el panel, se descarga con PowerShell:

```powershell
$clave = Read-Host "Contraseña"
$login = Invoke-RestMethod -Method Post -Uri "https://vice-city-app.onrender.com/api/v1/auth/login" -ContentType "application/json" -Body (@{ email = "TU_ADMIN_EMAIL"; password = $clave } | ConvertTo-Json)
Invoke-WebRequest -Uri "https://vice-city-app.onrender.com/api/v1/admin/establecimiento/qr?tamano=1024" -Headers @{ Authorization = "Bearer $($login.token)" } -OutFile "qr-vice-city.png"
```

Para imprimirlo: mínimo 5 × 5 cm, en blanco y negro, sin recortar el margen blanco, y probarlo con varios celulares antes de pegarlo. Tiene corrección de errores alta: se sigue leyendo aunque se ensucie o se doble un poco.

## Ejecutar en local

1. **PostgreSQL**, con [Docker Desktop](https://www.docker.com/products/docker-desktop/):
   ```powershell
   docker compose up -d
   ```
   Crea las bases `vicecity` y `vicecity_test` (usuario `vicecity`, clave `vicecity`) en el puerto 5432.

2. **Servidor**, en la terminal de IntelliJ (PowerShell):
   ```powershell
   $env:DATABASE_URL = "postgresql://vicecity:vicecity@localhost:5432/vicecity"
   $env:JWT_SECRETO = "una-clave-larga-solo-para-desarrollo-local"
   $env:ADMIN_EMAIL = "admin@local.test"
   $env:ADMIN_PASSWORD = "clave-local-123"
   ./gradlew :server:run
   ```
   Si la terminal dice `JAVA_HOME is not set`, define `JAVA_HOME` en las variables de entorno de Windows con la ruta de un JDK 21.
   Comprueba en el navegador: <http://localhost:8081/api/v1/salud> → `"baseDatos":"CONECTADA"`.

3. **Web**, en otra terminal:
   ```powershell
   ./gradlew :webApp:wasmJsBrowserDevelopmentRun
   ```
   Abre <http://localhost:8080>. Debe mostrar Servidor "Responde" y Base de datos "Conectada".

4. **Pruebas del servidor:**
   ```powershell
   $env:TEST_DATABASE_URL = "postgresql://vicecity:vicecity@localhost:5432/vicecity_test"
   ./gradlew :server:test
   ```
   Sin `TEST_DATABASE_URL` solo corren las pruebas que no usan base de datos. **Las pruebas de integración borran la base completa**, por eso se niegan a correr si el nombre de la base no termina en `_test`. En GitHub Actions corren siempre, con un PostgreSQL propio del CI.

## Configurar la nube (una sola vez)

### 1. Neon (PostgreSQL)

1. Crea una cuenta en <https://neon.com> (no pide tarjeta).
2. Crea un proyecto llamado `vice-city`, con región **AWS US East (N. Virginia)**, la misma del servidor en Render.
3. En el proyecto, crea la base de datos `vicecity` o usa la que viene por defecto.
4. En **Connect**, copia la *connection string* (`postgresql://…?sslmode=require`). Es el valor de `DATABASE_URL`.

### 2. Render (servidor)

1. Crea una cuenta en <https://render.com> con tu cuenta de GitHub.
2. **New → Blueprint** → elige el repositorio `vice-city-app`. Render lee `render.yaml` y crea el servicio `vice-city-api`.
3. Te pedirá `DATABASE_URL` (la de Neon), `YOUTUBE_API_KEY`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `ESTABLECIMIENTO_NOMBRE`. `JWT_SECRETO` la genera Render. Si creaste el servicio a mano (*New → Web Service*), agrega esas variables en **Environment**; para `JWT_SECRETO` usa el botón **Generate**.
4. Espera el primer despliegue y copia la URL del servicio. La de este proyecto es `https://vice-city-app.onrender.com`. Pruébala: `…/api/v1/salud`.

Con el Blueprint, se redespliega solo cuando cambian `server/`, `shared/` o la configuración de Gradle, y solo si el CI de GitHub pasa. Si creaste el servicio a mano, en **Settings → Auto-Deploy** elige **After CI Checks Pass** para tener el mismo comportamiento.

> El plan gratuito se duerme tras 15 minutos sin tráfico. La primera petición después tarda cerca de un minuto. Durante una sesión no se duerme, porque la vista pública enviará un mensaje periódico por WebSocket (iteración 1).

### 3. GitHub Pages (web)

1. En el repositorio: **Settings → Pages → Source: GitHub Actions**.
2. **Settings → Secrets and variables → Actions → pestaña Variables → New repository variable**:
   - Name: `API_BASE`
   - Value: la URL de Render, sin `/` al final (`https://vice-city-app.onrender.com`).
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

## Verificación del módulo de acceso y búsqueda (servidor)

- 46 pruebas automáticas. Las nuevas cubren el acceso por QR y la identificación del dispositivo, la búsqueda (caché, términos escritos distinto, caché vencida, límites por dispositivo y diario, respaldo local, YouTube caído), el autocompletado (tildes, cualquier palabra, comodines), el QR (se decodifica con un lector real y solo lo descarga el ADMIN) y el adaptador de YouTube contra respuestas simuladas (filtros, orden, clave en header y nunca en la URL, cuota agotada).
- Las pruebas no llaman a YouTube: usan un catálogo simulado. La primera búsqueda real se comprueba en Render con la clave configurada.

## Verificación de la entrega 1.1

- 15 pruebas automáticas: 4 sin base de datos y 11 de integración contra PostgreSQL real (16 en local, 17 en el CI). Cubren migraciones, datos iniciales sin duplicar, login correcto e incorrecto, cuenta inactiva, token alterado, sesiones (abrir, duplicada, activa, cerrar con canciones pendientes y sus eventos), permisos del DJ, aislamiento entre establecimientos, filtro de fechas y el *trigger* de la tabla `evento`.
- Prueba manual del servidor compilado: primer arranque con base vacía (migra y crea establecimiento y admin), login, abrir sesión y consultar la activa; segundo arranque sin migrar ni duplicar datos.
- En el CI, el paso *Pruebas del servidor* lista cada prueba con su resultado (`PASSED`, `SKIPPED` o `FAILED`).
- El despliegue en Render y la migración sobre Neon se verifican al fusionar en `main`, en el log de Render.

## Verificación de la estructura base (paso 5)

- Servidor: 3 pruebas automáticas (salud, CORS, conversión de la URL de Neon) y prueba manual contra un PostgreSQL real con los tres estados: conectada, error y no configurada.
- Web (versión JavaScript): probada en Chromium contra el servidor y PostgreSQL. Muestra "Conectada".
- Web (versión WebAssembly): compila. El empaquetado final lo hace GitHub Actions; en la PoC ya se comprobó que funciona.
- Dockerfile: usa el mismo comando de compilación probado (`:server:installDist`), pero la imagen no se construyó antes de la entrega. Si falla en Render, el log de *Events* indica el paso.
