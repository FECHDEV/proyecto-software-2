# Plan — HU-14 Mis pedidos

Historia: `docs/historias/HU-14.md` · Issue #18 · Rama `feature/hu-14-mis-pedidos`

Backend: `GET /api/pedidos` con los pedidos del Cliente paginados y su pedido
pendiente aparte, después de vencerlo si corresponde. Frontend: la página
`/mis-pedidos` y los accesos desde el encabezado, el panel y cada pedido.

## Tarea 1 — La lista de pedidos · backend

**Archivos:** `dto/PedidoResumenResponse.java`, `dto/MisPedidosResponse.java`
(nuevos); `repository/PedidoRepository.java`, `service/PedidoService.java`,
`controller/PedidoController.java`.

- **API:** `GET /api/pedidos?pagina=N` (solo Cliente, ya cubierto por
  `/api/pedidos/**` en `SecurityConfig`; el cliente sale del token) →
  `MisPedidosResponse(pendiente: PedidoResumenResponse | null, pagina:
  PaginaResponse<PedidoResumenResponse>)`.
- `PedidoResumenResponse(idPedido, estado, confirmado, cantidadProductos,
  total, segundosParaPagar)`; `cantidadProductos` es la cantidad de líneas.
- **Repositorio**, una sola consulta por página, sin cargar las líneas (sin
  N+1):

```java
@Query(value = """
		select new bo.edu.uagrm.tienda.repository.PedidoRepository$FilaDePedido(
			p.idPedido, p.estado, p.confirmado, size(p.lineas), p.total, p.vence)
		from Pedido p where p.usuario.idUsuario = :idUsuario
		order by p.confirmado desc, p.idPedido desc""",
		countQuery = "select count(p) from Pedido p where p.usuario.idUsuario = :idUsuario")
Page<FilaDePedido> misPedidos(@Param("idUsuario") Long idUsuario, Pageable pagina);

record FilaDePedido(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, int cantidadProductos,
		BigDecimal total, LocalDateTime vence) {
}
```

