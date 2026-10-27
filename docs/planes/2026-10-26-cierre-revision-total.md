# Plan — Cierre técnico: revisión total

*26/10/2026.* La revisión total que acordó `decisiones.md` → «Decisiones
técnicas del frontend» (14/10/2026), antes de las pruebas de extremo a
extremo. La hicieron cuatro subagentes de solo lectura: backend, código del
frontend, interfaz en el navegador (`playwright-cli`, Edge, escritorio y
Pixel 7, claro y oscuro, los cuatro roles) y documentación e
infraestructura. Fabio eligió corregir los tres grupos (A, B y C) y, para el
inventario, «contar el estante».

Sin hallazgos graves en el dinero ni en los permisos: el control de acceso
coincide con `SecurityConfig` endpoint por endpoint, el orden de bloqueos se
cumple en todos los caminos, el stock nunca queda negativo y el pago se
aplica una vez.

## Cómo se entrega

| PR | Rama | Qué |
|---|---|---|
| 1 | `fix/cierre-importantes` | Grupo A, menos el inventario |
| 2 | `feature/hu-08-contar-el-estante` | A1: cambio a HU-08 con el flujo SDD (historia, plan, TDD) |
| 3 | `fix/cierre-interfaz` | Grupo B |
| 4 | `fix/cierre-limpieza` | Grupo C y la documentación |

Después, las pruebas de extremo a extremo (`@playwright/test`, aprobado por
Fabio) en su propio PR.

## A. Importantes

1. **El ajuste de inventario infla el stock vendible.** El stock guardado es
   el disponible (la reserva y lo pagado sin retirar ya se restaron), pero el
   conteo físico incluye esas unidades. → PR 2: la pantalla muestra lo
   comprometido; lo contado es el estante y se guarda
   `disponible = contado − comprometido`; negativo se rechaza.
2. **Simulador de correo por defecto en Docker** (`docker-compose.yml`): el
   simulador escribe en el log los enlaces de recuperación y verificación
   («nunca activo en producción», `decisiones.md` → Notificaciones), y
   `copiar-env-al-servidor.sh` copia `simulador` si la PC lo tiene. → En
   `prod`, Gmail por defecto y sin usuario o contraseña la API no arranca,
   como el pago del 23/10; el instalador lo avisa antes de levantar.
3. **Inicio de sesión sin límite de largo** (`InicioSesionRequest`): un
   correo de varios MB queda 15 min en el mapa de `LimiteDeFrecuencia`. →
   `@Size` en correo y contraseña.
4. **Consulta del cobro sin límite** (`PagoService` → `ConciliacionDePagos`):
   cada consulta llama al sistema de pago y ocupa un hilo hasta 15 s. → No
   volver a consultar un cobro consultado hace menos de unos segundos.
5. **Pestaña abierta tras un despliegue** (`app.config.ts`): el fragmento
   viejo responde 404 y el clic no hace nada; la confirmación del pedido y el
   inicio de sesión navegan dentro del `try` y muestran «No pudimos
   conectar» aunque la escritura salió. → Manejador de errores de
   navegación que recarga, y navegar fuera del `try`.
6. **«Pedidos» del personal oculta un error** (`despacho.page`): el
   `linkedSignal` de la página se evalúa antes que `errorDeCarga()`.
7. **Infraestructura:** `.gitignore` no cubre `.env.tmp` (lo crean los
   scripts, con todos los secretos), `*.pem` ni `id_rsa*`; nginx resuelve
   `backend` una vez y da 502 si se recrea solo el backend; el instalador
   copia `.env.example` antes de pedir la IP y no se recupera si falla ahí.

## B. Interfaz visible

1. Dos «ojos» en las contraseñas con Edge (`::-ms-reveal`).
2. Placeholder del panel con 3:1 (`_formulario.scss`, sin regla con token).
3. Navegación del personal en el celular: la sección actual queda fuera.
4. Precio sin unidad en la tienda (portada, catálogo, detalle, carrito).
5. Textos de tú: «Tú» y «Reactívala» (`usuarios.page.html`), «explica»
   (`stock.page.ts`).
6. «Usuarios»: correos cortados en cualquier letra y filas irregulares.
7. «BS 640,00» por `text-transform` en el pedido del personal.
8. «Reportes» en el celular: unos 10 800 px de tarjetas, casi todas en cero.
9. Plural de la unidad («40 unidad»).
10. Áreas tocables de menos de 44 px (íconos del encabezado, nombres en
    Inventario).
11. Nombres de producto cortados en la medida (dos líneas).
12. Leyenda de la portada cortada en el celular.
13. El visor 3D le habla en inglés al lector de pantalla.
14. Foco: se pierde al paginar (`[disabled]` con `isLoading()`, paginador que
    se destruye), en los selectores de «Usuarios» mientras guardan, y no se
    mueve al cambiar de pantalla.

