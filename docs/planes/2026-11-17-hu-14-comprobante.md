# HU-14 (ampliación) — El comprobante del Cliente

Historia: `docs/historias/HU-14.md` (RF-9 a RF-13). Decisión: `decisiones.md`
→ «Pedidos» (17/11/2026). Issue #117. El plan de HU-14 del 21/10/2026 no se
toca: es el historial. Corregido el 17/11/2026 tras `/sdd:analizar`: los
enlaces con un id basura, la prueba del pago mientras se mira la página, el
código de la autorización y lo que cubre la revisión de interfaz.

## Decisiones de diseño

- **Un endpoint propio, `GET /api/pedidos/{id}/comprobante`,** y no más campos
  en `PedidoResponse`. **Motivo:** el nombre de quien vendió, lo recibido y el
  cambio solo hacen falta en el ticket; la página del pedido no tiene por qué
  traerlos en cada lectura. Queda bajo `/api/pedidos/**`, que ya es solo del
  Cliente (RF-13), y sale del pedido del Cliente del token.
- **404 para todo lo que no tiene comprobante** (no existe, es de otro, o no
  está `PAGADO` ni `ENTREGADO`), con la misma `PedidoNoEncontradoException`.
  **Motivo:** no revela si el pedido existe (RF-12), y la pantalla no
  necesita distinguir los casos.
- **El medio de pago:** un pedido en línea no guarda `medioPago` y el
  comprobante lo manda `null`; la pantalla dice «En línea». En el mostrador,
  el `medioPago` guardado. *17/11/2026 (`/code-review`, decisión de Fabio):*
  antes decía `QR`, pero la página de pago del proveedor puede cobrar con
  otro medio y la tienda no se entera de cuál.
- **La fecha del ticket es `pagado`,** que en `PAGADO` y `ENTREGADO` siempre
  existe (en línea, al conciliar; en el mostrador, `cobrarEnTienda` y la
  consulta del QR). Si faltara, `confirmado`, como hace hoy el ticket del
  mostrador.
- **El ticket pasa a `shared/ticket/`** (`app-ticket`), una pieza sin
  negocio: recibe los textos ya armados y los dibuja con los estilos de
  pantalla y de impresión de hoy. La usan el comprobante del mostrador
  (`features/comprobante`) y el del Cliente (`features/comprobante-cliente`).
  **Motivo:** `shared/` no importa de `core/` ni de `features/`
  (`decisiones.md` → «Decisiones técnicas del frontend»), y hoy ninguna
  pantalla importa de otra: es costumbre, no regla, y se conserva. Con los
  textos armados en cada pantalla, el ticket no conoce `Venta` ni `Pedido`.
- **La impresión** sigue en `styles.scss`, que hoy esconde el encabezado
  con `body:has(app-comprobante)`: pasa a `body:has(app-ticket)` y vale para
  las dos páginas.

## Tareas

1. **Backend: el comprobante** (`dto/`, `service/PedidoService.java`,
   `controller/PedidoController.java`).
   - `record ComprobanteResponse(Long idPedido, LocalDateTime fecha, String
     cliente, String vendidoPor, List<LineaPedidoResponse> lineas, BigDecimal
     total, MedioPago medio, BigDecimal recibido, BigDecimal cambio)`, con
     `static from(Pedido)`. `cliente`: nombre y apellido del usuario del
     pedido. `vendidoPor`: nombre y apellido, o `null` en línea. `recibido` y
     `cambio`: solo en efectivo, si no `null`.
   - **Delicado (autorización), `PedidoService.comprobante`.** No bloquea ni
     vence: solo lee, y un pedido pendiente no tiene comprobante. El dueño
     sale del token (`idUsuario` del controlador), nunca de la petición:

     ```java
     private static final Set<EstadoPedido> CON_COMPROBANTE = EnumSet.of(EstadoPedido.PAGADO,
     		EstadoPedido.ENTREGADO);

     // HU-14 RF-10, RF-12: el de otro cliente, el que no existe y el que no fue una compra dan el mismo 404
     @Transactional(readOnly = true)
     public ComprobanteResponse comprobante(Long idUsuario, Long idPedido) {
     	return pedidoRepository.visibleParaElCliente(idPedido, idUsuario, VISIBLES_EN_TIENDA)
     			.filter(pedido -> CON_COMPROBANTE.contains(pedido.getEstado()))
     			.map(ComprobanteResponse::from)
     			.orElseThrow(PedidoNoEncontradoException::new);
     }
     ```
   - `PedidoController`: `@GetMapping(ID + "/comprobante")`.
   - Pruebas, en `ComprobanteIntegracionTest` (nueva, con el armado de
     `MisPedidosIntegracionTest` y `VentaTiendaIntegracionTest`):
     - `enLineaPagadoDaElTicketSinMedioNiAtendio` (RF-10)
     - `enLineaEntregadoTambienTieneComprobante` (RF-9, RF-10)
     - `ventaEnEfectivoDaRecibidoCambioYQuienVendio` (RF-10)
     - `ventaConQrPagadaDaQrSinRecibidoNiCambio` (RF-10)
     - `pendienteCanceladoVencidoYPagoADevolverDan404` (RF-12)
     - `ventaAnuladaDa404` (RF-12)
     - `elDeOtroClienteDa404` (RF-12)
     - `idQueNoEsUnNumeroDa404` (RF-12)
     - `elPersonalRecibe403YSinSesion401` (RF-13)

