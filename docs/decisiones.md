# Decisiones de implementación

Las decisiones técnicas que ya están tomadas, con su motivo, para no
reabrirlas. Las generales vienen de la plantilla del equipo (y esta, de un
proyecto anterior con el mismo stack); las del negocio de la tienda se suman
en «Decisiones del proyecto», con fecha, a medida que se toman.

**Antes de resolver una ambigüedad, buscar acá.** Empezar por el índice y leer
solo las secciones que tocan la tarea. Al agregar una decisión, sumarla al
índice.

---

## Índice

- **[Transversales](#transversales)**: [Spring Boot 4.1.1](#spring-boot-411) · [Variables de entorno con prefijo propio](#variables-de-entorno-con-prefijo-propio) · [Despliegue con Docker y un solo origen](#despliegue-con-docker-y-un-solo-origen) · [Base de datos: sin migraciones](#base-de-datos-sin-migraciones) · [Pago: interfaz propia con dos implementaciones](#pago-interfaz-propia-con-dos-implementaciones) · [Imágenes subidas](#imágenes-subidas)
- **[Seguridad](#seguridad)**: [`Usuario` es una sola clase con `rol` enumerado](#usuario-es-una-sola-clase-con-rol-enumerado) · [Autenticación por JWT stateless](#autenticación-por-jwt-stateless) · [Contraseñas con BCrypt](#contraseñas-con-bcrypt) · [Administrador inicial al arrancar](#administrador-inicial-al-arrancar) · [Registro](#registro) · [Inicio de sesión](#inicio-de-sesión) · [Recuperación de contraseña](#recuperación-de-contraseña) · [Datos personales](#datos-personales) · [Gestión de usuarios](#gestión-de-usuarios)
- **[Notificaciones](#notificaciones)**: [Envío de correo detrás de `NotificadorCorreo`](#envío-de-correo-detrás-de-notificadorcorreo) · [Gmail por SMTP, sin servidor de correo propio](#gmail-por-smtp-sin-servidor-de-correo-propio)
- **[Sistema de diseño (frontend)](#sistema-de-diseño-frontend)**: [La aplicación es un producto, no un trabajo académico](#la-aplicación-es-un-producto-no-un-trabajo-académico) · [Paleta y modos](#paleta-y-modos) · [Tipografía](#tipografía) · [Reglas que valen para toda la interfaz](#reglas-que-valen-para-toda-la-interfaz) · [Decisiones técnicas del frontend](#decisiones-técnicas-del-frontend)
- **[Decisiones del proyecto](#decisiones-del-proyecto)**: [Tienda propia, no marketplace](#tienda-propia-no-marketplace) · [Roles de la tienda](#roles-de-la-tienda) · [Pago con Libélula, sin facturación SIAT](#pago-con-libélula-sin-facturación-siat) · [Rediseño del frontend dentro del flujo](#rediseño-del-frontend-dentro-del-flujo) · [Rubro: ferretería, con vista 3D](#rubro-ferretería-con-vista-3d)

---

## Transversales

### Spring Boot 4.1.1

`start.spring.io` ya no ofrece la línea 3.x. Algunos starters cambiaron de
nombre (`spring-boot-starter-webmvc` en lugar de `-web`; los de prueba están
divididos por módulo), y la mayoría de las referencias externas están escritas
para 3.x: tenerlo en cuenta al buscar ejemplos.

### Variables de entorno con prefijo propio

Todas las variables de la aplicación llevan el prefijo del proyecto
(`TIENDA_`).

**Motivo:** la máquina de desarrollo tenía `DB_USER`, `DB_PASSWORD` y `DB_URL`
de otro proyecto a nivel de sistema. Como las variables de entorno le ganan a
los valores por defecto de `application.properties`, con nombres genéricos la
aplicación se conectaba a la base equivocada sin avisar.

### Despliegue con Docker y un solo origen

`docker-compose.yml` levanta tres servicios: `base` (MySQL 8.0), `backend` (la
API) y `frontend` (nginx con el Angular compilado). nginx reenvía `/api` a la
API, así el navegador ve un solo origen: no hace falta CORS y el token viaja
igual que en desarrollo, donde lo hace `proxy.conf.json`. Los secretos van en
un `.env` que no se versiona (`.env.example` dice cuáles son).

- **Lo que se publica:** en la PC, solo `frontend` (puerto 80). La base y la
  API quedan en la red interna.
- **En la nube se suma Caddy:** `docker-compose.nube.yml` lo agrega como el
  único que publica puertos (80 y 443), pide el certificado HTTPS a Let's
  Encrypt y entrega `https://DOMINIO` a `frontend`. El dominio puede ser uno de
  sslip.io armado con la IP del servidor. La instalación la hace
  `scripts/instalar-servidor.sh`.
- **Perfil `prod`** (`application-prod.properties`): sin el SQL en los
  registros y con `server.forward-headers-strategy=native`, para que el límite
  de intentos vea la IP real del cliente y no la del proxy.
- **Los datos en volúmenes:** la base, las imágenes subidas y, en la nube, los
  certificados de Caddy. Sobreviven a rehacer las imágenes.
- **La imagen de la API no corre las pruebas:** corren en desarrollo, antes de
  cada PR y en GitHub Actions. La API corre sin privilegios de root.

### Base de datos: sin migraciones

El esquema lo genera Hibernate (`ddl-auto=update`), sin Flyway ni Liquibase.
IDs `Long` autoincrementales, no UUID. Las pruebas del backend corren sobre H2
en modo MySQL (perfil `test`), sin Testcontainers.

### Pago: interfaz propia con dos implementaciones

`IPago` (`service/pago/`) tiene un **simulador local**, que se usa por defecto
en desarrollo y pruebas, y un **adaptador del proveedor real**, que se escribe
cuando exista la cuenta. Se elige con `tienda.pago.proveedor`
(`simulador` | el nombre del proveedor), y las credenciales del adaptador van
en variables `TIENDA_PAGO_*` del `.env`.

- El circuito es el de un pago por QR: **crear el cobro** (la deuda, con su QR)
  y **consultar su resultado**. El aviso del proveedor (callback) termina en la
  misma consulta.
- **El pago se confirma consultando al sistema de pago,** nunca por lo que
  muestre o diga el cliente.
- **El nombre del proveedor aparece solo en su adaptador y en su
  configuración;** nunca en entidades, servicios, controladores ni en la API.
- **Un vencimiento, si lo hay, lo controla el sistema,** no el proveedor.
- **El manual del proveedor no se versiona** (`referencias/` está en
  `.gitignore`): el repositorio puede ser público.

### Imágenes subidas

`AlmacenImagenes` guarda imágenes en una carpeta del servidor, configurable con
`TIENDA_IMAGENES_DIRECTORIO` (un volumen en Docker).

- El backend comprueba que sea JPG, PNG o WebP **por su contenido** (los
  primeros bytes, `FormatoImagen`), no por la extensión ni por el tipo que
  declara el navegador, y que no pase de 2 MB.
- **El nombre del archivo lo genera el sistema;** el que trae la persona se
  descarta. La tabla guarda solo ese nombre, y ninguna ruta se arma con lo que
  manda el cliente.
- Se sirve desde el backend, por el registro al que pertenece.

**Motivo:** una URL externa haría depender la imagen de un sitio ajeno, que
además vería cada visita; guardarla en la base agranda la base y sus respaldos.

---

## Seguridad

### `Usuario` es una sola clase con `rol` enumerado

Roles **fijos**: `@Enumerated(EnumType.STRING)` sobre un campo `rol`, sin
`@Inheritance` ni subclases Java, y sin RBAC (no se crean roles ni se
configuran permisos). Son `CLIENTE`, `ADMINISTRADOR`,
`EMPLEADO` y `CONTADOR` (qué hace cada uno: «Decisiones del proyecto» → «Roles
de la tienda»).

**Motivo:** un Administrador puede cambiar el rol de un usuario existente. Con
`SINGLE_TABLE`, el discriminador fija la clase Java al insertar y JPA no deja
actualizarlo: cambiar el rol obligaría a borrar y recrear la fila, lo que
cambia el id y rompe el historial.

**El control de acceso se valida en el backend** (`SecurityConfig`). Ocultar
opciones en Angular según el rol es usabilidad, no seguridad.

### Autenticación por JWT stateless

Spring Security con el token en el header `Authorization`.

- El token vence a las **8 horas**, **sin refresh token**: cubre una jornada.
- Se firma con HS256 y la clave de `TIENDA_JWT_SECRETO` (base64, al menos
  256 bits). No tiene valor por defecto: si falta o es corta, la aplicación no
  arranca, porque con una clave conocida cualquiera podría fabricar tokens.
- El token lleva el id del usuario (`sub`), `iat`, `exp` y `hc`, una huella de
  la contraseña: HMAC-SHA256 del hash BCrypt con la clave del token, truncado a
  16 bytes, que no permite reconstruir el hash. El rol, el estado y la huella se
  comparan con la base en cada petición: si se desactiva una cuenta, se le
  cambia el rol o la contraseña, vale desde la siguiente petición y no recién
  al vencer el token.
- Un token inválido o vencido no autentica, pero tampoco bloquea la petición:
  los endpoints públicos la atienden igual, para que un token viejo guardado en
  el frontend no impida volver a iniciar sesión.

### Contraseñas con BCrypt

A través del `PasswordEncoder` de Spring Security. Mínimo 8 caracteres y
**máximo 72 bytes en UTF-8**: BCrypt ignora lo que pasa de 72 bytes, y sin el
máximo dos contraseñas largas distintas serían equivalentes.

### Administrador inicial al arrancar

`AdministradorInicial`: al arrancar, si no existe ningún usuario con rol
ADMINISTRADOR, crea uno con `TIENDA_ADMIN_CORREO` y la contraseña hasheada de
`TIENDA_ADMIN_PASSWORD`. Es idempotente y no deja un hash escrito en un
archivo versionado, como haría un `data.sql`.

- Si no hay ningún administrador y faltan las variables, o no cumplen las
  reglas del registro, la aplicación **no arranca**: un despliegue sin
  administradores no se puede recuperar desde la interfaz. Si ya existe uno,
  las variables no se leen.
- Si el correo configurado ya pertenece a otra cuenta, tampoco arranca: los
  roles solo los asigna un Administrador.

### Registro

- Todo usuario que se registra por sí mismo obtiene rol **Cliente**, y la
  cuenta queda **activa**, sin confirmación por correo.
- El correo se guarda en minúsculas y sin espacios alrededor, y los duplicados
  se buscan igual: `Juan@mail.com` y `juan@mail.com` son la misma cuenta. El
  inicio de sesión lo normaliza del mismo modo.
- *08/10/2026 (HU-01).* **Al crear la cuenta, la sesión queda iniciada:** el
  registro responde con un token como el inicio de sesión, y la persona vuelve
  a la pantalla de la que venía. **Motivo:** volver a escribir la contraseña
  recién elegida era fricción antes de comprar.
- *08/10/2026 (HU-01).* **Límite de registros:** 5 cuentas creadas desde la
  misma IP en 1 hora bloquean el registro desde esa IP durante 1 hora contada
  desde la quinta (429), como el bloqueo del inicio de sesión. Solo cuentan los registros exitosos; los contadores viven en
  memoria, como los del inicio de sesión. **Motivo:** el registro es público
  y sin límite permite crear cuentas falsas en masa.
- *08/10/2026 (HU-01).* El teléfono es opcional y de texto libre; el contacto
  para la entrega se pide al confirmar el pedido (HU-12). Con la sesión
  iniciada, «Crear cuenta» lleva al inicio.

### Inicio de sesión

- Correo inexistente y contraseña incorrecta devuelven el mismo mensaje, y el
  correo inexistente también ejecuta una comparación BCrypt: así no se revela
  qué correos tienen cuenta, ni por el texto ni por la demora.
- La cuenta desactivada solo se informa cuando la contraseña es correcta.
- Una contraseña de más de 72 bytes se rechaza como incorrecta, pero se compara
  igual, para que la demora no cambie.
- **Límite de intentos:** 5 fallidos para el mismo correo desde la misma IP en
  15 minutos bloquean ese correo desde esa IP durante 15 minutos (429
  `INTENTOS_EXCEDIDOS`), aunque la contraseña sea correcta. Un inicio exitoso
  reinicia el conteo, y restablecer la contraseña también (para ese correo
  desde todas las IPs). Los correos inexistentes también cuentan. Los
  contadores viven en memoria: se pierden al reiniciar y suponen una sola
  instancia del backend.

### Recuperación de contraseña

- Mismo mensaje siempre al solicitarla, exista o no el correo.
- Token: 256 bits aleatorios (`SecureRandom`, base64url), sin relación con el
  JWT. **La base guarda su SHA-256, no el token** (tabla
  `recuperacion_contrasena`): quien lea la base no puede usar una fila recién
  creada. Vigencia: **1 hora**. Una solicitud nueva invalida las anteriores.
- Token inexistente, vencido o ya usado responden el mismo mensaje.
- **Límite de solicitudes:** 3 para el mismo correo desde la misma IP en 15
  minutos (429 `SOLICITUDES_EXCEDIDAS`): cada solicitud aceptada dispara un
  correo real.

### Datos personales

- Se pueden modificar nombre, apellido y teléfono, con las reglas del registro.
  **El correo no se modifica:** es la identidad y el destino de la
  recuperación. El rol y el estado solo los cambia un Administrador.
- **Cambiar la contraseña exige la actual,** y una actual incorrecta cuenta en
  el límite de intentos del inicio de sesión. La respuesta trae un token nuevo:
  la huella `hc` invalida todos los anteriores, así las demás sesiones abiertas
  se cierran.

### Gestión de usuarios

Solo el Administrador. Operaciones rechazadas, cada una con 409 y su propio
código:

1. Un administrador no puede modificar su propio rol.
2. Un administrador no puede desactivar su propia cuenta.
3. No se puede quitar el rol ni desactivar al **último administrador activo**.
4. No se puede cambiar el rol de una cuenta desactivada; primero hay que
   reactivarla.

- **El último administrador activo se protege con bloqueo pesimista:** si dos
  administradores se quitan el rol entre sí al mismo tiempo, la operación
  bloquea las filas de los administradores activos antes de contarlos, siempre
  en el orden administradores → cuenta afectada. Si se agota la espera, 409
  `OPERACION_SIMULTANEA`. La tabla `usuario` lleva un índice por `rol` y
  `estado` para que ese bloqueo no recorra toda la tabla.
- **Listado paginado:** 20 por página (100 como máximo), del más reciente al
  más antiguo, con filtro por rol y estado y búsqueda en correo, nombre y
  apellido. No muestra el teléfono.
- **Aceptado:** al reactivar una cuenta, los tokens emitidos antes de
  desactivarla vuelven a valer hasta que vencen. Para cortar de verdad las
  sesiones de una cuenta hay que cambiarle la contraseña.

---

## Notificaciones

### Envío de correo detrás de `NotificadorCorreo`

Mismo patrón que `IPago`: un **simulador** que solo escribe en el log lo que
habría enviado (incluido el token de recuperación en claro: **nunca activo en
producción**) y un **adaptador SMTP**. Se elige con
`tienda.correo.proveedor`: `simulador` (por defecto, y siempre en las
pruebas) o `smtp`.

**El envío no puede cambiar la respuesta de quien lo pide.** La recuperación
responde igual exista o no la cuenta, pero el correo solo sale si existe: si el
envío tardara o fallara dentro de la petición, la demora o un 500 revelarían
qué correos tienen cuenta. El adaptador SMTP envía en segundo plano (`@Async`)
y registra los fallos en el log sin propagarlos. Cada correo va en texto plano
y en HTML, y en el HTML todo dato que escribió un usuario se escapa.

Cada correo nuevo es un método más de la interfaz, con su versión en el
simulador y en el adaptador.

### Gmail por SMTP, sin servidor de correo propio

El adaptador SMTP envía por `smtp.gmail.com:587` con STARTTLS, con una **cuenta
de Gmail del proyecto** y una **contraseña de aplicación** (no la contraseña
de la cuenta). Usuario y contraseña van solo en el `.env`
(`TIENDA_CORREO_USUARIO`, `TIENDA_CORREO_CONTRASENA`); el `.env.example`
los deja vacíos. La marca que firma los correos sale de `TIENDA_MARCA`.

**Motivo:** un servidor de correo propio (Postfix, webmail) sumaba dos
contenedores, buzones y configuración solo para entregar dentro del propio
dominio. Gmail entrega a cualquier dirección real sin mantener nada. Gmail
manda siempre desde la cuenta autenticada, así que el remitente es esa cuenta.

---

## Sistema de diseño (frontend)

### La aplicación es un producto, no un trabajo académico

En la interfaz no aparece la universidad, la materia ni la metodología: ni en
los textos, ni en el pie de página, ni en los correos. El nombre de la marca
vive en un único lugar del frontend (`core/marca.ts`) y del backend
(`TIENDA_MARCA`), para poder cambiarlo sin tocar las pantallas.

### Paleta y modos

Los valores están en `frontend/src/styles/_tokens.scss`, con el prefijo
`--app-`. **El oscuro es el modo por defecto;** un botón con sol o luna en el
encabezado alterna al claro, con o sin sesión, y la elección se guarda en el
navegador (`localStorage`, clave `app.tema`). El claro redefine los tokens en
`:root[data-tema='claro']`; un script mínimo en `index.html` lo aplica antes de
que arranque Angular, para que la página no aparezca oscura un instante.

*09/10/2026 (chore #8).* La paleta es la del prototipo del visor 3D:
**grafito con amarillo de señalización**, el de las herramientas y los avisos
de obra, que va con el rubro (ferretería). El amarillo tiene dos tokens: el de
relleno (`--app-ambar`: botones, logotipo, la raya del encabezado), con texto
oscuro encima en los dos modos, y el de texto (`--app-acento-texto`: enlaces,
foco, la pestaña actual), que en el modo claro es un ocre oscuro porque el
amarillo no llega a 4.5:1 sobre el fondo claro. **Se puede cambiar,** con una
condición: los contrastes se miden contra el fondo con la fórmula de luminancia
de WCAG, uno por uno, antes de fijar un color (4.5:1 en texto, 3:1 en bordes
de control), en los dos modos.

### Tipografía

*09/10/2026 (chore #8).* **Barlow Condensed** para los títulos (600/700),
como la rotulación de una ferretería; **Barlow** para el texto (400/500/600) y
**JetBrains Mono** para las etiquetas de los campos, los precios y los datos
(400/500). De Google Fonts y con su pila de reserva. Las etiquetas van en
mayúsculas y espaciadas, como un rótulo. Escala de 12, 14, 16 (base), 18, 20,
22, 28 y 34 px; interlineado 1.5 en el texto y 1.1 en los títulos. Sobre fondo
oscuro el texto fino se deshace, así que el cuerpo no baja de 400.

### Reglas que valen para toda la interfaz

- **El estado nunca depende solo del color:** cada etiqueta lleva su texto y una
  forma propia.
- **Errores junto al campo y resumidos al principio del formulario:** el resumen
  recibe el foco al fallar el envío y cada línea lleva a su campo.
- **Animaciones permitidas:** entradas y salidas de pantallas, listas,
  diálogos y avisos; estados de carga (esqueletos, indicadores); respuesta a
  cada acción (hover, clic, envío). Con CSS y `animate.enter` /
  `animate.leave` de Angular, **sin `@angular/animations`** (deprecado) ni
  librerías de animación. Duración de 150 a 400 ms; solo `transform` y
  `opacity` (no `width`, `height` ni `top`); las duraciones y curvas salen de
  tokens (`--app-duracion-*`, `--app-curva-*`). Ninguna animación bloquea al
  usuario ni retrasa lo que pidió. **Todo se apaga con
  `prefers-reduced-motion`.**
- **Áreas tocables de 44 px** y foco visible en todo lo que se use con teclado.
- **Iconos con [Lucide](https://lucide.dev)** (`@lucide/angular`, el paquete oficial actual; `lucide-angular` no soporta Angular 22), la librería
  de iconos abierta más usada: trazo uniforme, licencia ISC y se importa icono
  por icono, así que el bundle solo lleva los que se usan. Nunca emojis. Un
  icono solo, sin texto, lleva `aria-label`; uno junto a su texto,
  `aria-hidden="true"`. El tamaño y el color salen de tokens.
- **Sin librerías de componentes ni de estilos** (Angular Material, PrimeNG,
  Tailwind, Bootstrap): Lucide es la única excepción, y solo para iconos.
- **Espaciado** en múltiplos de 4 px; radio de 8 px en tarjetas y 6 px en
  campos y botones (más recto, como el prototipo); ancho máximo del contenido de 1120 px.
- **Lo angosto va centrado,** con el mixin `pagina-angosta` de
  `frontend/src/styles/_pagina.scss`, sin `max-width` suelto.
- **Estilos solo por tokens,** y componentes estilados con `:host`.

### Decisiones técnicas del frontend

- **Tres capas en `frontend/src/app/`:** `features/` (una carpeta por pantalla,
  con su componente, estilos y pruebas), `core/` (sesión, servicios HTTP,
  interceptores, guards, modelos) y `shared/` (piezas sin negocio). `core/`
  nunca importa de `features/`, y `shared/` no importa de ninguno de los dos.
- **Una pantalla por ruta, con `loadComponent`;** un servicio por área de la API.
- **Lecturas con `httpResource`, escrituras con `HttpClient`.**
- **Formularios nuevos con Signal Forms** (`@angular/forms/signals`, estable en
  Angular 22).
- **Interceptores funcionales:** uno agrega el token, otro traduce los errores y
  cierra la sesión ante un 401. Un 403 no cierra la sesión.
- **El token se guarda en `localStorage`,** dentro de `try/catch` (en modo
  privado puede fallar), en `core/sesion/almacen-token.ts`. **Riesgo asumido:**
  ante un XSS el token sería legible; Angular escapa el HTML por defecto y no se
  usa `bypassSecurityTrust*`.
- **Un solo diccionario traduce los códigos de error del backend**
  (`core/api/mensajes-error.ts`): el mismo error dice siempre lo mismo. El
  `detail` del backend nunca se muestra tal cual.
- **Pruebas con Vitest sobre jsdom,** sin Karma ni Jasmine, y **sin pruebas de
  extremo a extremo automatizadas:** el flujo completo se prueba a mano contra el
  backend levantado.

---

## Decisiones del proyecto

Las de la tienda, con fecha y motivo, a medida que se toman (al escribir una
historia, en un plan, en una retro). Si una cambia algo de arriba, se dice qué
reemplaza.

### Tienda propia, no marketplace

*08/10/2026.* Tienda Montero vende sus propios productos: catálogo, carrito,
pedidos, inventario y reportes. No hay vendedores externos ni comisiones.

**Motivo:** cabe en el plazo (2 meses y medio) y mantiene un solo inventario y
un solo dueño del dinero.

### Roles de la tienda

*08/10/2026.* Cuatro roles fijos (el enum `Rol`):

| Rol | Qué hace |
|---|---|
| Cliente | Compra: catálogo, carrito, pedidos propios. Es el rol de todo el que se registra solo |
| Administrador | Gestiona usuarios y roles, y todo lo de la tienda |
| Empleado | Pedidos, despacho e inventario |
| Contador | Reportes y dinero, **solo lectura** |

El detalle de cada permiso se ajusta en las historias del backlog.

### Pago con Libélula, sin facturación SIAT

*08/10/2026.* El proveedor real es **Libélula**, detrás de `IPago`; hasta que
exista la cuenta se usa el simulador. Sin facturación electrónica del SIAT.

**Motivo:** el pago real depende de una cuenta que todavía no hay, y la
facturación SIAT queda fuera del alcance. El manual del proveedor y cualquier
clave ajena **nunca** entran al repositorio (público).

### Rediseño del frontend dentro del flujo

*08/10/2026.* El frontend se rediseña dentro del proyecto, no en la plantilla:
un `chore/sistema-de-diseno` en el sprint 1 (paleta de la marca, tokens de
animación, Lucide, las pantallas que ya existen), y después cada historia nace
con el diseño nuevo. Lo genérico (tokens, animaciones base, `shared/`) vuelve
a la plantilla antes de la skill `nuevo-proyecto`.

**Motivo:** el diseño depende de la marca, y las pantallas que más lo muestran
(catálogo, producto, carrito) todavía no existen.

### Rubro: ferretería, con vista 3D

*08/10/2026.* Tienda Montero es una ferretería: herramientas, hogar y jardín.
Los productos pueden tener, además de sus fotos, un **modelo 3D** que el
cliente gira, acerca, aleja y mueve (HU-15 y HU-16).

- El modelo 3D es **opcional** y se hace **fuera de la aplicación** (se
  descarga, se escanea o se modela aparte). Un producto se crea sin modelo y
  el modelo se carga después, cuando exista; sin modelo, el producto se ve
  solo con sus fotos.
- Los modelos de demostración salen de [Poly Haven](https://polyhaven.com/models),
  con licencia CC0.

**Motivo:** el rubro se eligió por los modelos 3D gratuitos que existen. Poly
Haven tiene unos 70 modelos de herramientas con licencia CC0 y calidad
realista, y el prototipo del spike (#14) los mostró con `<model-viewer>` sin
problemas.