**Lo delicado: vencer antes de listar (RF-4) y la página que no existe
(RF-8).** Con READ_COMMITTED, como lo demás que puede vencer un pedido
(`decisiones.md` → «Pagos»):

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public MisPedidosResponse misPedidos(Long idUsuario, int pagina) {
	vencerSiCorresponde(idUsuario); // RF-4: nunca «Pendiente» con la hora pasada (salvo con cobro, HU-13 RF-7)
	LocalDateTime ahora = LocalDateTime.now(clock);
	Page<FilaDePedido> filas = pedidoRepository.misPedidos(idUsuario, PageRequest.of(Math.max(pagina, 0), POR_PAGINA));
	if (filas.isEmpty() && filas.getTotalPages() > 0) { // RF-8: después de la última, la última
		filas = pedidoRepository.misPedidos(idUsuario, PageRequest.of(filas.getTotalPages() - 1, POR_PAGINA));
	}
	PedidoResumenResponse pendiente = idDelPendiente(idUsuario)
			.flatMap(id -> pedidoRepository.findByIdPedidoAndUsuario_IdUsuario(id, idUsuario))
			.map(pedido -> PedidoResumenResponse.from(pedido, ahora)).orElse(null);
	return new MisPedidosResponse(pendiente, PaginaResponse.from(filas, fila -> PedidoResumenResponse.from(fila, ahora)));
}
```

`POR_PAGINA = 10`. El parámetro `pagina` se recibe como `String`: si no es un
número entero se toma 0, sin 400, como hace el frontend con `paginaDeLaUrl`
(RF-8).

**Pruebas** (`PedidoIntegracionTest` o `MisPedidosIntegracionTest`, con el reloj
de prueba):
- `listaTodosElMasNuevoPrimeroConSuResumen` (con un pedido «Pago a devolver»)
  — HU-14 RF-1, RF-3
- `elPendienteVaAparteConSuTiempo` — HU-14 RF-2
- `abrirLaListaVenceElPendienteQuePasoLaHora` — HU-14 RF-4
- `unPendienteConCobroSigueArribaHastaLaConsulta` — HU-14 RF-2, RF-4
- `sinPedidosLaListaVieneVacia` — HU-14 RF-5
- `elPersonalRecibe403YSoloSeVenLosPropios` — HU-14 RF-7
- `paginasDeA10YUnaPaginaFueraDeRangoMuestraLaUltima` (también negativa y no
  numérica) — HU-14 RF-1, RF-8
- `PedidoRepositoryTest.misPedidosCuentaLasLineasSinCargarlas` — HU-14 RF-1

## Tarea 2 — La página y los accesos · frontend

**Archivos:** `core/modelos/pedido.ts`, `core/api/pedidos.service.ts`,
`features/mis-pedidos/mis-pedidos.page.ts`, `.html`, `.scss`, `.spec.ts`
(nuevos), `app.routes.ts`, `app.component.html` (y `.ts`),
`features/panel/panel.page.html` (y `.ts`), `features/pedido/pedido.page.html`.

- Modelo: `PedidoResumen` y `MisPedidos`, espejo de los DTO.
- `PedidosService.misPedidos(pagina: Signal<number>)` con `httpResource`.
- Ruta `mis-pedidos` (antes de `mis-pedidos/:id`), con `rolGuard` y
  `data: { rol: 'CLIENTE' }`, como la del pedido.
- La página, en el mundo de `DESIGN.md` (siguiendo la del pedido y el
  carrito):
  - arriba, si hay pendiente, su número, el reloj que descuenta (fuera de
    la zona, como el de la página del pedido) y «Pagar» como acción
    principal;
  - la lista: número, fecha, chip del estado con los colores de la página
    del pedido (también «Pago a devolver», como aviso), cantidad de productos y total; cada fila es un enlace a
    `/mis-pedidos/:id`;
  - la paginación «Anterior / Siguiente» con la página en la dirección
    (`?pagina=`, leída con `paginaDeLaUrl`), como productos;
  - vacía: «Todavía no tiene pedidos» y el enlace al catálogo;
  - el error de carga con «Reintentar».
- Encabezado: «Mis pedidos» con `LucidePackage`, junto al carrito, solo con
  `esCliente()`. Panel: la tarjeta «Mis pedidos» solo para el Cliente.
  Página del pedido: un enlace «Mis pedidos» junto al título.
- Los nombres y colores de los estados salen de `NOMBRES_DE_ESTADO`; el reloj
  `mm:ss` se comparte con la página del pedido en `shared/formato/tiempo.ts`
  (`reloj(segundos)`): técnico, `pedido.page` deja su `dosCifras`.

**Pruebas:**
- `mis-pedidos.page.spec`: `lista los pedidos con su estado, total y enlace`
  — HU-14 RF-1, RF-3; `el pendiente va arriba con el tiempo y Pagar` —
  HU-14 RF-2; `sin pedidos lleva al catálogo` — HU-14 RF-5; `cambia de
  página por la dirección` — HU-14 RF-1, RF-8; `un error de carga deja
  reintentar` — caso borde.
- `app.component.spec` (o el del encabezado): `Mis pedidos solo para el
  Cliente` — HU-14 RF-6; `panel.page.spec`: `la tarjeta Mis pedidos solo
  para el Cliente` — HU-14 RF-6; `pedido.page.spec`: `enlaza a la lista` —
  HU-14 RF-6.
- `app.routes.spec`: `mis-pedidos es del Cliente` (el personal va a su
  panel; sin sesión, a iniciarla con el destino) — HU-14 RF-7.
- `pedidos.service.spec`: `misPedidos pide la página` — HU-14 RF-1.
- `tiempo.spec`: `reloj con dos cifras` — HU-14 RF-2.

## Decisiones del plan

- **El pendiente también aparece en su lugar de la lista:** arriba va su
  resumen con «Pagar»; en la lista, su fila como cualquier otra. Así la
  paginación no salta.
- **La cantidad de productos son las líneas** (no la suma de cantidades, que
  mezcla unidades y metros).
- **Una página fuera de rango muestra la última,** sin error, como pide RF-8.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: interfaz (pantalla nueva
con datos), `/code-review`. `/security-review` no corresponde: no hay
superficie nueva (la API ya es solo del Cliente y la lista sale del token), ni
pagos ni cuentas.

## Resultado

*21/10/2026, `/sdd:cerrar HU-14`.* Las dos tareas, las revisiones y la prueba
manual del usuario («todo pasó»).

### Cobertura RF → pruebas

| RF | Backend | Frontend |
|---|---|---|
| RF-1 | `listaTodosElMasNuevoPrimeroConSuResumen`, `paginasDeA10…`, `misPedidosCuentaLasLineasSinCargarlas` | `lista los pedidos con su estado, total y enlace`, `cambia de página por la dirección`, `misPedidos pide la página` |
| RF-2 | `elPendienteVaAparteConSuTiempo`, `unPendienteConCobroSigueArribaHastaLaConsulta` | `el pendiente va arriba con el tiempo y Pagar`, `comoReloj` (tiempo.spec) |
| RF-3 | `listaTodosElMasNuevoPrimeroConSuResumen` (con «Pago a devolver») | `lista los pedidos con su estado…` |
| RF-4 | `abrirLaListaVenceElPendienteQuePasoLaHora`, `unPendienteConCobroSigueArriba…` | `al llegar a cero el pendiente se pide de nuevo cada 5 segundos`, `un pendiente que llega sin tiempo avisa…` |
| RF-5 | `sinPedidosLaListaVieneVacia` | `sin pedidos lleva al catálogo` |
| RF-6 | — | `Mis pedidos solo para el Cliente` (encabezado), `la tarjeta Mis pedidos solo para el Cliente` (panel), `enlaza a la lista de pedidos` (pedido) |
| RF-7 | `elPersonalRecibe403YSoloSeVenLosPropios` | `mis pedidos es solo del cliente` (rutas) |
| RF-8 | `paginasDeA10YUnaPaginaFueraDeRangoMuestraLaUltima` (negativa, basura, enorme y fuera de un int) | `cambia de página por la dirección`, `si la página pedida no existe, la dirección muestra la que se ve` |

### Suites

- Backend: **549 pruebas, 0 fallas, 1 omitida** (antes 541).
- Frontend: **695 pruebas** (antes 679); `npx ng build` sin avisos.

### Revisiones

**Interfaz (subagente):** 1 real y 5 menores, corregidos. Real: el chip con
tono «aviso» tomaba la clase `.aviso` del error de carga (borde rojo, más
alto). Menores: el ícono del encabezado a 44 px, «N.º» pegado al número, en
celular el plazo debajo del reloj y la paginación en una línea, y «No
encontramos ese pedido» lleva a Mis pedidos.

**`/code-review` ronda 1 (10, 9 corregidos):** una página enorme daba 500
(se acota en el servicio, un solo lugar); dos lecturas del reloj podían
dejar una fila pendiente sin recuadro (y una consulta repetida del
pendiente); recarga inútil si el pendiente llegaba en cero; 00:00 sin
explicación; el reloj corría sin pendiente; parpadeo al cambiar de página;
la dirección no mostraba la página corregida. Descartado: dos consultas
cuando la página está después de la última (camino raro).

**`/code-review` ronda 2 (8, todos corregidos):** el servidor truncaba los
segundos y un pedido con medio segundo por delante parecía «sin tiempo»: se
redondean hacia arriba y, mientras el pendiente sigue en 0, la página pide la
lista cada 5 segundos (sin las dos variables de estado). El vencimiento lee su
propio reloj y el pendiente de arriba es el que la lista muestra pendiente.
La dirección se limpia de basura, un número fuera de un `int` lleva a la
última y la paginación espera mientras carga.

`/security-review`: no corresponde (sin superficie nueva: la API ya es solo
del Cliente y la lista sale del token).

### Prueba manual

*21/10/2026.* El usuario probó con Docker (primer arranque con MySQL 8.4,
que migró sola la base local): accesos, la lista con sus estados, el
pendiente arriba, el vacío y las páginas. **Todo pasó.**

### Distinto de lo planeado

- `PedidoResumenResponse.de(...)` recibe los campos sueltos: el DTO no
  importa la fila del repositorio.
- El pendiente de arriba sale de los pedidos en estado pendiente (no de
  `idDelPendiente`), para que coincida siempre con la lista.
- `segundosParaPagar` de la lista se redondea hacia arriba (el de la página
  del pedido sigue truncando; allí la recarga es cada segundo).
- La página se acota en el servicio (el controlador solo lee dígitos).