2. **Frontend: el ticket compartido** (`shared/ticket/`).
   - `interface DatosDelTicket { marca: string; titulo: string; fecha: string;
     estado: { texto: string; tono: string } | null; datos: { etiqueta:
     string; valor: string }[]; lineas: { nombre: string; detalle: string;
     importe: string }[]; total: string; pagos: { etiqueta: string; valor:
     string }[]; anulacion: string | null }`.
   - `Ticket` (`app-ticket`), con `input.required<DatosDelTicket>()`: el
     `<article>` de hoy, con su `id="titulo-ticket"` y «No válido como
     factura». Los estilos del ticket se mudan de
     `comprobante.page.scss` a `ticket.scss`.
   - `ComprobantePage` (mostrador) arma `DatosDelTicket` desde su `Venta` y
     usa `<app-ticket>`. Sin cambios de comportamiento: sus pruebas de hoy
     (`comprobante.page.spec.ts`) tienen que seguir en verde sin tocarlas,
     salvo los selectores que cambien de lugar.
   - `styles.scss`: `body:has(app-comprobante)` → `body:has(app-ticket)`.
   - Pruebas, en `ticket.spec.ts`:
     - `dibuja los datos, las líneas, el total y los pagos que recibe`
     - `sin estado no muestra el chip; con anulación la muestra`
     - `siempre dice «No válido como factura»`

3. **Frontend: el comprobante del Cliente** (`core/`, `features/`,
   `app.routes.ts`).
   - `core/modelos/pedido.ts`: `interface Comprobante`, espejo de
     `ComprobanteResponse`.
   - `PedidosService.comprobante(idPedido: Signal<number | null>):
     HttpResourceRef<Comprobante | undefined>`; sin id válido no pide nada.
   - Ruta `mis-pedidos/:id/comprobante`, `title: Comprobante · Tienda
     Montero`, `rolGuard` con `rol: 'CLIENTE'` (como `mis-pedidos/:id`).
   - `features/comprobante-cliente/`: `ComprobanteClientePage`. Arriba, solo
     en pantalla: «Imprimir», «Ver el pedido» y «Mis pedidos». El ticket:
     título «Pedido N.º X», sin estado; datos «Cliente» y, si hay
     `vendidoPor`, «Atendió»; pagos «Pago», y en efectivo «Recibido» y
     «Cambio». Con 404: «Este pedido no tiene comprobante», con «Ver el
     pedido» y «Mis pedidos». Con un id basura, lo mismo pero solo con «Mis
     pedidos»: «Ver el pedido» llevaría a otra dirección rota. Error de red:
     aviso y «Reintentar».
   - `PedidoPage`: «Ver comprobante» (enlace con forma de botón secundario)
     en las secciones de `PAGADO` y `ENTREGADO`.
   - Pruebas:
     - `pedidos.service.spec.ts`: `RF-9: pide el comprobante del pedido, y
       nada sin id`.
     - `comprobante-cliente.page.spec.ts`:
       - `RF-10: en línea muestra el cliente, las líneas, el total y «Pago:
         En línea», sin «Atendió» ni chip`
       - `RF-10: del mostrador en efectivo muestra «Atendió», lo recibido y
         el cambio`
       - `RF-11: «Imprimir» llama a window.print`
       - `RF-12: un 404 dice que no tiene comprobante, con los enlaces`
       - `RF-12: un id que no es un número no pide nada y enlaza solo a Mis
         pedidos`
       - `un error de red muestra «Reintentar», que vuelve a pedir`
     - `pedido.page.spec.ts`:
       - `RF-9: «Ver comprobante» en Pagado y Entregado, y no en Pendiente,
         Cancelado, Vencido, Pago a devolver ni Anulado`
       - `RF-9: al pasar a Pagado aparece «Ver comprobante» sin recargar`
     - `app.routes.spec.ts`: `RF-13: la ruta del comprobante es solo del
       Cliente`.

