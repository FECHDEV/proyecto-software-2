# Visibilidad en buscadores y enlaces, y eficiencia

Trabajo técnico sin historia (`docs/flujo-sdd.md` → «Trabajo técnico sin
historia»): tres PR cortos, `chore/…`, con el CI en verde. Las fotos
achicadas van aparte, como historia (abajo, «Fuera de este plan»).

Punto de partida (`main` en 173f240, revisado el 28/10/2026):

- `index.html` solo tiene `<title>`; sin descripción, Open Graph, `robots`
  ni `robots.txt`. La aplicación se arma en el navegador (sin SSR).
- Las rutas tienen título; el detalle pone el del producto. `Meta` no se usa.
- **Las fotos de productos se sirven sin `Cache-Control`**
  (`ProductosController` → `GET /api/productos/{id}/imagenes/{imagen}`): el
  navegador las vuelve a pedir en cada visita. El modelo 3D sí lleva un año.
- Las fotos se guardan y se sirven tal como se suben, hasta 2 MB; una tarjeta
  del catálogo las muestra a unos 200–300 px.
- Tres familias desde Google Fonts (8 pesos); JetBrains Mono solo en 6
  selectores, 5 de ellos del panel.
- Caddy comprime solo con gzip. Ningún contenedor tiene límite de memoria;
  la JVM arranca sin opciones y MySQL con `performance_schema` encendido.
- `favicon.ico` cae en la regla de nginx de «un año, inmutable» sin tener hash.

## PR 1 — `chore/visibilidad`: buscadores y enlaces compartidos

1. **Que no se indexe la demo, pero los enlaces sí muestren su tarjeta.**
   - `frontend/public/robots.txt`: `Allow: /`, `Disallow: /api/` y las
     rutas privadas (`/carrito`, `/confirmar-pedido`, `/mis-pedidos`,
     `/mis-datos`) y las de los enlaces con token de un solo uso
     (`/restablecer`, `/verificacion`). Sin las rutas del panel: el archivo es público y
     listarlas solo dice dónde están.
   - `nube/Caddyfile`: `X-Robots-Tag "noindex"` en `header`, con el
     comentario «quitar con un dominio propio».
   - *Decisión:* `noindex` en la cabecera y no `Disallow: /`. Con `Disallow`
     Google puede listar la dirección igual, sin texto, y los bots de vista
     previa que respetan `robots.txt` (Twitterbot) no arman la tarjeta.
     `noindex` saca la demo de Google sin cortar las tarjetas de WhatsApp,
     Facebook o Telegram.
   - *Decisión:* en el Caddyfile y no en `index.html`: solo en el servidor
     público, y se quita en una línea cuando haya dominio propio.
2. **Descripción y tarjeta para compartir** en `frontend/src/index.html`:
   `description`, `og:type`, `og:site_name`, `og:title`, `og:description`,
   `og:image` (+ `og:image:width`, `og:image:height`, `og:image:alt`) y
   `twitter:card` `summary_large_image`. Texto de usted, sin exagerar
   (`PRODUCT.md`), por ejemplo: «Ferretería de Montero: herramientas, hogar y
   jardín. Vea si hay stock, compre en línea y retire en la tienda.»
   - **Imagen** `frontend/public/compartir.jpg`, 1200×630, menos de 150 kB:
     la marca con los tokens de `DESIGN.md` (fondo de lona verde, «M» en el
     cuadro ámbar, «Tienda Montero» y una línea). Se arma con un HTML y una
     captura de Playwright; se versiona el JPG, no el HTML.
   - **`og:image` necesita la dirección completa** y el dominio cambia en
     cada servidor (sslip.io o propio): `index.html` lleva
     `__ORIGEN__/compartir.jpg` y nginx lo reemplaza con `sub_filter` por
     `TIENDA_SITIO_URL`, que la imagen de nginx completa al arrancar
     (`envsubst` sobre `/etc/nginx/templates`, solo las variables `TIENDA_`).
     Los dos `docker-compose` le pasan la variable al frontend.
     - *Decisión:* de la configuración y no del `Host` de la petición, que lo
       manda el cliente (revisión de código).
     - *Decisión:* el módulo `sub_filter` ya viene en la imagen oficial de
       nginx; sin un paso de compilación por entorno.
   - *Límite conocido:* WhatsApp y Facebook no ejecutan JavaScript: **todos
     los enlaces muestran la misma tarjeta**, también el de un producto. La
     tarjeta por producto necesita SSR (fuera de este plan).