## C. Menores y limpieza

- **HU-20:** «Reintentar» tras un corte dice que el enlace no sirve; 403 en
  lugar de 401 con la cuenta recién desactivada; el reenvío duplicado en el
  carrito y en «Mis datos».
- **Backend:** página gigante → 500 en las listas del personal
  (`Busquedas`); cambiar la unidad con reservas abiertas deja decimales; el
  pago tardío puede restar lo agregado después; la imagen de una categoría
  oculta se sirve; servicios que devuelven entidades; comentarios viejos.
- **Frontend:** 404 de producto con «Reintentar»; historial de stock sin
  reintentar; «Ya pagué» ignorado durante la consulta automática; botones de
  «Categorías» sin nombre propio ni guarda de doble toque; validador de id
  duplicado; roles de cada módulo en tres lugares; colores del tema copiados;
  `window.confirm` en el guard; resumen de errores en los formularios;
  estilo de tienda o de panel cruzado en algunas pantallas; detalles varios
  (fondo del producto sin foto, «Agregar al carrito» en dos renglones,
  paginación sin la última, número del pedido partido, sin página 404).
- **Docs:** `PRODUCT.md` («simulador» y «envío», capacidades «planificadas»
  ya hechas); `decisiones.md` (cuenta de Libélula, frase repetida);
  `flujo-sdd.md` (sprints hasta el 4); README (MySQL 8.0, estructura);
  pendientes resueltos sin marcar (Draco/KTX2 en HU-15); «verificación de
  David pendiente» en los planes de HU-01 a HU-08.
- **Infraestructura:** `X-Forwarded-Proto`, caché de `/texturas/`,
  `permissions:` del CI, contraseña de MySQL en la línea de comandos,
  comillas en `copiar-env`, `TIENDA_CORREO_HOST`/`PUERTO` sin documentar.

## D. Datos (los resuelve Fabio)

- Desactivar el producto «prueba» (Bs 0,05) y poner la dirección y el
  horario reales en `TIENDA_RETIRO_*`.
- Borrar lo que creó la revisión de interfaz: las cuentas
  `*.revision@tienda.test`, el pedido 6 y el producto 24.

## Resultado

*(al cerrar cada PR)*

### PR 1 — `fix/cierre-importantes` (27/10/2026)

**Hecho (grupo A, menos el inventario):**

- **A2:** Gmail por defecto en `prod` y en `docker-compose.yml`;
  `CredencialesSmtp` exige usuario y contraseña (la API no arranca sin
  ellos, y la contraseña no sale en mensajes); `instalar-servidor.sh` lo
  avisa antes de levantar; `copiar-env-al-servidor.sh` no copia un simulador
  y quita el que ya estuviera en el servidor. Decisión en `decisiones.md` →
  «Envío de correo detrás de `NotificadorCorreo`» y en `CLAUDE.md`.
- **A3:** `InicioSesionRequest` con 120 caracteres en el correo y 72 bytes en
  la contraseña.
- **A4:** una sola consulta en curso por cobro desde la página (la que llega
  mientras otra espera al sistema de pago devuelve el pedido como está); el
  aviso, la tarea de cada minuto y el vencimiento consultan siempre.
- **A5:** `withNavigationErrorHandler` carga la página entera si falta el
  fragmento de una versión vieja (`VersionPublicada`: una vez por dirección
  hasta que una navegación termine bien, y nunca sin conexión); la
  confirmación del pedido y el inicio de sesión navegan fuera del `try` y,
  si navegar falla, cargan el destino entero.
- **A6:** «Pedidos» del personal muestra el error antes que la página que
  quedó del filtro anterior.
- **A7:** `.gitignore` con `.env.*`, `*.env`, `*.pem`, `*.key` y llaves SSH;
  nginx resuelve `backend` en cada petición (`resolver 127.0.0.11
  ipv6=off`): probado recreando solo el backend; el instalador completa solo
  lo que falta del `.env` (se puede volver a correr si se cortó), con
  `umask 077` y `MYSQL_PWD`. De paso (grupo C): `/texturas/` se revalida.

**Pruebas nuevas:** `SeleccionDelNotificadorTest` (sin usuario o contraseña
no arranca; la contraseña no sale en `toString`),
`InicioSesionIntegracionTest.correoOContrasenaDeMasSeRechazanSinCompararlos`,
`PagoIntegracionTest.unaSolaConsultaALaVezPorCobro`,
`version-nueva.spec.ts` (4), y en las pantallas: confirmación e inicio de
sesión («si falla la navegación…») y despacho («un error con una lista ya
mostrada…»).

**Suites:** backend 729 pruebas, 0 fallas, 1 omitida; frontend 70 archivos,
858 pruebas; `ng build` sin avisos. Docker: la API responde por nginx después
de recrear solo el backend.