4. **Cierre.** `docs/lista-de-cambios.md`: el punto 1 pasa a «hecho» con el
   PR. `/sdd:cerrar HU-14` con las revisiones de `docs/flujo-sdd.md` →
   «Revisión antes del PR»; `/security-review` corresponde (endpoint nuevo
   con datos del pedido de un cliente). Lo propio de esta historia, en la
   revisión de interfaz: la **vista de impresión** (que salga solo el
   ticket, RF-11, que Vitest sobre jsdom no puede probar) y un nombre y un
   producto muy largos (`break-ui`, caso límite).

## Casos borde

- **Pago tardío:** un pedido en línea pagado tarde queda `PAGADO` y tiene
  comprobante; uno que quedó `PAGO_A_DEVOLVER`, no (404).
- **El pedido se paga mientras el Cliente mira la página:** «Ver
  comprobante» aparece cuando la página pasa a «Pagado», sin recargar
  (sale del mismo `pedido()` que ya se actualiza).
- **Una respuesta tardía tras cambiar de id en la dirección:** la maneja
  `httpResource`, que descarta la anterior.
- **El bundle inicial:** la página nueva va con `loadComponent` y sin iconos
  nuevos: no suma a `main` (el presupuesto de 500 kB está a 3 kB).

## Resultado

*17/11/2026 (`/sdd:cerrar`).*

### Cobertura RF → pruebas

| RF | Pruebas |
|---|---|
| RF-9 | `ComprobanteIntegracionTest.enLineaEntregadoTambienTieneComprobante`; `pedidos.service.spec` («RF-9: pide el comprobante…»); `pedido.page.spec` («RF-9: Ver comprobante en Pagado y Entregado…», «RF-9: al pasar a Pagado aparece…») |
| RF-10 | `ComprobanteIntegracionTest`: `enLineaPagadoDaElTicketSinMedioNiAtendio`, `ventaEnEfectivoDaRecibidoCambioYQuienVendio`, `ventaConQrPagadaDaQrSinRecibidoNiCambio`; `ticket.spec` (3); `comprobante-cliente.page.spec` («RF-10: en línea…», «RF-10: del mostrador en efectivo…») |
| RF-11 | `comprobante-cliente.page.spec` («RF-11: Imprimir llama a window.print»); que salga solo el ticket, en la revisión de interfaz |
| RF-12 | `ComprobanteIntegracionTest`: `pendienteCanceladoVencidoYPagoADevolverDan404`, `ventaAnuladaDa404`, `elDeOtroClienteDa404`, `idQueNoEsUnNumeroDa404`; `comprobante-cliente.page.spec` (404, id basura) |
| RF-13 | `ComprobanteIntegracionTest.elPersonalRecibe403YSinSesion401`; `app.routes.spec` («RF-13: la ruta del comprobante es solo del Cliente») |

### Suites

- Backend: `./mvnw test`, 844 pruebas, 0 fallas, 1 omitida (de antes).
- Frontend: `npx ng test --watch=false`, 964 pruebas en 82 archivos; `npm run
  lint` sin problemas; `npx ng build` sin avisos, inicial 494,12 kB.
- Una corrida dio dos pruebas por tiempo agotado (`app.routes.spec`,
  `usuarios.page.spec`): el servidor de desarrollo de la revisión de interfaz
  seguía levantado. Sin él, todo en verde.

### Revisiones

