# Plan — HU-08 (ampliación) El ajuste cuenta el estante

Historia: `docs/historias/HU-08.md` (RF-15 a RF-18; cambian RF-4 y RF-6) ·
Issue #11 · Rama `feature/hu-08-contar-el-estante` · Hallazgo A1 de
`docs/planes/2026-10-26-cierre-revision-total.md`

Backend: lo comprometido se calcula de las líneas de los pedidos abiertos, y
el ajuste lo descuenta de lo contado. Frontend: la ficha de stock y la lista
«Inventario» lo muestran. El plan de HU-08 del 12/10 no se toca.

## Decisiones técnicas

- **Lo comprometido no se guarda: se calcula** con una consulta sobre
  `LineaPedido` (pedidos `PENDIENTE_PAGO` y `PAGADO`, agrupado por producto).
  Guardarlo en `Producto` obligaría a mantenerlo en cada reserva, liberación,
  pago tardío y entrega; la consulta usa el índice de `pedido` por estado.
- **En el ajuste se calcula con el producto ya bloqueado:** confirmar,
  cancelar, vencer y el pago tardío también bloquean el producto, así que lo
  comprometido no cambia mientras tanto (RF-17). La entrega no bloquea el
  producto ni cambia el disponible: queda como caso límite de la historia.
- **La lista pide lo comprometido de los productos de la página** en una sola
  consulta (`in :ids`), sin una por fila.
- **El movimiento guarda `contado` y `comprometido`** (columnas nuevas,
  opcionales, solo en los ajustes); `ddl-auto=update` las agrega. Los ajustes
  viejos las tienen vacías y el historial no muestra nada para ellos.
- **409 `CONTEO_MENOR_QUE_COMPROMETIDO`** con la propiedad `comprometido`,
  como `TOTAL_CAMBIADO` lleva `total`.

## Tarea 1 — Lo comprometido y el ajuste · backend

**Archivos:** `repository/PedidoRepository.java` (consultas),
`service/InventarioService.java`, `entity/MovimientoStock.java`,
`dto/InventarioResponse.java`, `dto/MovimientoResponse.java`,
`exception/ConteoMenorQueComprometidoException.java` (nueva).

- `PedidoRepository`: `List<Comprometido> comprometidos(Collection<Long>
  idsProductos)` (record `Comprometido(Long idProducto, BigDecimal cantidad)`)
  y `BigDecimal comprometido(Long idProducto)`.
- `InventarioResponse` suma `BigDecimal comprometido`;
  `from(Producto, BigDecimal comprometido)`.
- `MovimientoResponse` suma `BigDecimal contado` y `BigDecimal comprometido`.
- `MovimientoStock.ajuste(…, contado, comprometido, …)`.

```java
// registrarAjuste: el producto primero, como confirmar, cancelar, vencer y el pago tardío; con él bloqueado, lo
// comprometido no cambia hasta el commit (RF-17)
Producto producto = bloquear(idProducto);
exigirDecimales(producto.getUnidad(), solicitud.contada(), "contada");
// … el motivo «otro» exige la nota, como antes
BigDecimal comprometido = pedidoRepository.comprometido(idProducto);
if (solicitud.contada().compareTo(comprometido) < 0) {
	throw new ConteoMenorQueComprometidoException(comprometido); // RF-18
}
BigDecimal disponible = solicitud.contada().subtract(comprometido);
BigDecimal diferencia = disponible.subtract(producto.getStock());
if (diferencia.signum() == 0) {
	throw new AjusteSinCambioException(); // RF-6: lo contado es lo que ya hay en el estante
}
producto.aplicarMovimiento(disponible);
movimientoRepository.save(MovimientoStock.ajuste(producto, diferencia, disponible, solicitud.contada(),
		comprometido, solicitud.motivo(), usuario, ahora, solicitud.nota()));
```

**Pruebas** (`InventarioIntegracionTest`, HU-08):

- `loComprometidoSumaPendientesYPagadosSinEntregar` — RF-15 (entregado,
  cancelado, vencido y a devolver no cuentan)
- `laFichaYLaListaMuestranLoComprometido` — RF-16
- `elAjusteDescuentaLoComprometidoDeLoContado` — RF-17, escenario
- `elHistorialMuestraLoContadoYLoComprometido` — RF-17
- `contarMenosDeLoComprometidoSeRechaza` — RF-18
- `contarLoMismoQueHayEnElEstanteNoCambiaNada` — RF-6
- `contarLoMismoQueLoComprometidoDejaElDisponibleEnCero` — caso límite
- `unAjusteYUnaConfirmacionALaVezNoPierdenStock` — RF-17, caso límite (con
  `aLaVez`: el stock final es el mismo en cualquier orden)
- `sinPedidosAbiertosElAjusteEsComoAntes` — caso límite; las pruebas de
  ajuste que ya existen (RF-4) siguen en verde

## Tarea 2 — La ficha y la lista · frontend

**Archivos:** `core/modelos/inventario.ts`, `core/api/mensajes-error.ts`,
`features/stock/stock.page.{html,ts,scss,spec.ts}`,
`features/inventario/inventario.page.{html,scss,spec.ts}`.

