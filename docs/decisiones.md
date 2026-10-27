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
- **[Seguridad](#seguridad)**: [`Usuario` es una sola clase con `rol` enumerado](#usuario-es-una-sola-clase-con-rol-enumerado) · [Autenticación por JWT stateless](#autenticación-por-jwt-stateless) · [Contraseñas con BCrypt](#contraseñas-con-bcrypt) · [Administrador inicial al arrancar](#administrador-inicial-al-arrancar) · [Registro](#registro) · [Inicio de sesión](#inicio-de-sesión) · [Recuperación de contraseña](#recuperación-de-contraseña) · [Verificación del correo](#verificación-del-correo) · [Datos personales](#datos-personales) · [Gestión de usuarios](#gestión-de-usuarios)
- **[Notificaciones](#notificaciones)**: [Envío de correo detrás de `NotificadorCorreo`](#envío-de-correo-detrás-de-notificadorcorreo) · [Gmail por SMTP, sin servidor de correo propio](#gmail-por-smtp-sin-servidor-de-correo-propio) · [Correos del pedido](#correos-del-pedido)
- **[Sistema de diseño (frontend)](#sistema-de-diseño-frontend)**: [La aplicación es un producto, no un trabajo académico](#la-aplicación-es-un-producto-no-un-trabajo-académico) · [Lo visual vive en `DESIGN.md`](#lo-visual-vive-en-designmd) · [Decisiones técnicas del frontend](#decisiones-técnicas-del-frontend)
- **[Decisiones del proyecto](#decisiones-del-proyecto)**: [Tienda propia, no marketplace](#tienda-propia-no-marketplace) · [Roles de la tienda](#roles-de-la-tienda) · [Pago con Libélula, sin facturación SIAT](#pago-con-libélula-sin-facturación-siat) · [Rediseño del frontend dentro del flujo](#rediseño-del-frontend-dentro-del-flujo) · [Rubro: ferretería, con vista 3D](#rubro-ferretería-con-vista-3d) · [Visor 3D y modelos](#visor-3d-y-modelos) · [Categorías del catálogo](#categorías-del-catálogo) · [Productos](#productos) · [Inventario](#inventario) · [Catálogo público](#catálogo-público) · [Detalle del producto](#detalle-del-producto) · [Carrito](#carrito) · [Pedidos](#pedidos) · [Pagos](#pagos) · [Reportes](#reportes) · [Exportar reportes](#exportar-reportes) · [Catálogo de demostración](#catálogo-de-demostración)

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

`docker-compose.yml` levanta tres servicios: `base` (MySQL 8.4), `backend` (la
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

*21/10/2026 (HU-17).* **El estado del pedido es `varchar`, no `enum`.**
Hibernate creaba `pedido.estado` como `enum(...)` de MySQL, y `ddl-auto=update`
no cambia una columna que ya existe: un estado nuevo («Entregado») fallaba en
MySQL y pasaba en H2. Se mapea con `@JdbcTypeCode(SqlTypes.VARCHAR)` y, al
arrancar, `EstadoPedidoAlArrancar` pasa la columna a `varchar(15)` solo si en
MySQL sigue siendo `enum`. Es un cambio de tipo puntual, no una migración: si
hiciera falta otro, se vuelve a decidir acá. En una base nueva Hibernate crea
además un `check` con los estados de ahora, que `ddl-auto=update` tampoco
cambia: un estado nuevo vuelve a pedir esta decisión.

### Pago: interfaz propia con dos implementaciones

`IPago` (`service/pago/`) tiene un **simulador local**, que se usa por defecto
en desarrollo y pruebas, y un **adaptador del proveedor real**, por defecto en
Docker desde el 23/10/2026 (ver «Pagos»)
(`AdaptadorLibelula`, desde HU-13; ver «Pagos»). Se elige con `tienda.pago.proveedor`
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
- *22/10/2026 (HU-15).* La misma carpeta guarda los modelos 3D (`.glb`), con
  las mismas reglas de nombres. Multipart deja pasar hasta 3 MB, el límite de
  un modelo; los 2 MB de una imagen los controla el servicio.

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

*23/10/2026 (antes de publicar).* **Todo lo que escribe una cuenta la lee
bloqueada** (`findConBloqueoByIdUsuario`). `Usuario` se guarda entero (sin
`@DynamicUpdate` ni `@Version`): una lectura sin bloqueo reescribía el estado
o el rol anteriores sobre lo que un Administrador cambió al mismo tiempo. Lo
corrigen «Mis datos» (`UsuarioService.actualizarDatos`) y restablecer la
contraseña, que ahora bloquea la cuenta antes que el enlace, como verificar el
correo.

### Autenticación por JWT stateless

Spring Security con el token en el header `Authorization`.

- El token vence a las **8 horas**, **sin refresh token**: cubre una jornada.
- Se firma con HS256 y la clave de `TIENDA_JWT_SECRETO` (base64, al menos
  256 bits). No tiene valor por defecto: si falta o es corta, la aplicación no
  arranca, porque con una clave conocida cualquiera podría fabricar tokens.
- El token lleva el id del usuario (`sub`), `iat`, `exp` y `hc`, una huella de
  la contraseña: HMAC-SHA256 del hash BCrypt con la clave del token, truncado a
  16 bytes, que no permite reconstruir el hash. Desde HU-05 lleva también
  `vs`, la versión de sesión de la cuenta, que sube al desactivarla. El rol, el
  estado, la huella y la versión se comparan con la base en cada petición: si
  se desactiva una cuenta, se le cambia el rol o la contraseña, vale desde la
  siguiente petición y no recién al vencer el token. Un token sin `vs` no se
  acepta.
- Un token inválido o vencido no autentica, pero tampoco bloquea la petición:
  los endpoints públicos la atienden igual, para que un token viejo guardado en
  el frontend no impida volver a iniciar sesión.

### Contraseñas con BCrypt

A través del `PasswordEncoder` de Spring Security. Mínimo 8 caracteres y
**máximo 72 bytes en UTF-8**: BCrypt ignora lo que pasa de 72 bytes, y sin el
máximo dos contraseñas largas distintas serían equivalentes.

*21/10/2026 (auditoría, revisando la skill `spring-security-jwt`).* **Costo
12** (`tienda.contrasenas.costo-bcrypt`; 4 en las pruebas): cada intento de
adivinar una contraseña robada cuesta 4 veces más que con 10. Las guardadas con
costo 10 siguen sirviendo (el costo va dentro del hash) y pasan a 12 cuando su
dueño cambia la contraseña. No se vuelven a encriptar al iniciar sesión: el
token lleva una huella del hash y cerraría las sesiones del usuario en sus
otros dispositivos. Mientras queden cuentas con costo 10, el tiempo de
respuesta del inicio de sesión las distingue de un correo inexistente (que se
compara con costo 12); en un servidor nuevo no hay cuentas viejas.

### Administrador inicial al arrancar

`AdministradorInicial`: al arrancar, si no existe ningún usuario con rol
ADMINISTRADOR, crea uno con `TIENDA_ADMIN_CORREO` y la contraseña hasheada de
`TIENDA_ADMIN_PASSWORD`. Es idempotente y no deja un hash escrito en un
archivo versionado, como haría un `data.sql`. *23/10/2026:* el nombre y el
apellido salen de `TIENDA_ADMIN_NOMBRE` y `TIENDA_ADMIN_APELLIDO`
(opcionales; vacíos, «Administrador Inicial»), e `instalar-servidor.sh` pide
el correo, el nombre y el apellido en vez de usar `admin@mail.com`. Ningún dato
personal en el repositorio: van en el `.env`.

- Si no hay ningún administrador y faltan las variables, o no cumplen las
  reglas del registro, la aplicación **no arranca**: un despliegue sin
  administradores no se puede recuperar desde la interfaz. Si ya existe uno,
  las variables no se leen.
- Si el correo configurado ya pertenece a otra cuenta, tampoco arranca: los
  roles solo los asigna un Administrador.

### Registro

- Todo usuario que se registra por sí mismo obtiene rol **Cliente**, y la
  cuenta queda **activa**, sin confirmación por correo. *21/10/2026 (HU-20):*
  sigue entrando directo, pero con el correo sin verificar no confirma
  pedidos (ver «Verificación del correo»).
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
- *09/10/2026 (HU-02).* **Con la sesión iniciada, «Iniciar sesión» lleva al
  inicio,** como «Crear cuenta» (HU-01).
- *09/10/2026 (HU-02).* **Cuando la sesión se cierra sola** (vence el token o
  el servidor responde 401 en una ruta protegida), se va a «Iniciar sesión»
  con el aviso «Tu sesión se cerró. Inicia sesión de nuevo.» y, al volver a
  entrar, a la pantalla donde estaba. «Cerrar sesión» a pedido no muestra
  aviso. **Motivo:** sin el aviso, la persona no entiende por qué la sacaron.
- *09/10/2026 (HU-02).* El campo de la contraseña del inicio de sesión tiene
  un botón para mostrarla u ocultarla; empieza oculta.

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
- *09/10/2026 (HU-03).* **A una cuenta desactivada no se le envía el
  enlace,** y un enlace pedido antes de desactivarla deja de servir (responde
  como un token inválido). La solicitud responde igual que siempre. **Motivo:**
  con la cuenta desactivada, cambiar la contraseña no le sirve para entrar.
- *09/10/2026 (HU-03).* Después de restablecer se va a «Iniciar sesión» con el
  aviso, sin entrar directo: el enlace del correo no alcanza por sí solo para
  abrir una sesión.
- *09/10/2026 (HU-03).* El correo usa los colores de la marca (entonces
  grafito y amarillo de señalización; desde el 16/10/2026 la marca es la de
  `DESIGN.md`, y el correo todavía no se actualizó).

### Verificación del correo

*21/10/2026 (HU-20).* **Verificar para comprar, no para entrar** (forma
acordada el 09/10/2026 en el issue #32):

- Al registrarse, la sesión queda iniciada como siempre y llega el correo
  «Confirme su correo» con un enlace a `/verificacion`. Sin verificar se ve
  el catálogo y se arma el carrito, pero **no se confirma un pedido**; el
  backend lo rechaza aunque la petición llegue directo.
- **Las cuentas que ya existían quedan verificadas,** de cualquier rol, y
  también el Administrador inicial.
- El enlace sigue el esquema de la recuperación (token de 256 bits, la base
  guarda su SHA-256, un solo uso) y **vale 24 horas**. «Reenviar el correo»
  manda uno nuevo e invalida los anteriores, con un límite de **3 reenvíos
  por cuenta cada 15 minutos** (429). Enlace vencido, usado, inexistente o de
  una cuenta desactivada: el mismo mensaje. A una cuenta desactivada no se le
  envía.
- **El enlace funciona sin sesión** y no la inicia: muestra «Su correo quedó
  verificado» con un enlace a la tienda.
- Solo se avisa en el carrito (en lugar de «Confirmar pedido») y en «Mis
  datos», cada uno con «Reenviar el correo».
- *21/10/2026 (revisión de HU-20).* **Bloqueos: primero la cuenta, después
  sus enlaces.** Reenviar y verificar bloquean la fila de la cuenta antes de
  tocar los enlaces; verificar lee el id de la cuenta del token sin bloqueo y
  recién después trae el enlace bloqueado. En el orden contrario, un reenvío
  y una verificación a la vez se traban y uno responde 500.
- *21/10/2026 (revisión de HU-20).* **Desactivar una cuenta invalida sus
  enlaces de verificación**, así no vuelven a servir si se la reactiva.

**Motivo:** sin verificar, cualquiera podía comprar con un correo ajeno, y
los avisos del pedido y la recuperación irían a otra persona. Pedirlo recién
al confirmar no frena a quien solo mira la tienda; un día de vigencia alcanza
para quien lee el correo más tarde.

### Datos personales

- Se pueden modificar nombre, apellido y teléfono, con las reglas del registro.
  **El correo no se modifica:** es la identidad y el destino de la
  recuperación. El rol y el estado solo los cambia un Administrador.
- **Cambiar la contraseña exige la actual,** y una actual incorrecta cuenta en
  el límite de intentos del inicio de sesión. La respuesta trae un token nuevo:
  la huella `hc` invalida todos los anteriores, así las demás sesiones abiertas
  se cierran.
- *09/10/2026 (HU-04).* **El botón para mostrar u ocultar la contraseña va en
  todos los campos de contraseña** («Iniciar sesión», «Crear cuenta», «Elegir
  contraseña nueva» y «Mis datos»), como una pieza de `shared/`.
- *09/10/2026 (HU-04).* **El usuario no da de baja su propia cuenta:** la
  desactiva un Administrador. Borrar una cuenta con pedidos rompería el
  historial y los reportes.

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
- *10/10/2026 (HU-05).* **Desactivar invalida las sesiones para siempre:** la
  cuenta lleva una versión de sesión que viaja en el token y se compara en cada
  petición, como la huella de la contraseña; desactivar la incrementa, así los
  tokens emitidos antes no vuelven a valer al reactivarla. Reemplaza el riesgo
  aceptado anterior (los tokens viejos revivían hasta vencer). Al desactivar
  también se anulan los enlaces de recuperación pendientes (HU-03).
  **Consecuencia aceptada:** al desplegar este cambio, los tokens que ya
  estaban emitidos no tienen `vs` y dejan de valer: todos inician sesión una
  vez más.
- *22/10/2026 (HU-18).* **Tipo de cliente:** Público general, Contratista,
  Taller o Empresa. Toda cuenta nace «Público general»; solo el Administrador
  lo cambia, en la gestión de usuarios, y solo en cuentas con rol Cliente
  (409 en otra). El listado lo muestra y filtra por tipo. **Motivo:** los
  reportes agrupan las ventas por tipo; si lo eligiera el cliente, cualquiera
  se pondría «Empresa».

---

## Notificaciones

### Envío de correo detrás de `NotificadorCorreo`

Mismo patrón que `IPago`: un **simulador** que solo escribe en el log lo que
habría enviado (incluido el token de recuperación en claro: **nunca activo en
producción**) y un **adaptador SMTP**. Se elige con
`tienda.correo.proveedor`: `simulador` (por defecto, y siempre en las
pruebas) o `smtp`.

*26/10/2026 (revisión total).* **En Docker, Gmail por defecto**
(`application-prod.properties` y `docker-compose.yml`), como el pago el
23/10: con el simulador por defecto, un servidor sin `TIENDA_CORREO_PROVEEDOR`
dejaba en el log los enlaces de recuperación, y quien leyera los logs podía
entrar a cualquier cuenta. Sin `TIENDA_CORREO_USUARIO` o
`TIENDA_CORREO_CONTRASENA` la API no arranca (`CredencialesSmtp`), y
`instalar-servidor.sh` lo avisa antes de levantar. `copiar-env-al-servidor.sh`
no deja ningún simulador (de pago ni de correo) en el servidor. El simulador
sigue por defecto fuera de Docker y en las pruebas.

**El envío no puede cambiar la respuesta de quien lo pide.** La recuperación
responde igual exista o no la cuenta, pero el correo solo sale si existe: si el
envío tardara o fallara dentro de la petición, la demora o un 500 revelarían
qué correos tienen cuenta. El adaptador SMTP envía en segundo plano (`@Async`)
y registra los fallos en el log sin propagarlos. Cada correo va en texto plano
y en HTML, y en el HTML todo dato que escribió un usuario se escapa.

*24/10/2026:* **el correo sale después del commit** (`TrasElCommit`): si la
transacción que creó el enlace se deshace, no llega un enlace que no sirve.
Sin transacción abierta, sale en el momento. Un fallo al encargar el envío
solo va al log: la cuenta o el enlace ya quedaron guardados. Los archivos que
se borran al terminar la transacción (`ArchivosAlTerminar`) usan la misma
pieza.

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

### Correos del pedido

*26/10/2026 (HU-21, pedido de Fabio).* La pasarela le manda al cliente el
comprobante del cobro, pero no sabe qué compró ni cómo lo recibe. La tienda
le escribe a la cuenta que hizo el pedido en dos momentos:

- **«Pedido pagado»,** cuando el pago se confirma (también un pago tardío que
  revive el pedido): el número, la fecha del pago, cada producto con su
  cantidad, precio y subtotal, el total, quién retira, la dirección y el
  horario de retiro si están configurados, y el enlace «Ver mi pedido».
- **«Pago a devolver»,** cuando el pago llega y el pedido ya no se puede
  reservar (HU-13 RF-9): el número y el monto, y que la tienda se comunicará
  con él al teléfono del pedido para devolverle el dinero. La devolución se
  sigue haciendo fuera del sistema.
- **A la cuenta, esté activa o desactivada** (confirmado al cerrar): el
  pedido es suyo y lo pagó.
- **Uno por pedido y por estado:** sale con la confirmación del pago, que se
  aplica una sola vez, después del commit (`TrasElCommit`). Si el envío
  falla, solo queda en el log: el pedido ya está pagado.
- Sin correos al crear, cancelar, vencer ni entregar el pedido, ni copia a la
  tienda.

**Motivo:** el comprobante de la pasarela es del dinero; el del pedido lo
tiene solo la tienda. Con la devolución fuera del sistema, el teléfono que
ya guarda el pedido alcanza para coordinarla, y el remitente no recibe
respuestas.

---

## Sistema de diseño (frontend)

### La aplicación es un producto, no un trabajo académico

En la interfaz no aparece la universidad, la materia ni la metodología: ni en
los textos, ni en el pie de página, ni en los correos. El nombre de la marca
vive en un único lugar del frontend (`core/marca.ts`) y del backend
(`TIENDA_MARCA`), para poder cambiarlo sin tocar las pantallas.

### Lo visual vive en `DESIGN.md`

*16/10/2026 (HU-09).* **Todo el sistema visual está en `DESIGN.md`:** paleta y
modos, tipografía, forma, espaciado, movimiento, texturas, componentes y las
reglas que valen para toda la interfaz (contraste medido, estado nunca solo por
color, errores junto al campo, áreas tocables, iconos Lucide). Lo escribe y lo
mantiene la skill `impeccable` a partir de lo construido; `PRODUCT.md` guarda a
quién le habla la tienda y cómo se siente. Este archivo sigue con las
decisiones técnicas y de negocio. **El mundo actual es «Letrero de ruta»** (lona
verde, amarillo cromo y lona clara), elegido en HU-09; reemplaza al grafito con
amarillo de señalización del 09/10/2026, y el encabezado translúcido con
desenfoque pasa a ser de lona sólida.

**Motivo:** con las skills de diseño, tener lo visual repartido entre este
archivo y `CLAUDE.md` duplicaba la fuente; `impeccable` lee `DESIGN.md`, y un
solo lugar por tema evita que se contradigan.

### Decisiones técnicas del frontend

- **Tres capas en `frontend/src/app/`:** `features/` (una carpeta por pantalla,
  con su componente, estilos y pruebas), `core/` (sesión, servicios HTTP,
  interceptores, guards, modelos) y `shared/` (piezas sin negocio). `core/`
  nunca importa de `features/`, y `shared/` no importa de ninguno de los dos.
- **Una pantalla por ruta, con `loadComponent`;** un servicio por área de la API.
- **Lecturas con `httpResource`, escrituras con `HttpClient`.**
- **Animaciones con CSS y `animate.enter` / `animate.leave`** de Angular
  cuando alcanzan, **sin `@angular/animations`** (deprecado); duraciones y
  curvas por tokens (`DESIGN.md`).
- **Iconos con [Lucide](https://lucide.dev)** (`@lucide/angular`, el paquete
  oficial actual; `lucide-angular` no soporta Angular 22): se importa icono por
  icono, así que el bundle solo lleva los que se usan.
- *14/10/2026.* **Librerías permitidas si mejoran el proyecto** (antes: sin
  librerías de componentes ni de estilos, salvo Lucide). Condiciones: que sea
  compatible con Angular 22 y esté mantenida, que se avise antes de
  instalarla, y que su aspecto se adapte a los tokens. **Motivo:** la regla
  venía de otro proyecto y limitaba el rediseño sin una razón propia.
- **Estilos solo por tokens** (`frontend/src/styles/_tokens.scss`) y
  componentes estilados con `:host`; lo angosto va centrado con el mixin
  `pagina-angosta` de `_pagina.scss`, sin `max-width` suelto.
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
  *14/10/2026.* Mientras tanto, cada pantalla se revisa con `playwright-cli`
  (escritorio y celular) antes de la prueba manual. **Cuando el proyecto esté
  casi completo** (catálogo, carrito, pedido y pago estables): una **revisión
  total** del proyecto y pruebas de extremo a extremo **solo del camino
  principal** (registro → catálogo → carrito → pedido → pago simulado → mis
  pedidos), unas 10 a 15 en el CI. **Motivo:** por historia costarían de 10 a
  20 % más de trabajo cada una y se romperían con el rediseño; al final son un
  costo fijo (~4 min de CI) sobre pantallas que ya no cambian.
- *20/10/2026 (auditoría).* **Un botón que espera una respuesta no se
  deshabilita:** lleva `aria-disabled` y su manejador ignora un segundo toque.
  Con `disabled` el navegador manda el foco al cuerpo de la página. `disabled`
  queda para lo que no se puede usar (sin página anterior, producto agotado).
  **`.solo-lectores` es global** (`styles.scss`), no una copia por
  componente, y **`comoErrorApi` vive en `core/modelos/error-api.ts`**, junto
  a `comoError`.

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

### Visor 3D y modelos

*17/10/2026 (spike #14).* El visor del detalle del producto (HU-16) es
**`<model-viewer>`** (Google), con los modelos en **`.glb`**.

- **Por qué `<model-viewer>` y no Three.js a mano:** los dos se probaron en la
  tienda real (Angular 22) con los mismos modelos. `<model-viewer>` trae lo que
  el otro obliga a programar y probar: encuadre automático, fondo
  transparente, sombra, giro, zoom y paneo con mouse y con los dedos, aviso de
  carga, accesibilidad y la realidad aumentada (WebXR y Scene Viewer en
  Android, Quick Look en iPhone, convirtiendo a USDZ en el momento). Pesa unos
  86 kB más.
- **Peso:** 295 kB transferidos (1 040 kB sin comprimir), en un fragmento
  aparte que se carga recién al abrir el visor; la carga inicial de la tienda
  no cambia. Se integra como elemento web (`CUSTOM_ELEMENTS_SCHEMA`) con un
  `import()` dentro de `afterNextRender`.
- **Versiones:** `@google/model-viewer` 4.4.0 pide Three.js `^0.183.0`; Three.js
  se instala en la versión que pida él, no en la última.
- **Escala:** glTF mide en metros y los modelos de Poly Haven vienen en tamaño
  real (martillo de 30 cm, caja de 40 cm), así que la realidad aumentada los
  muestra en su tamaño sin ajustes.
- **Tamaño de cada modelo:** el peso está casi todo en las texturas. Se
  preparan **fuera de la aplicación** con texturas de 1k recomprimidas en
  JPEG (`npx @gltf-transform/cli jpeg entrada.glb salida.glb --quality 75`),
  sin diferencia visible: la caja pasa de 2,74 a 0,95 MB y el martillo de
  1,51 a 0,40 MB. En 4G lenta cada MB tarda unos 5 s. **Límite al subir:
  3 MB; lo recomendado, menos de 1 MB.**
- **JPEG y no WebP:** WebP achica un poco más (la caja queda en 0,82 MB), pero
  necesita la extensión `EXT_texture_webp`, y no se pudo confirmar que la
  acepten Scene Viewer (Android) ni la conversión a USDZ de Quick Look
  (iPhone). Con JPEG el modelo queda en glTF estándar.
- **Modelos de demostración probados** (Poly Haven, CC0): martillo de peña
  (`cross_pein_hammer`), llave combinada (`combination_wrench`), taladro
  (`Drill_01`), wincha (`measuring_tape_01`), caja de herramientas
  (`metal_toolbox`) y regadera (`watering_can_metal_01`). Alcanzan para la
  demo; se pueden sumar otros de Poly Haven preparándolos igual.
- **Carga a pedido:** el modelo se descarga recién con «Ver en 3D», con la foto
  principal como póster mientras carga.
- **Probado en celulares (Fabio):** la realidad aumentada funciona en Android
  con `<model-viewer>`; en un celular sin soporte el botón no aparece; en uno
  de gama baja el giro y el zoom son fluidos. **Pendiente para HU-16:** la
  realidad aumentada en iPhone y con los modelos propios servidos por HTTPS
  (Scene Viewer necesita descargarlos de una dirección pública; un archivo
  abierto desde el celular no la ofrece).

**Motivo:** el visor es un detalle del producto, no el producto: conviene la
pieza que ya resuelve los casos difíciles (realidad aumentada en dos
sistemas, gestos, accesibilidad) aunque pese un poco más, y con modelos
livianos la diferencia que se nota es la de los modelos, no la de la librería.

*22/10/2026 (HU-15).* **Carga del modelo:**

- **Un modelo `.glb` por producto,** opcional. El Administrador lo sube,
  lo reemplaza o lo quita desde el formulario del producto. Al reemplazarlo
  o quitarlo se borra el archivo anterior, como con las fotos.
- **Se comprueba por su contenido,** no por la extensión: un glTF binario
  versión 2. Hasta **3 MB**. Entre 1 y 3 MB se acepta con un aviso de que
  puede tardar en cargar en un celular con datos.
- **Vista previa con el mismo visor:** `<model-viewer>` entra en HU-15 y el
  Administrador gira el modelo en el formulario antes de que lo vean los
  clientes. HU-16 reutiliza el visor en el detalle.
- **Marca «3D»** en la lista «Productos» del Administrador, sin filtro nuevo.
- **El catálogo de demostración carga la columna `modelo_3d`** con las mismas
  reglas. Un modelo inválido hace inválida la fila, como una imagen.
- El archivo se guarda y se sirve como las imágenes («Imágenes subidas»): el
  nombre lo genera el sistema y se sirve por el producto al que pertenece.

**Motivo:** reemplazar y quitar cubren un modelo mal preparado; la vista
previa evita publicar uno roto, girado o a otra escala; el aviso de peso
sostiene la recomendación del spike sin bloquear la carga; con la
demostración, los modelos de Poly Haven quedan listos para mostrar.

*23/10/2026 (HU-16).* **El modelo en el detalle del producto:**

- **En la galería:** si el producto tiene modelo, la galería ofrece «Ver en
  3D». El modelo reemplaza a la foto grande, con la foto principal como póster
  mientras carga, y «Ver fotos» (o una miniatura) vuelve a las fotos.
- **Quieto, con la pista de gesto:** el modelo no gira solo, como los
  carruseles. Si el cliente no lo toca, aparece la mano que muestra cómo
  girarlo; con movimiento reducido, un texto en su lugar.
- **Realidad aumentada en Android y en iPhone:** WebXR o Scene Viewer en
  Android y Quick Look en iPhone (`<model-viewer>` convierte el `.glb` a USDZ
  en el momento; no se sube otro archivo). El botón «Ver en su espacio» va
  dentro del visor y aparece solo donde el dispositivo la soporta.
- **Marca «3D» en las tarjetas** del catálogo, de la portada y de «Más de…»,
  sin filtro nuevo.
- **Los decodificadores los sirve la tienda:** si un modelo viene comprimido
  con Draco, KTX2 o meshopt, sus decodificadores salen de la propia tienda
  (copiados de `three` en el build), no de los servidores de Google. Van en
  `/decodificadores/`, sin hash en el nombre, así que nginx los revalida en
  cada visita (`no-cache`) y responde 404 si falta uno. meshopt ya viene
  dentro de `<model-viewer>`: un archivo vacío de la tienda solo lo activa.

**Motivo:** en la galería, el modelo es otra forma de ver el producto y no
tapa el resto de la página; que no gire solo respeta a quien lo está mirando;
la conversión a USDZ evita pedirle al Administrador un segundo archivo; la
marca avisa antes de abrir el producto; y el navegador del cliente no le pide
nada a terceros.

*26/10/2026 (HU-16, ampliación).* **El modelo en pantalla completa:**

- **Un botón «Pantalla completa» dentro del visor,** arriba a la derecha, en
  el detalle del producto y en la vista previa del Administrador (HU-15): es
  el mismo visor.
- **La pantalla completa del navegador** sobre el visor: el modelo se sigue
  girando y acercando, y «Ver en su espacio» sigue ahí. Se sale con el mismo
  botón («Salir de pantalla completa») o con Esc.
- **Sin botón donde el navegador no la permite:** Safari del iPhone solo la da
  a los videos, así que ahí no aparece; no se imita cubriendo la ventana.
- La pantalla completa de las fotos (HU-10) sigue siendo solo de fotos.

**Motivo:** en el celular la caja de la galería es chica para mirar un
modelo. Fabio prefirió no mostrar el botón en el iPhone antes que una
imitación que deja a la vista las barras del navegador.

### Categorías del catálogo

*10/10/2026 (HU-06).* Un solo nivel, sin subcategorías. Cada categoría tiene
nombre (obligatorio, único sin importar mayúsculas ni tildes, hasta 60
caracteres: «Jardin» y «Jardín» son la misma),
descripción opcional (hasta 300) e imagen opcional (reglas de «Imágenes
subidas»; sin imagen, un ícono genérico). Se muestran en orden alfabético.
Cada producto pertenece a **una sola** categoría.

- **Una categoría con productos no se borra: se oculta.** Oculta no aparece
  en el catálogo y sus productos no se listan por ella; se puede volver a
  mostrar. Sin productos se borra del todo, con su imagen.

**Motivo:** el catálogo de demostración tiene 15 a 25 productos en 4 a 6
categorías; un nivel alcanza, y ocultar evita perder productos y el
historial de ventas por categoría.

### Productos

*10/10/2026 (HU-07).* Cada producto tiene nombre (no único), código, marca,
categoría, unidad de venta, precio en bolivianos, descripción, una ficha
técnica libre y hasta cinco imágenes.

- **El código lo genera el sistema:** las tres primeras letras de la categoría
  de alta, sin tildes y en mayúsculas, un guion y el número del producto con
  cuatro cifras («JAR-0007»). No cambia si el producto cambia de categoría.
  Del nombre solo cuentan las letras (sin espacios, cifras ni signos): «3D»
  da «D»; un nombre sin letras usa «PRD».
- **Unidad de venta de una lista fija:** unidad, metro, kilo, litro, caja, par,
  rollo (parejo para el stock y los reportes).
- **Ficha técnica:** hasta 15 pares nombre–valor, para los datos que cambian
  de una herramienta a otra sin tocar el sistema.
- **Hasta cinco imágenes; la primera es la principal.**
- **Un producto no se borra: se desactiva.** Inactivo no sale en el catálogo
  ni se compra, pero queda para pedidos y reportes. Una categoría con
  productos, activos o no, no se borra (HU-06: se oculta).
- Precio mayor que 0 y hasta 99 999,99 Bs, con dos decimales como máximo.

**Motivo:** el código automático evita repetidos e inventos; la lista fija de
unidades y la ficha libre equilibran orden y flexibilidad; desactivar en vez de
borrar protege el historial de ventas.

*22/10/2026 (HU-18).* **Costo opcional:** el Administrador puede cargar el
costo de cada producto (mayor que 0, hasta 99 999,99 Bs, dos decimales). Es
uno solo, el último cargado, sin costo promedio ni por lote. Nunca sale en el
catálogo público, el carrito ni los pedidos del cliente. Cada línea del pedido
guarda el costo al confirmar, como el precio, y el margen de los reportes se
calcula con ese. Reemplaza «sin costo de compra» de «Inventario» solo en
esto: el inventario sigue sin proveedores ni costo por movimiento.
**Motivo:** el margen de utilidad lo pide el Contador, y guardarlo en la línea
evita que un cambio de costo reescriba lo ya vendido.


### Inventario

*12/10/2026 (HU-08).* Un solo stock por producto, que se mueve solo con
movimientos registrados.

- **Cantidades según la unidad:** metro, kilo y litro admiten hasta dos
  decimales; unidad, caja, par y rollo, solo enteros.
- **Dos movimientos:** la *entrada* suma (una compra); el *ajuste* lleva la
  cantidad contada y el sistema guarda la diferencia, con un motivo de lista
  (conteo físico, daño o merma, devolución, otro). Los dos con nota opcional.
  Sin costo de compra ni proveedores.
- **El stock nunca es negativo.**
- **Un movimiento no se edita ni se borra:** un error se corrige con otro
  ajuste. Cada movimiento guarda quién lo hizo y cuándo.
- **Stock mínimo opcional por producto:** la pantalla marca «Stock bajo» y
  deja filtrar por eso; sin correos ni notificaciones.
- **Roles:** el Empleado y el Administrador registran; el Contador solo ve.
  Los productos inactivos se listan y admiten movimientos.
- **Límites:** una cantidad hasta 99 999,99 y el stock hasta 9 999 999,99;
  «Stock bajo» es stock menor o igual al mínimo; la nota admite 200
  caracteres y es obligatoria con el motivo «otro». Un producto no puede
  pasar a una unidad sin decimales si su stock o su mínimo los tienen.

*19/10/2026 (HU-12).* **Dos movimientos más, sin cargarlos a mano:**
«Reserva por pedido» resta al confirmar un pedido y «Liberación por pedido»
suma al cancelarlo o al vencer, los dos con el número de pedido y a nombre
del cliente. El stock sigue sin quedar negativo.

**Motivo:** el ajuste por cantidad contada es lo que hace quien cuenta un
estante; el historial inmutable deja rastro de cada cambio; costos y
proveedores quedan fuera porque los reportes son de ventas e ingresos.

### Catálogo público

*14/10/2026 (HU-09).* La tienda es la portada para todos, con o sin sesión.

- **Portada (`/`) y catálogo (`/catalogo`):** la portada tiene carruseles
  («Destacados», «Recientes» y uno por categoría, hasta 12 productos cada uno)
  y una búsqueda; el catálogo tiene búsqueda, filtros, orden y páginas de 24.
- **Destacados:** los marca el Administrador en el producto, al crearlo o al
  editarlo; su carrusel no lleva «Ver todo». «Más vendidos» llega cuando
  existan pedidos.
- *16/10/2026.* **La búsqueda es la del encabezado,** en todas las pantallas
  (en `/catalogo` la reemplaza la del catálogo); la portada se recorre con la
  ventana del letrero y su cinta (`DESIGN.md`).
- **Solo productos activos de categorías visibles.** Sin stock se muestran
  como «Agotado», al final de cualquier orden y fuera de los carruseles;
  **nunca se muestra la cantidad en stock**.
- **Búsqueda en nombre, marca y código,** sin importar mayúsculas ni tildes;
  filtros por categoría, marca, rango de precio y «Solo disponibles»; orden
  por recientes, precio y nombre. Todo en la dirección.
- **Carruseles manuales:** flechas, rueda, teclado o arrastre, con freno en
  cada tarjeta; no avanzan solos.
- **El panel del personal pasa a `/panel`,** con un enlace en el encabezado;
  el personal llega ahí al iniciar sesión y el Cliente vuelve a la tienda.

**Motivo:** la portada vende y el catálogo encuentra; un carrusel que avanza
solo le quita el producto a quien lo estaba mirando; mostrar la cantidad en
stock expone el inventario a cualquiera.

### Detalle del producto

*16/10/2026 (HU-10).* Cada producto que se puede listar tiene su página
pública en `/producto/:id`, con o sin sesión.

- **Muestra:** las fotos (la principal grande, miniaturas para cambiar y
  pantalla completa), nombre, marca, código, categoría (enlaza al catálogo
  filtrado), precio con su unidad, «Disponible» o «Agotado» (nunca la
  cantidad), descripción y ficha técnica. Debajo, «Más de <categoría>»: hasta
  12 disponibles de la misma categoría, sin el producto.
- **No disponible:** un producto inactivo, de una categoría oculta o que no
  existe responde 404, igual en los tres casos, y la pantalla dice que no
  está disponible, con un enlace al catálogo.
- **Sin compra todavía:** el botón para agregar al carrito llega con HU-11; el
  modelo 3D, con HU-15 y HU-16. *(23/10/2026: el modelo y la realidad aumentada, en
  «Visor 3D y modelos».)*
- **El Administrador** ve un acceso «Editar producto» (usabilidad; el permiso
  lo valida el backend).

**Motivo:** el código ayuda a pedir por teléfono o en el local; un 404 igual
para los tres casos no revela qué productos existen; un botón que todavía no
anda promete algo que la tienda no cumple.

### Carrito

*17/10/2026 (HU-11).* El carrito es del Cliente, con sesión, y se guarda en
el servidor: uno por cliente, sin vencimiento, igual en cualquier
dispositivo.

- **Solo el Cliente:** el personal no tiene carrito (403). Sin sesión,
  «Agregar al carrito» lleva a iniciar sesión y vuelve al producto, sin
  agregarlo.
- **Una línea por producto, hasta 50 líneas.** Agregar uno que ya está suma
  la cantidad.
- **Cantidad:** mayor que 0 y hasta 999 por línea; los decimales, como el
  inventario (dos en metro, kilo y litro; ninguno en el resto).
- **Stock:** un producto agotado no se agrega; la cantidad no se compara con
  el stock en el carrito, y lo que no alcance lo rechaza el pedido (HU-12).
- **Precio de hoy:** el carrito no guarda precios. Lo que se agota o deja de
  venderse queda marcado «Agotado» o «No disponible», fuera del total, hasta
  que el cliente lo quita.

**Motivo:** el carrito en el servidor se ve igual en la computadora y en el
celular, y evita juntar un carrito del navegador con otro de la cuenta;
comparar la cantidad con el stock en el carrito revelaría cuánto hay, y el
stock recién se reserva al confirmar.

### Pedidos

*19/10/2026 (HU-12).* El Cliente convierte su carrito en un pedido que se
retira en la tienda.

- **Reserva con vencimiento:** al confirmar, el stock se resta («Reserva por
  pedido»). El pedido queda «Pendiente de pago» y, si no se paga en **30
  minutos**, pasa a «Vencido» y el stock vuelve. El vencimiento lo controla
  el sistema.
- **Solo retiro en la tienda:** sin envío ni costo de envío. Se piden el
  nombre y el teléfono de quien retira (llenos con los de la cuenta,
  editables) y una nota opcional; el pedido guarda una copia.
- **Todo o nada:** si para alguna línea no alcanza el stock, no se confirma
  nada y el carrito marca la línea, sin revelar cuánto hay. El rechazo dice
  si alcanza, nunca cuánto; probando cantidades se podría deducir el stock,
  como en cualquier tienda que rechaza por falta de stock, y se acepta: no
  hay límite de intentos.
- **Un solo pedido pendiente de pago por cliente,** que el cliente puede
  cancelar. Al cancelar o vencer, el stock vuelve y las líneas regresan al
  carrito.
- **El pedido guarda el precio de cada línea al confirmar:** desde ahí, un
  cambio de precio no lo toca.
- **No se confirma un total distinto del que vio el cliente:** si cambió un
  precio, la confirmación muestra el total nuevo.
- **Al volver al carrito,** una línea que pasaría de 999 queda en 999 y las
  que no entran en las 50 líneas no vuelven, con un aviso.
- **Dirección y horario de retiro** en `TIENDA_RETIRO_DIRECCION` y
  `TIENDA_RETIRO_HORARIO` (`.env`); sin configurar, no se muestran.
- *19/10/2026 (revisión de HU-12).* **Lo devuelto no pasa del stock
  máximo:** si mientras el pedido esperaba el stock llegó al máximo
  (9 999 999,99, o 9 999 999 en una unidad entera), al cancelar o vencer
  queda en el máximo y la «Liberación por pedido» registra solo lo que
  volvió. Sin esto, el guardado fallaba y el pedido quedaba pendiente para
  siempre, bloqueando otro pedido del cliente. Es un caso de laboratorio: el
  máximo está muy por encima de cualquier stock real.
- *19/10/2026 (revisión de HU-12).* **Todo lo que cambia un pedido bloquea
  antes la fila de su cliente** (después, el pedido y los productos por id
  ascendente). Por eso la confirmación lee los pedidos pendientes sin
  bloquearlos: en MySQL, un `SELECT … FOR UPDATE` que no encuentra filas
  bloquea el hueco del índice y traba a dos clientes que confirman a la vez.
  El pago (HU-13) y el despacho (HU-17) siguen la misma regla.

- *21/10/2026 (HU-14).* **Mis pedidos:** la lista de todos los pedidos del
  Cliente, el más nuevo primero y de a 10 por página, con el pedido que espera
  el pago arriba (el tiempo que le queda y «Pagar») y cada estado con el color
  de su chip. Se llega desde el encabezado (junto al carrito, solo el
  Cliente), desde el panel y desde la página de cada pedido.
- *21/10/2026 (HU-17).* **La entrega:** un solo paso, de «Pagado» a
  «Entregado», sin «Listo para retirar» ni correos de aviso. Quien retira da
  el número del pedido; el Empleado compara el nombre y el teléfono guardados
  con su documento, sin código de retiro, y confirma la entrega, que no se
  deshace y guarda quién la registró y cuándo. El personal ve **todos** los
  pedidos en `/pedidos`, filtrados de entrada en «Pagado» (el pagado hace más
  tiempo primero), con filtro por estado y búsqueda por número o por nombre
  de quien retira. Registran la entrega el Empleado y el Administrador; el
  Contador solo ve, como en el inventario. Sin exportar (HU-19).
  **Motivo:** en una tienda de un solo local, el pedido pagado ya se puede
  armar en el momento; un paso intermedio y su correo no agregan nada, y
  comparar el nombre con el documento alcanza sin sumar un código.

**Motivo:** el pago por QR es inmediato, así que 30 minutos alcanzan y un
pedido abandonado no bloquea el stock; retirar en la tienda evita calcular
envíos en una tienda de un solo local; todo o nada evita cobrar un pedido
distinto del que el cliente armó.

### Pagos

*20/10/2026 (HU-13).* El Cliente paga con un QR su pedido pendiente de pago.
El circuito y el adaptador de Libélula vienen del proyecto de eventos del
mismo equipo.

- **El cobro se genera con «Pagar con QR»** en la página del pedido, uno por
  pedido: volver a pedirlo devuelve el mismo. Si el sistema de pago informa
  un error, ese cobro se descarta y se puede generar otro.
- **La tienda se entera consultando:** la página consulta cada pocos segundos
  mientras el QR está a la vista, «Ya pagué» consulta en el acto, el aviso del
  proveedor (`callback_url`) solo adelanta la consulta y una tarea revisa cada
  minuto los cobros de los pedidos pendientes y de los vencidos o cancelados
  en las últimas 24 horas (la deuda del proveedor vence por día).
- **Pagado:** el pedido queda «Pagado» con la fecha del pago; la reserva pasa
  a ser la venta, sin otro movimiento de stock. No se cancela ni vence.
- **Antes de vencer un pedido con cobro, se consulta:** si está pagado, queda
  «Pagado»; si la consulta falla, no vence y se reintenta en la próxima
  vuelta. Consultan la tarea de cada minuto y la página del pedido al llegar
  a cero; leer el carrito o el pedido no llama al sistema de pago (una
  llamada puede tardar 15 s), así que un pedido con cobro puede seguir
  «Pendiente de pago» hasta un minuto después de su hora. Los cobros se leen
  sin bloqueo y con el cliente ya bloqueado, por la misma razón que los
  pedidos pendientes al confirmar.
- **Pago tardío** (de un pedido vencido o cancelado): si todos sus productos
  siguen a la venta y alcanza el stock, se vuelve a reservar, el pedido queda
  «Pagado» y sus cantidades se restan del carrito (las líneas en cero se
  quitan). Si no, queda «Pago a devolver»: la devolución del dinero se hace
  fuera del sistema y no se registra.
- **Un pago se aplica una sola vez,** aunque lleguen a la vez la consulta, el
  aviso y la tarea; se bloquea primero el cliente, como todo lo que cambia un
  pedido. Ninguna llamada al sistema de pago se hace con filas bloqueadas.
- **Libélula** (`TIENDA_PAGO_PROVEEDOR=libelula`): la appkey y el correo fijo
  que exige en cada deuda van solo en `.env` (`TIENDA_PAGO_LIBELULA_*`); sin
  ellos la API no arranca y la appkey nunca sale en el registro. Su nombre
  queda en el adaptador y su configuración. Las pruebas usan un servidor
  falso.
- **El simulador sigue por defecto,** y con él, bajo el QR, «Simular pago» y
  «Simular error», marcados como herramienta de pruebas; con otro proveedor
  ni los botones ni su endpoint existen.
- *23/10/2026 (antes de publicar).* **En Docker, Libélula por defecto**
  (`application-prod.properties` y `docker-compose.yml`): con el simulador
  por defecto, un servidor sin `TIENDA_PAGO_PROVEEDOR` dejaba a cualquier
  cliente marcar pagado su pedido. Sin la appkey o el correo, la API no
  arranca (`instalar-servidor.sh` lo avisa antes de levantar). El simulador
  queda para probar en la PC, con `TIENDA_PAGO_PROVEEDOR=simulador` en el
  `.env`, y sigue por defecto fuera de Docker y en las pruebas.

- *20/10/2026 (revisión de HU-13).* **Lo que puede vencer un pedido va con
  READ_COMMITTED** (`PedidoService` y `CarritoService.ver`, además de
  `PagoService` y la conciliación): con REPEATABLE_READ, una lectura sin
  bloqueo anterior al bloqueo del cliente fija la vista y no ve un cobro
  recién guardado. **Dos hilos para las tareas programadas**
  (`spring.task.scheduling.pool.size=2`): la conciliación espera al sistema
  de pago y no demora el vencimiento, que **consulta siempre antes de
  vencer**. **Cada línea del pedido guarda cuánto volvió al carrito**
  (`cantidad_devuelta`): el pago tardío resta solo eso.

**Motivo:** el pago por QR se hace en la app del banco, así que la tienda
solo puede preguntar; consultar antes de vencer y aceptar el pago tardío
evita cobrar sin entregar, y la deuda que vence por día deja pagar un QR
después de los 30 minutos del pedido.

### Reportes

*22/10/2026 (HU-18).* `/reportes`, solo para el Contador y el Administrador
(el Empleado no), de solo lectura.

- **Venta = pedido «Pagado» o «Entregado», en la fecha del pago,** con su
  total guardado. «Pago a devolver» no suma: se muestra aparte.
- **Período** en hora de Bolivia: de entrada «Este mes»; atajos Hoy, Últimos
  7 días, Este mes y Mes anterior, o un rango de hasta 366 días. Se compara
  con el período anterior de igual largo. Por día hasta 62 días; más, por
  mes.
- **Secciones:** resumen (ingresos, pedidos, promedio, margen, pagos a
  devolver), ventas por día, por día de la semana y por hora, por categoría
  (la actual del producto), por tipo de cliente (el actual de la cuenta),
  productos más y menos vendidos, sin venderse, pedidos perdidos y clientes
  inactivos.
- **Margen** solo con las líneas que tienen costo guardado; lo vendido sin
  costo se muestra aparte.
- **Pedidos perdidos:** los vencidos y cancelados entre los confirmados del
  período. La proporción de vendidos no cuenta los que esperan el pago ni los
  «Pago a devolver», que se pagaron pero no fueron venta (revisión de código).
- **Sin venderse:** productos activos con stock, por días desde la última
  venta o desde el alta, con el dinero inmovilizado (stock × costo, o × precio
  sin costo). **Clientes inactivos:** clientes activos que compraron alguna
  vez y no compran hace más de 30, 60 o 90 días, con nombre, correo y
  teléfono para llamarlos; la tienda no les manda correos.
- **Fuera:** crédito, descuentos y cupones y campañas por correo. Exportar
  es de HU-19 (ver «Exportar reportes»).

**Motivo:** el Contador necesita controlar el dinero y además saber qué
rebajar y a quién llamar; contar la venta al pagar coincide con el dinero que
entró, y el tipo y la categoría actuales evitan guardar copias que nadie
corrige.

### Exportar reportes

*22/10/2026 (HU-19).* Desde `/reportes`, el Contador o el Administrador
descargan **un archivo con todo el reporte** del período y los filtros que
tienen en pantalla, en **Excel (.xlsx) o PDF**.

- **Se arma en el backend** (`GET /api/reportes/exportar`), con las mismas
  consultas de HU-18 y el mismo control de rol. Librerías: **Apache POI**
  para el Excel y **OpenPDF** para el PDF.
- **Contenido:** todas las secciones de HU-18; las listas van **completas,
  sin paginar**, con los días elegidos en pantalla (30, 60 o 90). El Excel,
  una hoja por sección con números de verdad (no texto) y formato de
  bolivianos; el PDF, las mismas secciones una tras otra, con páginas
  numeradas.
- **Encabezado:** la marca, el período y el anterior, el tipo de cliente,
  los días de las listas, la fecha y hora de generación (Bolivia) y el
  nombre de quien lo exportó.
- **Clientes inactivos con nombre, correo y teléfono,** porque el archivo
  sirve para llamarlos, con el aviso «Contiene datos personales de
  clientes: uso interno».

**Motivo:** el Contador entrega los números (Excel para seguir trabajando,
PDF para entregar o imprimir); armarlo en el backend evita repetir los
cálculos en el navegador y deja el control de acceso donde ya está.

### Catálogo de demostración

*17/10/2026 (issue #25).* El catálogo con el que se muestra la tienda se carga
al arrancar desde una carpeta (`TIENDA_DEMO_DIRECTORIO`, en Docker de solo
lectura) con `catalogo.csv` y sus imágenes. Lo hace `CatalogoDemo`.

- **Solo una vez:** sin la variable no hace nada; con productos en la base,
  tampoco.
- **`catalogo.csv`:** una fila por producto, con
  `categoria,nombre,descripcion,precio_bs,stock_inicial,imagenes,modelo_3d,destacado,unidad`.
  Separado por comas y sin comillas: **ningún campo lleva comas**. Precio y
  stock con punto decimal; `imagenes`, archivos de la carpeta separados por
  `|`; `modelo_3d`, un archivo `.glb` de la carpeta o vacío (desde HU-15, con sus
  reglas en «Visor 3D y modelos»); `destacado`, `si` o vacío
  (sale en «Destacados» de la portada); `unidad`, `unidad`, `metro`, `kilo`,
  `litro`, `caja`, `par` o `rollo`, y vacía es `unidad`.
- **Las reglas de siempre:** categorías, productos, imágenes (2 MB, JPG, PNG o
  WebP, hasta cinco, sin repetir) y stock pasan por los mismos servicios y
  validaciones que la gestión. Las categorías que faltan se crean; una fila
  de una categoría oculta es inválida, porque no se vería.
- **Todo o nada:** una fila inválida se informa en el registro con su número y
  el motivo, y no se carga nada; un fallo durante la carga la revierte entera.
  La aplicación arranca igual, con el catálogo vacío. Una variable que no
  apunta a una carpeta con `catalogo.csv` impide arrancar, aunque ya haya
  productos.
- **El stock inicial respeta la unidad:** entero, salvo en metro, kilo o
  litro, que admiten dos decimales. Marca y ficha quedan vacías.
- **El stock inicial es una entrada** a nombre de la cuenta de
  `TIENDA_ADMIN_CORREO` si es un Administrador activo, o de otro Administrador
  activo; nunca de un cliente.

**Motivo:** como la carga no se repite, saltar filas dejaría un catálogo a
medias que no se completa corrigiendo el CSV; pasar por los servicios evita
reglas duplicadas que se desfasen.