- **Subagente de interfaz** (frontend sin backend, la API y la sesión
  simuladas en el navegador; escritorio e iPhone 15, los dos temas, impresión,
  404, id basura, error de red, peores datos, el comprobante del mostrador).
  Confirmó: solo el ticket al imprimir, negro sobre blanco y de 72 mm; sin
  desbordes con nombres de 70 caracteres, productos de 150 y 15 líneas;
  «Ver comprobante» de 44 px con foco visible y contraste de 7,5:1 o más; un
  solo `h1`; `role="alert"` en el error; el mostrador igual que antes.
  **Corregido:** un comentario viejo repetido en `comprobante.page.scss`.
  **Pendientes, de antes (HU-23) y comunes a las dos páginas:** al tocar
  «Reintentar» el foco vuelve a `<body>`; en un rollo real de 80 mm con los
  márgenes que pone el navegador, el ticket de 72 mm más el margen lateral
  de `main` no entra y el navegador lo achica (`@page` y sin margen al
  imprimir). **Sin cambio:** «Ver comprobante» dentro de la sección
  `role="status"` (válido; se ve en la prueba manual) y los botones en dos
  filas en el celular (como en el mostrador).
- **`/security-review`** (controlador, servicio, DTO, consulta y
  `SecurityConfig`): sin hallazgos de confianza ≥ 8. Revisó el orden de las
  reglas de `/api/pedidos/**`, el dueño desde el token en
  `visibleParaElCliente`, el mismo 404 para lo ajeno, lo inexistente y lo que
  no fue una compra, y los datos que expone el comprobante (sin correo ni id
  del vendedor ni datos del proveedor de pago).
- **`/code-review`, primera ronda.**
  **Corregido:**
  - en línea el ticket decía «Pago: QR», pero un pedido en línea no guarda
    con qué se pagó y la página de pago del proveedor puede cobrar con otro
    medio: ahora el servidor manda el medio vacío y el ticket dice «En línea»
    (decisión de Fabio), con sus pruebas;
  - cómo se arman las líneas, los pagos y la fecha del ticket
  estaba repetido en las dos páginas: ahora en `core/modelos/ticket.ts`.
  **Descartado:**
  - 3 o 4 consultas por comprobante (el pedido y, de a una, sus líneas, el
    cliente y el vendedor): una lectura por clic, sin efecto con este tráfico;
  - la verificación del id de la ruta (`idDe`), repetida en las pantallas
    desde antes: fuera de esta historia;
  - unas 45 líneas de estilos de los botones repetidas con el mostrador: cada
    pantalla define los suyos, como en el resto del proyecto;
  - el nombre completo (`nombre + " " + apellido`) armado también en otros
    cuatro DTO desde antes: fuera de esta historia;
  - el `id="titulo-ticket"` fijo en el ticket compartido: hay un ticket por
    página, y el mostrador lo usa para llevar el foco al anular;
  - `backend/mvnw.cmd` modificado en la copia de trabajo: solo fines de
    línea, que dejó el PR #115 de Dependabot; no se commitea.
- **`/code-review`, segunda ronda** (solo las correcciones). **Corregido:**
  «En línea» sale de `NOMBRES_DE_CANAL`, no escrito aparte; pruebas propias
  de `core/modelos/ticket.ts` (`ticket.spec.ts`, 5). **Descartado:** que un
  medio vacío signifique «en línea» sin mandar el canal: la venta del
  mostrador siempre guarda su medio (`requireNonNull` desde HU-23, sin filas
  viejas). **Pendiente:** los reportes cuentan las compras en línea como QR
  (`VentaFila.medioDePago`, HU-23 RF-22), y ahora el ticket dice «En línea»;
  va a `docs/lista-de-cambios.md` → punto 4, con los reportes.

### Distinto de lo planeado

- «Ver comprobante» es un enlace con el estilo de los demás enlaces de la
  sección del estado (`.cerrado a`), no con forma de botón secundario.
- `core/modelos/ticket.ts` no estaba en el plan (salió de `/code-review`).
- En línea el medio de pago dice «En línea», no «QR» (`/code-review`).

### Prueba manual

*17/11/2026:* Fabio probó los escenarios en Docker (venta del mostrador en
efectivo a su cuenta, compra en línea, pedidos sin comprobante, la dirección
basura e imprimir): todo pasó. El cambio a «En línea» vino después, con sus
pruebas automáticas.
