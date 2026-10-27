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
