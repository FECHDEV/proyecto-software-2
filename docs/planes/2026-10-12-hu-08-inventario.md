# Plan — HU-08 Inventario

Historia: `docs/historias/HU-08.md` · Issue #11 · Rama `feature/hu-08-stock`

Backend: stock y mínimo en el producto, movimientos inmutables y la API de
inventario para tres roles. Frontend: lista de inventario y la página de stock
de un producto con su historial.

## Tarea 1 — Entidades y repositorios · backend

**Archivos:** `entity/Producto.java` (stock y mínimo), `entity/UnidadVenta.java`
(`decimales()`), `entity/TipoMovimiento.java`, `entity/MotivoAjuste.java`,
`entity/MovimientoStock.java`, `repository/MovimientoStockRepository.java`,
`repository/ProductoRepository.java` (`inventario`).

- `Producto`: `stock` (`DECIMAL(9,2)`, no nulo, `@ColumnDefault("0")` para que
  MySQL complete los productos que ya existen) y `stockMinimo` (`DECIMAL(9,2)`,
  nulo). Métodos públicos `aplicarMovimiento(BigDecimal nuevoStock)`,
  `cambiarMinimo(BigDecimal)`, `isStockBajo()` (`minimo != null && stock <= minimo`).
- `UnidadVenta.decimales()`: 2 para METRO, KILO y LITRO; 0 para el resto.
- `TipoMovimiento`: ENTRADA, AJUSTE. `MotivoAjuste`: CONTEO, MERMA,
  DEVOLUCION, OTRO.
- `MovimientoStock` (tabla `movimiento_stock`, índice `id_producto, fecha`):
  `idMovimiento`, `producto` (`@ManyToOne(LAZY)`), `tipo`, `motivo` (nulo en
  la entrada), `cantidad` (`DECIMAL(10,2)`, con signo: la diferencia),
  `stockResultante` (`DECIMAL(9,2)`), `usuario` (`@ManyToOne(LAZY)`), `fecha`,
  `nota` (200). Sin setters: se crea con `entrada(...)` o `ajuste(...)` y no
  cambia (RF-12).
- `MovimientoStockRepository`: `findByProducto_IdProducto(Long, Pageable)` con
  `@EntityGraph("usuario")`.
- `ProductoRepository.inventario(texto, idCategoria, activo, soloBajo,
  Pageable)`: JPQL como `buscar`, con `(:soloBajo = false or (p.stockMinimo is
  not null and p.stock <= p.stockMinimo))`, texto en nombre o código.

**Pruebas** (`MovimientoStockRepositoryTest`, `ProductoRepositoryTest`):
- `guardaYLeeElMovimientoConSuUsuario` — HU-08 RF-3, RF-11
- `historialDelMasRecienteAlMasAntiguo` — HU-08 RF-11
- `productoNuevoEmpiezaConStockCeroYSinMinimo` — HU-08 RF-2
- `inventarioFiltraPorTextoCategoriaEstadoYStockBajo` — HU-08 RF-9, RF-10

## Tarea 2 — Servicio · backend

**Archivos:** `service/InventarioService.java`, `dto/EntradaRequest.java`,
`dto/AjusteRequest.java`, `dto/MinimoRequest.java`,
`dto/InventarioResponse.java`, `dto/MovimientoResponse.java`; excepciones
`AjusteSinCambioException` (409 `AJUSTE_SIN_CAMBIO`),
`StockFueraDeRangoException` (409 `STOCK_FUERA_DE_RANGO`),
`UnidadIncompatibleConStockException` (409 `UNIDAD_INCOMPATIBLE_CON_STOCK`);
`ProductoService.editar` (RF-13).

- `EntradaRequest(@NotNull @DecimalMin(value="0", inclusive=false)
  @DecimalMax("99999.99") @Digits(integer=5, fraction=2) cantidad, @Size(max=200) nota)`.
- `AjusteRequest(@NotNull @DecimalMin("0") @DecimalMax("9999999.99")
  @Digits(integer=7, fraction=2) contada, @NotNull MotivoAjuste motivo,
  @Size(max=200) nota)`; nota obligatoria con OTRO (`CamposInvalidosException`,
  campo `nota`).
- `MinimoRequest(@DecimalMin("0") @DecimalMax("9999999.99") @Digits(7,2) minimo)`:
  null lo quita.