**`/code-review` medium, ronda 1 (10 hallazgos):** corregidos 9: el límite por
tiempo de A4 tapaba «Ya pagué» y el aviso (pasó a una consulta en curso por
cobro), la recarga sin tope ni control de conexión, el silencio si navegar
falla tras confirmar o iniciar sesión, la decisión del correo sin anotar,
`copiar-env` con un simulador ya puesto o entre comillas dobles, el
`.env.tmp` legible; revertido 1: `X-Forwarded-Proto` vuelve a `$scheme` (con
nginx expuesto, un cliente podía decir HTTPS).

**`/code-review` medium, ronda 2 (la última, 10 hallazgos):** corregidos 8: la
página recargaba sin los topes del manejador (ahora los dos usan
`VersionPublicada`), la marca de la recarga no se borraba, el aviso no
consultaba si la página ya lo hacía, `umask` y `grep` que podía vaciar el
`.env` en `copiar-env`, `valor()` con comillas dobles, `ipv6=off`, la fila
del correo en `CLAUDE.md`. Descartados 2: la prueba de A4 no puede volverse
inestable (las tareas programadas están apagadas en `application-test`), y
la interacción real entre el manejador y la página queda cubierta por la
prueba de `VersionPublicada` con las dos llamadas.

### PR 3 — `fix/cierre-interfaz` (27/10/2026)

**Hecho (grupo B):**

- **B1/B2:** en `styles.scss`, el ojo propio de Edge se oculta
  (`::-ms-reveal`) y el texto de ejemplo usa el token del texto secundario
  en toda la app (6,5:1 en claro y en oscuro).
- **B3:** `centrarEnLaFila` deja a la vista la sección actual de la
  navegación del personal en el celular; se centra al cambiar de sección o
  de secciones, no con un filtro.
- **B4: descartado.** El precio sin unidad es la regla para lo que se vende
  por unidad (`catalogo.ts`); las demás unidades ya la muestran.
- **B5:** «Su cuenta», «Reactívela» y «explique qué pasó».
- **B6:** el correo se parte en la `@` (`<wbr>`); uno sin `@` no se parte.
- **B7:** «Bs» y no «BS» en los totales de confirmación, pedido y despacho.
- **B8:** las ventas de «Reportes» siguen siendo una tabla en el celular,
  con los números a la derecha.
- **B9:** `unidadPara` y `pluralDeUnidad` (`producto.ts`): «1 unidad»,
  «40 unidades», «Cantidad (unidades)», en inventario y stock.
- **B10:** 44 px en los íconos del encabezado y en los nombres de
  Inventario.
- **B11:** el nombre en la tarjeta llega a tres líneas (nota en HU-09).
- **B12:** la leyenda compacta de la portada se achica con la pantalla
  (`clamp`); una categoría muy larga todavía termina en «…», como se espera.
- **B13:** `TEXTOS_DEL_VISOR`: el visor 3D le habla en español al lector de
  pantalla. «Live announcements» de model-viewer no se puede traducir desde
  la app.
- **B14:** los paginadores no se deshabilitan mientras cargan ni en el
  borde (`aria-disabled`, y `irAPagina` ignora el toque), y la página que
  se veía queda mientras llega la otra (`ultimoValor`, en `shared/recursos/`);
  tras un error no vuelve la de otra búsqueda. Los selectores de «Usuarios»
  no se deshabilitan mientras guardan. Al cambiar de pantalla, el foco pasa
  a `#contenido`, salvo si la dirección se reemplaza (crear un producto).

**Pruebas nuevas:** `centrar-en-la-fila.spec.ts`, `unidad-para.spec.ts`,
`ultimo-valor.spec.ts`, el foco en `desplazamiento.spec.ts`, el `a11y` del
visor, y en las pantallas: usuarios («un error de carga con una página ya
mostrada…» y el borde del paginador), mis pedidos y stock.

**Suites:** frontend 73 archivos, 871 pruebas; `ng build` sin avisos. Sin
cambios en el backend.

**`/code-review` medium, ronda 1 (9 hallazgos):** corregidos los 9.

**`/code-review` medium, ronda 2 (la última, 10 hallazgos):** corregidos 8:
sin aviso de carga con la página anterior a la vista, la búsqueda anterior
de vuelta tras «Reintentar», el foco que dejaba el formulario al crear un
producto, el centrado que deshacía lo corrido a mano o no esperaba a las
secciones del rol, el selector bloqueado sin verse, la misma lógica en
cuatro pantallas (pasó a `ultimoValor`) y el control del toque repetido en
diez botones (pasó a `irAPagina`). Descartados 2: los paginadores sí tienen
estilo bloqueado (`_lista.scss`), y el «/ unidad» de «Productos» es un
precio por unidad, en singular.