- La ficha: «Disponible», «Comprometido» y «En el estante» en la cabecera; la
  ayuda del ajuste dice que se escribe lo que hay en el estante y que lo
  comprometido se descuenta; en el historial, un ajuste con `contado` muestra
  «contados X, comprometidos Y».
- La lista: columna «Comprometido» en la tabla y en las tarjetas del celular.
- `CONTEO_MENOR_QUE_COMPROMETIDO` → «Contaron menos de lo que ya está vendido
  o reservado (N). Revise el conteo.»

**Pruebas:** `stock.page.spec.ts` (la cabecera con las tres cifras — RF-16;
el ajuste rechazado muestra el mensaje con lo comprometido — RF-18; el
historial con contados y comprometidos — RF-17), `inventario.page.spec.ts`
(la columna — RF-16), `mensajes-error.spec.ts` (el mensaje — RF-18).

## Revisiones (al cerrar)

Las de `docs/flujo-sdd.md` → «Revisión antes del PR» (tiene pantallas:
primero la de interfaz). `/security-review`: no hace falta (sin seguridad,
dinero ni superficie nueva; el endpoint del ajuste ya existe con sus
permisos). Prueba manual de Fabio con un pedido pendiente y uno pagado.

## Resultado

*27/10/2026.* Cerrada: la prueba manual de Fabio salió bien.

**Cobertura:**

| RF | Pruebas |
|---|---|
| RF-4 | `sinPedidosAbiertosElAjusteEsComoAntes`, las de ajuste de HU-08 (sin cambios) |
| RF-6 | `contarLoMismoQueHayEnElEstanteNoCambiaNada`, `unaEntradaNoDejaElEstantePorEncimaDelMaximo` |
| RF-15 | `loComprometidoSumaPendientesYPagadosSinEntregar` |
| RF-16 | `laFichaYLaListaMuestranLoComprometido`; `stock.page.spec` («muestra lo comprometido y lo que hay en el estante»), `inventario.page.spec` («muestra lo comprometido de cada producto») |
| RF-17 | `elAjusteDescuentaLoComprometidoDeLoContado`, `elHistorialMuestraLoContadoYLoComprometido`, `contarLoMismoQueLoComprometidoDejaElDisponibleEnCero`, `unAjusteYUnaConfirmacionALaVezNoPierdenStock`; `stock.page.spec` (el historial) |
| RF-18 | `contarMenosDeLoComprometidoSeRechaza`; `mensajes-error.spec`, `stock.page.spec` (el rechazo con el número) |

Cambiaron dos pruebas que fijaban la regla vieja:
`PedidoIntegracionTest.unAjusteConPedidoPendienteDescuentaLoComprometido`
(antes «UsaLoContado», el caso límite de HU-12 que esta decisión reemplaza) y
`DespachoIntegracionTest.soloSeEntregaUnPedidoPagado` (cuenta lo comprometido
para dejar el disponible en 0).

**Suites:** backend 735 pruebas, 0 fallas, 1 omitida; frontend 69 archivos,
856 pruebas; `ng build` sin avisos.

**Revisión de interfaz (subagente, Edge, escritorio, Pixel 7 y 360 px,
claro y oscuro):** sin errores de consola ni desbordes. Corregidos: «stock»
ambiguo en las ayudas de entrada y mínimo, la ayuda del ajuste, la unidad en
las cifras nuevas de la ficha y de la lista (y el 0 comprometido en tono
suave), la cabecera 2×2 en el celular, el historial con el vocabulario de la
cabecera («en el estante 14, comprometido 5»). Descartados: «Sin mínimo»
grande (ya era así), la suma en el navegador (cantidades de dos decimales).

**`/code-review` medium, ronda 1:** corregidos: `READ_COMMITTED` en el ajuste
(como todo lo que lee después de bloquear), el comentario que decía que lo
comprometido no cambia (la entrega sí lo cambia: caso límite aceptado), la
respuesta copiada campo a campo, la regla de qué pedidos cuentan en dos
consultas (queda una), la ayuda con «las 5 unidad», el texto del escenario,
y una entrada que podía dejar el estante por encima del máximo (prueba
nueva). Descartados: la consulta extra al registrar una entrada o cambiar el
mínimo (barata y la respuesta la necesita) y lo comprometido viejo en la
página (el backend valida con lo actual y el rechazo dice el número).

**`/code-review` medium, ronda 2 (la última):** corregidos: el mensaje de
`STOCK_FUERA_DE_RANGO` habla del estante, la regla nueva de la entrada en
RF-6 y en `decisiones.md`, el Markdown de la historia, `READ_COMMITTED` al
cambiar el mínimo, el comentario de `liberar`. Descartado: validar la
entrada contra el estante en la página (el backend la rechaza con el
mensaje).

**Distinto de lo planeado:** `comprometido(id)` usa la misma consulta que la
lista; la entrada también mira lo comprometido (regla nueva de RF-6); las
etiquetas de la ficha y de la lista pasan de «Stock» a «Disponible», y el
campo del ajuste a «En el estante».