- Los decimales según la unidad se validan en el servicio
  (`CamposInvalidosException`, campo `cantidad`/`contada`/`minimo`: «tiene que
  ser un número entero» o «admite hasta dos decimales»).
- Todos los movimientos bloquean la fila del producto
  (`findConBloqueoByIdProducto`, ya existe) antes de leer el stock (RF-8):

```java
@Transactional
public InventarioResponse registrarEntrada(Long idProducto, Long idUsuario, EntradaRequest solicitud) {
    Producto producto = bloquear(idProducto);
    exigirDecimales(producto.getUnidad(), solicitud.cantidad(), "cantidad");
    BigDecimal nuevo = producto.getStock().add(solicitud.cantidad());
    if (nuevo.compareTo(STOCK_MAXIMO) > 0) throw new StockFueraDeRangoException();
    producto.aplicarMovimiento(nuevo);
    movimientoRepository.save(MovimientoStock.entrada(producto, solicitud.cantidad(), nuevo,
            usuarioRepository.getReferenceById(idUsuario), LocalDateTime.now(clock), solicitud.nota()));
    return InventarioResponse.from(producto);
}
// registrarAjuste: igual, con diferencia = contada - stock; si es 0, AJUSTE_SIN_CAMBIO
```

- `ProductoService.editar`: si la unidad nueva no admite los decimales del
  stock o del mínimo, `UnidadIncompatibleConStockException` (el producto ya
  está bloqueado).

**Pruebas** (`InventarioServiceTest`, `ProductoServiceTest`):
- `entradaSumaYGuardaElMovimiento` — HU-08 RF-3
- `ajusteDejaLaCantidadContadaYGuardaLaDiferencia`, `ajusteACeroSeAcepta` — HU-08 RF-4
- `decimalesSegunLaUnidad` (2,5 en UNIDAD rechazado; 25,5 en METRO aceptado; 25,555 rechazado) — HU-08 RF-5
- `entradaQueSuperaElMaximoSeRechaza`, `ajusteSinCambioSeRechaza` — HU-08 RF-6
- `motivoOtroExigeNota` — HU-08 RF-7
- `minimoMarcaStockBajoYSeQuita` — HU-08 RF-9
- `productoInactivoAdmiteMovimientos` — HU-08 RF-10 (caso límite)
- `productoInexistente` — HU-08 RF-14
- `ProductoServiceTest.unidadSinDecimalesConStockDecimalSeRechaza` (y con stock 12 se acepta) — HU-08 RF-13

## Tarea 3 — API · backend

**Archivos:** `controller/InventarioController.java`, `config/SecurityConfig.java`.

| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/inventario?texto=&categoria=&activo=&bajo=&pagina=` | Empleado, Administrador, Contador |
| GET | `/api/inventario/{id}` | Empleado, Administrador, Contador |
| GET | `/api/inventario/{id}/movimientos?pagina=` | Empleado, Administrador, Contador |
| POST | `/api/inventario/{id}/entradas` | Empleado, Administrador |
| POST | `/api/inventario/{id}/ajustes` | Empleado, Administrador |
| PUT | `/api/inventario/{id}/minimo` | Empleado, Administrador |

`SecurityConfig`: `GET /api/inventario/**` con `hasAnyRole` de los tres; el
resto de `/api/inventario/**` con Empleado y Administrador. El usuario sale de
`@AuthenticationPrincipal Long idUsuario`.

**Pruebas** (`InventarioIntegracionTest`):
- `permisosPorRol` (Contador lee y recibe 403 al registrar; Cliente 403; sin sesión 401) — HU-08 RF-1
- `entradaYAjusteActualizanElStockYElHistorial` — HU-08 RF-2, RF-3, RF-4, RF-11
- `cantidadesInvalidasSeRechazanSinCambiarNada` — HU-08 RF-5, RF-6, RF-7
- `dosEntradasALaVezNoSePierden`, `entradaYAjusteALaVezSeAplicanEnOrden` (dos hilos con `MockMvcTester`; el stock resultante de cada movimiento es coherente con el orden) — HU-08 RF-8
- `minimoYFiltroDeStockBajo`, `listaPorNombreConFiltrosYPaginas` — HU-08 RF-9, RF-10
- `noHayFormaDeEditarNiBorrarUnMovimiento` (PUT/DELETE a un movimiento: 4xx) — HU-08 RF-12
- `cambiarAUnidadSinDecimalesConStockDecimalSeRechaza` — HU-08 RF-13
- `productoInexistenteYDatosBasura` — HU-08 RF-14

## Tarea 4 — Frontend

**Archivos:** `core/modelos/inventario.ts`, `core/api/inventario.service.ts`,
`core/guards/rol.guard.ts` (acepta `roles: Rol[]`), `shared/formato/cantidades.ts`
(`leerCantidad(texto, decimales)`, `problemaDeCantidad`),
`features/inventario/inventario.page.*` (lista),
`features/stock/stock.page.*` (`/inventario/:id`: stock, mínimo, entrada,
ajuste e historial), rutas, menú y panel (Empleado, Administrador y Contador),
mensajes nuevos.

- Lista: `httpResource` con los filtros en la URL (texto, categoría, estado,
  «Solo stock bajo», página), como «Productos». «Stock bajo» con texto e
  ícono, no solo color.
- Página del producto: stock actual y mínimo; formularios de entrada y ajuste
  (Signal Forms; cantidad con coma o punto y los decimales de su unidad;
  motivo de lista; nota) y de mínimo, ocultos para el Contador; historial
  paginado con la cantidad con signo («+10», «−2»).
- `MOTIVOS` y sus nombres en pantalla en el modelo.

**Pruebas:** `cantidades.spec.ts` (decimales por unidad); servicio (lista,
historial, entrada, ajuste, mínimo); `rol.guard` con varios roles; lista
(filtros en la URL, stock bajo, página); página del producto (entrada, ajuste
con motivo y nota con «otro», cantidad con decimales de más, mínimo, el
Contador no ve los formularios, rechazos del backend); rutas, menú y panel —
HU-08 RF-1 a RF-14.

## Decisiones

- El stock vive en `Producto` y el historial en `MovimientoStock`: leer el
  stock no exige sumar movimientos, y bloquear la fila del producto serializa
  los movimientos (RF-8). Es el mismo bloqueo que usan las imágenes (HU-07).
- La cantidad del movimiento se guarda con signo (la diferencia), y también el
  stock que quedó: el historial se lee sin recalcular.
- Los decimales por unidad se validan en el servicio: dependen del producto,
  no se pueden expresar como anotación del DTO.
- API propia `/api/inventario` en vez de `/api/gestion`: `/api/gestion/**` es
  solo del Administrador.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: `/code-review`,
`/security-review` (roles nuevos con acceso a escritura) y
`web-design-guidelines`.

## Resultado

*13/10/2026, `/sdd:cerrar HU-08`.*

### Cobertura RF → pruebas

| RF | Pruebas |
|---|---|
| RF-1 | `InventarioIntegracionTest.permisosPorRol`; `guards.spec.ts` «con una lista de roles…»; `app.routes.spec.ts` «el inventario es del empleado, el administrador y el contador»; `app.component.spec.ts` y `panel.page.spec.ts` (menú y acceso); stock: «el contador ve el stock y el historial, sin los formularios» |
| RF-2 | `ProductoRepositoryTest.productoNuevoEmpiezaConStockCeroYSinMinimo`; `InventarioIntegracionTest.entradaYAjusteActualizanElStockYElHistorial` |
| RF-3 | `MovimientoStockRepositoryTest.guardaYLeeElMovimientoConSuUsuario`; `InventarioServiceTest.entradaSumaYGuardaElMovimiento`; integración; front: «registra una entrada y actualiza el stock», servicio «registra una entrada sin nota vacía» |
| RF-4 | `InventarioServiceTest.ajusteDejaLaCantidadContadaYGuardaLaDiferencia`, `ajusteACeroSeAcepta`; integración; front: «registra un ajuste…» |
| RF-5 | `InventarioServiceTest.decimalesSegunLaUnidad`; `InventarioIntegracionTest.cantidadesInvalidasSeRechazanSinCambiarNada`; `cantidades.spec.ts`; front: «no envía decimales en un producto que se cuenta por unidad», «acepta decimales con coma…», «explica un dato que rechazó el backend con su campo» |
| RF-6 | `InventarioServiceTest.entradaQueSuperaElMaximoSeRechaza`, `ajusteSinCambioSeRechaza`; integración; front: «explica un ajuste sin cambio» |
| RF-7 | `InventarioServiceTest.motivoOtroExigeNota`; integración; front: «registra un ajuste y exige la nota con el motivo «Otro»» |
| RF-8 | `InventarioIntegracionTest.dosEntradasALaVezNoSePierden`, `entradaYAjusteALaVezSeAplicanEnOrden` |
| RF-9 | `ProductoRepositoryTest.inventarioFiltraPorTextoCategoriaEstadoYStockBajo`; `InventarioServiceTest.minimoMarcaStockBajoYSeQuita`; `InventarioIntegracionTest.minimoYFiltroDeStockBajo`; front: «guarda y quita el mínimo», «guarda «5,000» como 5», lista «muestra el stock… con la marca de stock bajo» |
| RF-10 | `ProductoRepositoryTest.inventarioFiltra…`; `InventarioServiceTest.productoInactivoAdmiteMovimientos`; `InventarioIntegracionTest.listaPorNombreConFiltrosYPaginas`, `categoriasDelFiltroIncluyenLasOcultas`; front: «lee los filtros de la URL», «filtra por categoría, estado y stock bajo», «busca después de una pausa…» |
| RF-11 | `MovimientoStockRepositoryTest.historialDelMasRecienteAlMasAntiguo`; integración; front: «muestra el stock, el mínimo y el historial», servicio «lee el producto y su historial por páginas» |
| RF-12 | `MovimientoStock` sin setters; `InventarioIntegracionTest.noHayFormaDeEditarNiBorrarUnMovimiento` |
| RF-13 | `ProductoServiceTest.unidadSinDecimalesConStockDecimalSeRechaza`; `InventarioIntegracionTest.cambiarAUnidadSinDecimalesConStockDecimalSeRechaza` |
| RF-14 | `InventarioServiceTest.productoInexistente`; `InventarioIntegracionTest.productoInexistenteYDatosBasura`; front: «avisa si la dirección no es la de un producto» |

### Suites

- Backend `./mvnw test`: **358 pruebas**, 0 fallas (antes 332).
- Frontend `npx ng test --watch=false`: **484 pruebas** (antes 453); `npx ng build` sin avisos.

### Revisiones

- **`/security-review`:** sin vulnerabilidades. Reglas por método y ruta
  (sin variantes que escapen), rol leído de la base en cada petición, quien
  registra sale del token, movimientos sin ruta de cambio, búsqueda con
  parámetros y comodines escapados, tamaño de página acotado, números
  extremos rechazados por las anotaciones. Aceptado: un `HEAD` del Contador
  recibe 403 (la pantalla no lo usa).
- **`/code-review`:** 4 hallazgos, corregidos: «5,000» en el mínimo lo quitaba
  sin avisar (leer y validar una cantidad usan ahora la misma forma); el
  filtro por categoría no mostraba las ocultas (`GET /api/inventario/categorias`);
  guardar el mismo mínimo escrito distinto dejaba un falso «cambios sin
  guardar»; el historial no volvía a la primera página con otro producto.
- **`web-design-guidelines`:** corregidos el `<p>` dentro del `<dl>` del
  resumen, la nota del ajuste que pasaba a obligatoria sin decirlo
  («(obligatoria)» y `aria-required`), el error de la nota de la entrada sin
  asociar, y «Quitar mínimo» ahora dice cuál era. Se quitó el `textarea`
  repetido de categorías. Descartados: el atributo `name` en los campos (no
  se usa en ningún formulario del proyecto) y la escala tipográfica en px
  (los tokens no la definen).

### Prueba manual

13/10/2026, Fabio, con Docker y cuentas de Empleado, Administrador y
Contador: pasaron todos los escenarios (entrada, ajuste con motivo y sin
cambio, «Otro» con nota, decimales por unidad y cambio de unidad rechazado,
mínimo y stock bajo, filtros con categoría oculta e inactivos, Contador solo
lectura, Cliente sin acceso, cambios sin guardar, vista de celular).
Verificación cruzada de David pendiente para el PR. *27/10/2026:* no se hizo; David se retiró del proyecto el 21/10/2026.

### Distinto de lo planeado

- `GET /api/inventario/categorias` (todas, también las ocultas) para el
  filtro: la gestión de categorías es solo del Administrador.
- `rolGuard` acepta `roles` además de `rol`.
- Los estilos de `select` y `textarea` pasaron a `_formulario.scss`; la
  página de stock usa solo `_lista.scss` y suma lo que le falta, para no
  pasar el presupuesto de estilos por componente.
- El stock, el mínimo y las cantidades de los movimientos se guardan siempre
  con dos decimales, así la respuesta es igual recién guardada y leída.
- Las pruebas del frontend se escribieron junto con el código, sin verlas
  fallar antes.