3. **Descripción por pantalla** (le sirve a Google cuando se indexe):
   - `core/navegacion/titulo-y-descripcion.ts`: `TituloYDescripcion extends
     TitleStrategy`, registrado en `app.config.ts`; pone el título de la ruta
     y `<meta name="description">` desde `data.descripcion`, o la general si
     la ruta no tiene.
   - `app.routes.ts`: `descripcion` en el catálogo; el inicio usa la general,
     que ya lo describe.
   - `detalle.page.ts`: en el mismo `effect` del título, la descripción del
     producto recortada a 155 caracteres (o la general si no tiene).
   - Pruebas (`titulo-y-descripcion.spec.ts`, `detalle.page.spec.ts`):
     `laRutaPoneSuDescripcion`, `sinDescripcionQuedaLaGeneral`,
     `elDetalleUsaLaDescripcionDelProducto`,
     `unaDescripcionLargaSeRecortaSinCortarPalabras`,
     `unProductoSinDescripcionUsaLaGeneral`.
4. **Compresión:** `encode zstd gzip` en `nube/Caddyfile` (zstd para los
   navegadores que lo aceptan, gzip para los demás).
5. **e2e** (`catalogo.spec.ts`): `robots.txt` responde 200 y no es el
   `index.html`; el `index.html` servido trae `og:image` con
   `http://localhost:8090/compartir.jpg` (el `TIENDA_SITIO_URL` del entorno)
   y la descripción y el nombre de `core/marca.ts`, que `index.html` repite.
6. **Docs:** `decisiones.md` → «Buscadores y enlaces compartidos» (y el
   índice); README → «Despliegue»: quitar `X-Robots-Tag` con dominio propio.

## PR 2 — `chore/cache-y-fuentes`: menos descargas

1. **Fotos en caché un año** (`ProductosController`): la respuesta de
   `GET /api/productos/{id}/imagenes/{imagen}` lleva `GUARDAR_UN_ANO`, como
   el modelo 3D.
   - *Decisión:* sin riesgo de ver una foto vieja: el nombre es un UUID que
     no se reutiliza; reemplazar una foto crea otro nombre.
   - La imagen de categoría **no**: conserva el nombre al reemplazarse. Va
     con `no-cache` y `ETag`, para que el navegador revalide con un 304. Hoy
     solo se ve en el panel.
   - Pruebas: `ProductosIntegracionTest.laFotoSeGuardaUnAno` y
     `CategoriasIntegracionTest.laImagenDeCategoriaSeRevalida` (con el mismo
     `ETag`, 304 sin cuerpo; con otra imagen, la nueva).
2. **`favicon.ico`** fuera de la regla de un año en `nginx.conf`: `no-cache`,
   como `texturas/`.
3. **Fuentes servidas por la tienda**, sin Google Fonts:
   - `frontend/public/fuentes/`: los `woff2` de los 8 pesos actuales, en el
     subconjunto latino (el español entra entero) y el latino extendido (el
     guaraní), cada uno con su `unicode-range`, con la versión en el
     nombre (`barlow-400-v13.woff2`, la versión de Google Fonts), más las
     licencias SIL Open Font. JetBrains Mono es variable: un archivo para sus
     dos pesos, 7 en total.
     Se bajan una vez de Google Fonts; sin dependencias nuevas.
   - `styles/_fuentes.scss`: `@font-face` con `font-display: swap`.
   - `index.html`: sin los `preconnect` ni el enlace a Google; `preload` de
     los dos pesos que se ven primero (Barlow 400 y Barlow Condensed 800).
   - *Decisión:* nombre con versión → entran en la regla de un año de nginx.
   - *Decisión:* menos conexiones (la página ya no espera a dos dominios de
     Google) y Google deja de ver cada visita.
   - **A decidir con Fabio:** JetBrains Mono solo aparece en los códigos de
     producto (detalle y 4 pantallas del panel). Cambiarla por la mono del
     sistema (`ui-monospace`) ahorra 2 archivos, pero cambia el aspecto:
     toca `DESIGN.md` y se ve con `impeccable`. Por defecto, se queda.
4. **Verificación:** en el navegador, sin pedidos a `fonts.googleapis.com` ni
   a `fonts.gstatic.com`; capturas de inicio y detalle iguales a las de
   antes; consola sin errores.
5. **Docs:** `decisiones.md` → «Imágenes subidas» (el caché) y una
   «Fuentes servidas por la tienda» (y el índice); `DESIGN.md`, la carga.

## PR 3 — `chore/memoria-del-servidor`: que entre holgado en un servidor chico

1. **Medir antes:** `docker compose up` con el catálogo de demostración,
   recorrer el camino principal (`npm run e2e`) y anotar `docker stats` de
   cada contenedor, en reposo y bajo uso.
2. **MySQL** (`docker-compose.yml`, `command`): `--performance-schema=OFF`
   (diagnóstico que no se usa; unos 200–400 MB). `innodb_buffer_pool_size`
   queda en 128 MB, su valor por defecto: la base de la tienda es más chica.