**Revisión en el navegador** (`playwright-cli`, Edge, escritorio y
«iPhone 15», claro y oscuro): 12 de 13 puntos bien; corregidos la tabla de
reportes (el selector del celular pesaba más que `.numero`), la etiqueta
«Correo» que se partía con un correo largo y el foco que caía al cuerpo al
llegar a la última página. Consola sin errores.

**Prueba manual de Fabio:** aprobada.

### PR 4 — `fix/cierre-limpieza` (27/10/2026)

**Hecho (grupo C):**

- **HU-20:** «Reintentar» tras un corte ya no dice que el enlace no sirve:
  un enlace usado o reemplazado de un correo ya verificado responde
  «verificado» mientras no venza (RF-17, decisión de Fabio); el reenvío con
  la cuenta recién desactivada responde 401 y la app cierra la sesión; el
  reenvío del carrito y de «Mis datos» es una sola función
  (`reenvioDeVerificacion`).
- **Backend:** una página gigante ya no da 500 (`Busquedas.paginaValida`,
  la misma para todos los listados); el cambio de unidad mira los pedidos
  abiertos (HU-08 RF-19, decisión de Fabio); el pago tardío solo resta de la
  línea del carrito que recibió lo devuelto (`id_linea_carrito`).
- **Frontend:** página «Esta página no existe»; un producto inexistente no
  ofrece «Reintentar» y el historial de stock sí; «Ya pagué» durante la
  consulta automática vuelve a preguntar al terminar; «Categorías» con el
  nombre en cada botón, sin doble toque y con el foco en el campo con error;
  los roles de cada módulo en `core/sesion/modulos.ts`; un solo validador de
  id (`idDeLaUrl`); el diálogo de la app en lugar de `window.confirm`; la
  paginación del catálogo con la primera y la última; el producto sin fotos
  sin recuadro blanco; «Agregar al carrito» en un renglón (container query);
  el número del pedido no se parte.
- **Docs:** `PRODUCT.md`, README (MySQL 8.4, estructura, correo),
  `decisiones.md` (cuenta de Libélula, el simulador dicho una vez),
  `flujo-sdd.md` (sprint 5, «Cierre»), Draco y KTX2 resueltos en el plan de
  HU-15, la verificación de David anotada en los planes de HU-01 a HU-08.
- **Infraestructura:** `permissions: contents: read` en el CI; el
  healthcheck de MySQL sin contraseña; `TIENDA_CORREO_HOST` y
  `TIENDA_CORREO_PUERTO` documentados y pasados por `docker-compose.yml`.

**Sin cambios, con motivo:** la imagen de una categoría oculta se sigue
sirviendo (como las fotos de un producto inactivo, y «Categorías» la muestra
con esa URL); los servicios que devuelven entidades no leen nada perezoso
fuera de la transacción; los colores del tema no se pueden unificar
(`index.html` los necesita antes del CSS: aviso junto al token);
`X-Forwarded-Proto`, la caché de `/texturas/` y las comillas de `copiar-env`
ya estaban en el PR 1; «estilo cruzado» y «comentarios viejos» no se
pudieron precisar.

**Suites:** backend 742 pruebas, 0 fallas, 1 omitida; frontend 74 archivos,
874 pruebas; `ng build` sin avisos. Docker: la base queda *healthy* con el
healthcheck nuevo.

**`/code-review` medium, ronda 1 (8 hallazgos):** corregidos 7: la consulta
de pedidos abiertos en cada guardado, la hora como señal de la línea del
carrito, el tope de página en cuatro lugares, el comentario de los roles del
panel, el diálogo de salida que pisaba otra confirmación, un «…» que escondía
una sola página y RF-17 más amplio que su texto. Descartado 1: `idDeLaUrl`
acepta 15 dígitos en vez de 16.

**`/code-review` medium, ronda 2 (la última, 9 hallazgos):** corregidos 6:
«Ya pagué» mostraba la respuesta de una consulta anterior, la imagen de una
categoría ocupada, el diálogo que se abría dos veces, el código del 401 en
dos lugares, `MYSQL_PWD` (obsoleto) en el healthcheck y el orden de los
imports. Descartados 3: RF-17 con un enlace viejo dice algo cierto y no
cambia nada; la línea agregada otra vez por el Cliente es suya; los estados
abiertos repetidos en dos consultas, con pocos pedidos por producto.

**Revisión en el navegador** (`playwright-cli`, Edge, escritorio e
«iPhone 15», claro y oscuro): 9 de 10 bien; «Agregar al carrito» medía 0 px
con el container query (corregido con `align-self: stretch` y verificado de
nuevo). Queda de antes: a 320 px, con un nombre largo, «Cerrar sesión» se
sale 22 px de la cabecera.

**Prueba manual de Fabio:** aprobada.