3. **Java** (`backend/Dockerfile`):
   `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"`.
   - *Decisión:* el montón se calcula sobre el límite del contenedor (punto
     4) y no sobre la RAM del servidor. Si se queda sin memoria, el proceso
     termina y Docker lo reinicia (`restart: unless-stopped`), en vez de
     quedar a medias.
4. **Límites por contenedor** (`mem_limit` en `docker-compose.yml`): con lo
   medido en el punto 1 más un margen del 50 %. Referencia: base ~512 MB,
   backend ~768 MB, frontend ~64 MB, Caddy ~128 MB. Con 2 GB de RAM y la
   swap de `instalar-servidor.sh` queda holgado.
5. **Medir después** con el mismo recorrido. Las tres suites y el e2e en
   verde; los números van al «Resultado».
6. **Docs:** `decisiones.md` → «Despliegue con Docker y un solo origen»: los
   límites y el tamaño mínimo del servidor; README → «Despliegue».

**Descartado, con motivo:**

- **Hilos virtuales** (`spring.threads.virtual.enabled`): con el tráfico de la
  tienda no se notan, y en Java 21 un hilo virtual dentro de un
  `synchronized` bloquea su hilo de plataforma (se corrige en Java 24).
- **Grupo de conexiones de Hikari:** el valor por defecto ya es 10, que
  alcanza.
- **gzip en nginx:** en el servidor público ya comprime Caddy.

## Fuera de este plan

- **Fotos achicadas** (el mayor ahorro: una tarjeta pasa de hasta 2 MB a unos
  30 kB). Historia nueva, con `/sdd:historia`: al subir, el backend guarda
  versiones de 400 y 1200 px; el frontend las elige con `srcset`
  (`NgOptimizedImage`); también el cargador de demostración y las fotos ya
  subidas. **Formato a decidir:** JPEG con `ImageIO` del JDK, sin
  dependencias, o WebP (unos 30 % menos), que el JDK no escribe y necesita
  una librería con código nativo o `cwebp` en la imagen Docker.
- **SSR o prerenderizado y `sitemap.xml`:** una tarjeta por producto y mejor
  posición en Google. Cuando la tienda tenga dominio propio.

## Orden

PR 1 → PR 2 → PR 3, uno por vez y con el CI en verde. Cada uno se prueba en
el navegador (escritorio y celular, consola sin errores) antes del PR.

## Resultado

**PR 1 (`chore/visibilidad`):** frontend con 75 archivos y 884 pruebas,
`ng build` sin avisos; 18 pruebas e2e (2 nuevas, más la marca comparada con
`index.html`). En la sesión en la nube Docker no pudo bajar dependencias, así
que el e2e completo corre en el CI. nginx con la plantilla, probado aparte:
con `Host: atacante.com`, `og:image` sigue siendo el `TIENDA_SITIO_URL`.
Una ronda de `/code-review`. **Corregido:** el origen salía del `Host` de la
petición; una descripción de solo espacios; la marca repetida sin prueba; la
puntuación antes de «…»; `/restablecer` y `/verificacion` en `robots.txt`; el
plan desactualizado; la búsqueda recursiva. **Descartado:** que la descripción
del detalle se pierda al reusar la página (la ruta no tiene parámetros de
consulta y el `effect` se repite al cambiar de producto); el emoji partido al
recortar (necesita 155 caracteres seguidos sin espacio). Un hallazgo propio:
un comentario de `index.html` con el marcador hacía que `sub_filter_once`
reemplazara el comentario en vez de la etiqueta.

**PR 2 (`chore/cache-y-fuentes`):** backend con 743 pruebas; frontend con 75
archivos y 885 pruebas, `ng build` sin avisos. La prueba de la foto mostró
que salía con el `no-store` de Spring Security: el navegador no la guardaba
nunca. Con las fuentes propias, inicio, catálogo, detalle e inicio de sesión
quedaron idénticos píxel a píxel a `main`, sin pedidos a Google; la parte
extendida se baja solo con una letra como «ũ». En nginx, con las cabeceras
probadas: pasar `compartir.jpg` a una expresión regular la dejaba en la regla
de un año (gana la primera expresión que coincide); van con `=`. Una ronda de
`/code-review`. **Corregido:** el `?v=Date.now()` de la pantalla de
categorías anulaba el `ETag` (ahora la dirección fija, y un sufijo solo
después de cambiar la imagen); la parte latina extendida que servía Google;
las cifras de las fuentes. **Descartado:** leer el archivo antes de responder
304 (solo en el panel, imágenes de hasta 2 MB); el `nombre` en
`ImagenGuardada` (lo llenan los dos servicios y describe la versión); que una
foto borrada siga un año en el navegador de quien ya la vio (no se vuelve a
listar y quien la tiene ya la vio; es lo que se decidió para los modelos).
