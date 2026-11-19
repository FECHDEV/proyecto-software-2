# HU-24 — Descuentos

Historia: `docs/historias/HU-24.md` (RF-1 a RF-20). Decisión: `decisiones.md`
→ «Descuentos» (18/11/2026), con las notas en «Reportes» y «Venta en
tienda». Issue #120. Entrega: viernes 20/11/2026.

Se parte en **tres PR**, en orden y cada uno desde `main` con el anterior ya
mergeado: `feature/hu-24-descuentos` (PR 1, «Parte de #120»),
`feature/hu-24-descuentos-tienda` (PR 2, «Parte de #120») y
`feature/hu-24-descuentos-reportes` (PR 3, `Closes #120`). El PR 1 lleva
todo el riesgo de dinero y deja lo cobrado correcto en los dos canales; los
PR 2 y 3 solo muestran lo que el PR 1 ya calcula y guarda. Si falta tiempo,
se recortan RF-13 (filtro y orden) y después RF-19 y RF-20 (reportes), que se
pueden sumar en una ampliación porque la línea ya guarda todo desde el PR 1.

## Decisiones de diseño

- **`PrecioDeVenta` vive en `entity/`** (un record sin dependencias, como un
  valor): `LineaPedido` lo recibe y la entidad no importa de `service/`.
  `PreciosDeVenta` (`service/`) lee los descuentos vigentes y arma los
  `PrecioDeVenta`. **Es el único lugar del cálculo** (RF-8, RF-9); la consulta
  del catálogo (RF-13) repite la fórmula en JPQL y una prueba compara las dos.
- **El descuento se aplica al precio unitario,** y el subtotal sigue siendo
  `precio × cantidad` con HALF_UP en `LineaPedido`. **Motivo:** el precio que
  se ve, el del ticket («2 × Bs 85,00») y el total que calcula la pantalla del
  mostrador (`venta.page.ts` → `centavos()`) coinciden al centavo sin tocar
  ese cálculo.
- **En los DTO públicos y del mostrador, `precio` es el que se cobra,** y se
  suman `precioNormal` y `descuento` (nulos sin descuento). **Motivo:** si
  `precio` siguiera siendo el normal, `venta.page.ts` calcularía otro total,
  el servidor respondería `TOTAL_CAMBIADO` y `actualizarPrecios()`
  (`ventas.service.ts` → `precioDeHoy`, que lee `GET /api/productos/{id}`)
  traería otra vez el normal: un rechazo sin fin. La gestión
  (`ProductoResponse`, `ProductoRequest`) no cambia.
- **La línea guarda `precio_normal`, `descuento` y `descontado`,** nulos sin
  descuento y en las líneas viejas; sin clave foránea al descuento (se puede
  borrar o terminar). `descontado` = `round(precioNormal × cantidad) −
  subtotal`, calculado en Java al confirmar: el reporte solo suma y el
  redondeo no depende de la base.
- **Los vigentes se leen una vez por respuesta** (`DescuentoRepository.vigentes`,
  tabla chica), no una consulta por producto: el catálogo, la portada, el
  carrito y la búsqueda del mostrador arman sus listas con un solo mapa.
- **El solapamiento (RF-2) se valida sin bloquear filas:** tomar el bloqueo
  del producto metería la gestión en el orden de bloqueos de los pedidos. Si
  dos Administradores colaran dos descuentos que se cruzan, RF-9 aplica el
  mayor y el dinero no se rompe (`decisiones.md` → «Descuentos»).
- **Fechas `LocalDate`** con `LocalDate.now(clock)`: el `Clock` ya está en
  `America/La_Paz` (`ClockConfig`).
- **Sin cambios en el cobro, la conciliación, el pago tardío ni la
  anulación:** usan `pedido.getTotal()` y las líneas guardadas
  (`PagoService` línea 138, `VentaTiendaService.crear` línea 145). Se cubren
  con pruebas (RF-17).

## PR 1 — El cálculo, la gestión y lo cobrado (backend)

Rama `feature/hu-24-descuentos`. Primer commit: la historia, las decisiones,
`CLAUDE.md`, las notas de HU-09, HU-10, HU-11, HU-18 y HU-23 y este plan.

### Tarea 1. `PrecioDeVenta` (RF-8)

- `entity/PrecioDeVenta.java`. **Delicado (dinero), completo:**

  ```java
  // El precio de un producto hoy (HU-24 RF-8): el normal, el porcentaje vigente (nulo sin descuento), el que se cobra y
  // el último día del descuento (para el JSON-LD, RF-14)
  public record PrecioDeVenta(BigDecimal normal, Integer descuento, BigDecimal cobrado, LocalDate fin) {

  	static final BigDecimal MINIMO = new BigDecimal("0.01");
  	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  	public static PrecioDeVenta sinDescuento(BigDecimal normal) {
  		return new PrecioDeVenta(normal, null, normal, null);
  	}

  	public static PrecioDeVenta de(BigDecimal normal, int descuento, LocalDate fin) {
  		BigDecimal cobrado = normal.multiply(BigDecimal.valueOf(100 - descuento))
  				.divide(CIEN, 2, RoundingMode.HALF_UP).max(MINIMO);
  		return new PrecioDeVenta(normal, descuento, cobrado, Objects.requireNonNull(fin));
  	}

  	public boolean conDescuento() {
  		return descuento != null;
  	}
  }
  ```
  `divide(CIEN, 2, HALF_UP)` es exacto: `normal × (100 − p)` tiene dos
  decimales y dividir por 100 solo corre la coma, así que el único redondeo es
  el de los centavos.
- Pruebas, `entity/PrecioDeVentaTest`:
  - `sinDescuentoCobraElNormal` (RF-8)
  - `quinceSobreCuatroCincuentaDaTresOchentaYTres` (RF-8: 3,825 sube)
  - `noventaSobreElMaximoDaDiez` (99 999,99 → 10,00)
  - `nuncaBajaDeUnCentavo` (0,01 y 0,04 al −90 % → 0,01; 0,05 → 0,01)
  - `unoPorCientoSobreUnBoliviano` (1,00 → 0,99)

### Tarea 2. `Descuento` y su repositorio (RF-1 a RF-5, RF-9, RF-10)

- `entity/Descuento.java`, tabla `descuento`:
  `idDescuento` (`id_descuento`, IDENTITY), `producto` (`@ManyToOne` LAZY,
  `id_producto`, nulo), `categoria` (`@ManyToOne` LAZY, `id_categoria`,
  nulo), `porcentaje` (`int`), `inicio` y `fin` (`LocalDate`, no nulos),
  `creadoPor` (`id_creado_por`, no nulo), `creado` (`LocalDateTime`).
  Índices `idx_descuento_producto_fin (id_producto, fin)` e
  `idx_descuento_categoria_fin (id_categoria, fin)`.
  - `static Descuento deProducto(Producto, int, LocalDate, LocalDate, Usuario,
    LocalDateTime)` y `deCategoria(Categoria, …)`: exactamente uno de los dos.
  - `void cambiar(int porcentaje, LocalDate inicio, LocalDate fin)`,
    `void terminar(LocalDate hoy)` (fin = hoy − 1),
    `EstadoDescuento estado(LocalDate hoy)`.
  - `entity/EstadoDescuento`: `PROGRAMADO`, `VIGENTE`, `VENCIDO`.
- `repository/DescuentoRepository.java`:
  - `List<Descuento> vigentes(LocalDate hoy)`: `inicio <= :hoy and fin >= :hoy`.
  - `List<Descuento> cruzados(Long idProducto, Long idCategoria, LocalDate
    inicio, LocalDate fin, Long sinId)`: con `left join d.producto` y
    `left join d.categoria` (un join implícito dejaría afuera los de la otra
    clase), `d.inicio <= :fin and d.fin >= :inicio` y `(:sinId is null or
    d.idDescuento <> :sinId)`.
  - `Page<Descuento> listar(LocalDate hoy, Pageable)`: vigentes, programados
    y vencidos (`order by case …`), después `inicio desc, idDescuento desc`,
    con `@EntityGraph` de producto y categoría.
- Pruebas, `entity/DescuentoTest`:
  - `estadoSegunHoy` (RF-3, RF-10: el día de inicio y el de fin son
    «Vigente»; el siguiente, «Vencido»)
  - `terminarDejaElFinEnAyer` (RF-5)
  - `exigeProductoOCategoria` (RF-1)
- Pruebas, `repository/DescuentoRepositoryTest` (con `TestEntityManager`,
  `flush()` y `clear()`):
  - `vigentesIncluyeElPrimerYElUltimoDia` (RF-10)
  - `cruzadosEncuentraElQueSeTocaUnDia` (RF-2: fin de uno = inicio del otro)
  - `cruzadosNoMezclaProductoConCategoria` (RF-2)
  - `cruzadosSinSiMismoAlEditar` (RF-4)
  - `listarVigentesProgramadosYVencidos` (RF-3)

### Tarea 3. `PreciosDeVenta` (RF-8, RF-9, RF-10)

- `service/PreciosDeVenta.java`, `@Service`, con `DescuentoRepository` y
  `Clock`. **Delicado (dinero), completo:**

  ```java
  // El único lugar donde un producto toma su precio con descuento (HU-24 RF-8 a RF-10). Lee los vigentes de hoy una
  // sola vez por llamada: la tabla es chica y así una página del catálogo no hace una consulta por producto
  // RF-9: gana el mayor porcentaje; si empatan, el que termina después (su fin es la validez del JSON-LD)
  private static final Comparator<Descuento> MEJOR = Comparator.comparingInt(Descuento::getPorcentaje)
  		.thenComparing(Descuento::getFin);

  @Transactional(readOnly = true)
  public Map<Long, PrecioDeVenta> para(Collection<Producto> productos) {
  	LocalDate hoy = LocalDate.now(clock);
  	Map<Long, Descuento> porProducto = new HashMap<>();
  	Map<Long, Descuento> porCategoria = new HashMap<>();
  	BinaryOperator<Descuento> mejor = BinaryOperator.maxBy(MEJOR);
  	for (Descuento descuento : descuentoRepository.vigentes(hoy)) {
  		if (descuento.getProducto() != null) {
  			porProducto.merge(descuento.getProducto().getIdProducto(), descuento, mejor);
  		} else {
  			porCategoria.merge(descuento.getCategoria().getIdCategoria(), descuento, mejor);
  		}
  	}
  	Map<Long, PrecioDeVenta> precios = new HashMap<>();
  	for (Producto producto : productos) {
  		// El propio y el de la categoría actual (caso límite: cambió de categoría)
  		Descuento ganador = Stream.of(porProducto.get(producto.getIdProducto()),
  				porCategoria.get(producto.getCategoria().getIdCategoria())).filter(Objects::nonNull).max(MEJOR)
  				.orElse(null);
  		precios.put(producto.getIdProducto(), ganador == null ? PrecioDeVenta.sinDescuento(producto.getPrecio())
  				: PrecioDeVenta.de(producto.getPrecio(), ganador.getPorcentaje(), ganador.getFin()));
  	}
  	return precios;
  }

  public PrecioDeVenta para(Producto producto) {
  	return para(List.of(producto)).get(producto.getIdProducto());
  }
  ```
- Pruebas, `service/PreciosDeVentaTest` (Mockito, con `Clock.fixed`):
  - `sinDescuentosCobraElNormal` (RF-8)
  - `elDelProductoYElDeLaCategoriaGanaElMayor` (RF-9, los dos sentidos)
  - `dosDelMismoProductoGanaElMayor` (caso límite: dos Administradores)
  - `usaLaCategoriaActual` (caso límite: cambió de categoría)
  - `leeLosVigentesUnaSolaVez` (`verify(…, times(1)).vigentes(hoy)` con
    varios productos)

### Tarea 4. La línea del pedido guarda lo cobrado (RF-15, RF-17)

- `LineaPedido`: columnas `precio_normal` (7,2), `descuento` (`Integer`) y
  `descontado` (11,2), nulas sin descuento. **Delicado (dinero), completo:**

  ```java
  LineaPedido(Pedido pedido, Producto producto, BigDecimal cantidad, PrecioDeVenta precioDeVenta) {
  	this.pedido = Objects.requireNonNull(pedido);
  	this.producto = Objects.requireNonNull(producto);
  	this.nombre = producto.getNombre();
  	this.unidad = producto.getUnidad();
  	this.costo = producto.getCosto();
  	this.cantidad = cantidad.setScale(2, RoundingMode.UNNECESSARY);
  	// HU-24 RF-15: el precio que se cobra; con descuento, también el normal, el porcentaje y lo que se ahorró
  	this.precio = precioDeVenta.cobrado();
  	this.subtotal = precio.multiply(this.cantidad).setScale(2, RoundingMode.HALF_UP);
  	if (precioDeVenta.conDescuento()) {
  		this.precioNormal = precioDeVenta.normal();
  		this.descuento = precioDeVenta.descuento();
  		this.descontado = precioNormal.multiply(this.cantidad).setScale(2, RoundingMode.HALF_UP).subtract(subtotal);
  	}
  }
  ```
- `Pedido.agregarLinea(Producto, BigDecimal, PrecioDeVenta)`, **sin** dejar la
  versión sin precio: así el compilador obliga a cada llamada a decidir el
  precio, y ninguna se salta el descuento. Las 15 llamadas de las pruebas
  (`PedidoTest`, `VentasDePrueba`, `PedidoRepositoryTest`, `CobroRepositoryTest`,
  `InventarioIntegracionTest` y las de reportes) pasan
  `PrecioDeVenta.sinDescuento(producto.getPrecio())`.
- `PedidoService.confirmar` (línea 140): antes de las líneas,
  `Map<Long, PrecioDeVenta> precios = preciosDeVenta.para(productos del
  carrito)`, y `pedido.agregarLinea(producto, cantidad, precios.get(id))`. Lo
  demás no cambia: la comparación con `totalEsperado` (líneas 142 y 143) ya es
  RF-16.
- `VentaTiendaService.registrar` (línea 196): lo mismo con los productos ya
  bloqueados. La comparación (líneas 198 y 199) y lo recibido no cambian.
- `LineaPedidoResponse`: suma `precioNormal`, `descuento` y `descontado`
  (`PedidoResponse`, `VentaResponse` y `ComprobanteResponse` los heredan).
- Pruebas, `entity/PedidoTest` (ampliada):
  - `lineaConDescuentoGuardaCobradoNormalPorcentajeYDescontado` (RF-15)
  - `subtotalConDescuentoYCantidadDecimal` (RF-8: 3,83 × 0,69 = 2,64; el
    normal 4,50 × 0,69 = 3,11, descontado 0,47)
  - `lineaSinDescuentoDejaNulos` (RF-15)
  - `totalSumaLosSubtotalesConDescuento` (RF-15)
- Pruebas, `PedidoIntegracionTest` (ampliada):
  - `confirmaConElPrecioConDescuentoYElCobroPideEseTotal` (RF-15: el total y
    el monto que recibe el simulador al generar el cobro)
  - `descuentoQueEmpiezaEntreVerYConfirmarDaTotalCambiado` (RF-16, con el
    reloj cruzando la medianoche de Bolivia)
  - `descuentoQueTerminaEntreVerYConfirmarDaTotalCambiado` (RF-16)
  - `terminarEditarElDescuentoOCambiarElPrecioNoTocaElPedido` (RF-17)
  - `vencidoVuelveAlCarritoConElPrecioDelDia` (RF-17, caso límite)
- Pruebas, `PagoIntegracionTest` (ampliada):
  - `pagoTardioDespuesDelFinDelDescuentoQuedaConSuTotal` (RF-17)
- Pruebas, `VentaTiendaIntegracionTest` (ampliada):
  - `ventaEnEfectivoCobraElPrecioConDescuento` (RF-15: total, recibido y
    cambio)
  - `ventaConQrGeneraElCobroConElTotalConDescuento` (RF-15, caso límite del QR
    pendiente)
  - `totalDeLaPantallaSinDescuentoDaTotalCambiado` (RF-16)
  - `anularUnaVentaConDescuentoDevuelveElStock` (RF-17)
- Pruebas, `ComprobanteIntegracionTest` (ampliada):
  - `lineasConDescuentoLlevanNormalPorcentajeYDescontado` (RF-15, para RF-18)

### Tarea 5. Precios en la API pública, el carrito y el mostrador (RF-12)

- `ProductoTarjetaResponse.from(Producto, PrecioDeVenta)` y
  `ProductoDetalleResponse.from(Producto, PrecioDeVenta, relacionados)`:
  `precio` = `cobrado`, más `precioNormal` y `descuento` (nulos sin descuento).
  `ProductoDetalleResponse` suma `finDescuento` (`LocalDate`, nulo), que sale
  de `PrecioDeVenta.fin()`, para el JSON-LD (RF-14).
- `CatalogoService`: `listar` (línea 56), `portada` y `vitrina` (línea 100)
  piden los precios de toda la página o de todos los carruseles juntos, y
  `detalle` (línea 78) los del producto y sus relacionados.
- `CarritoService.armar`/`linea` (líneas 141 a 157): `linea` deja de ser
  estática y recibe el mapa; subtotal y total con `cobrado`.
  `LineaCarritoResponse` suma `precioNormal` y `descuento`.
- `VentaTiendaService.buscarProductos` (línea 120) y
  `ProductoVentaResponse.from(Producto, PrecioDeVenta)`.
- Pruebas, `CatalogoIntegracionTest` (ampliada):
  - `tarjetaYDetalleConDescuentoDanCobradoNormalYPorcentaje` (RF-12)
  - `sinDescuentoPrecioNormalYDescuentoNulos` (RF-12)
  - `noExponeElCostoNiQuienCreoElDescuento` (RF-12: el JSON no tiene `costo`
    ni `creadoPor`)
  - `alDiaSiguienteDelFinVuelveElPrecioNormal` (RF-10)
  - `elDetalleTraeElFinDelDescuento` (RF-14)
  - `categoriaOcultaConDescuentoNoSeLista` (caso límite: el descuento sigue
    guardado y vuelve a verse al mostrar la categoría)
- Pruebas, `CarritoIntegracionTest` (ampliada):
  - `lineaYTotalConElPrecioConDescuento` (RF-12, RF-11)
- Pruebas, `VentaTiendaIntegracionTest` (ampliada):
  - `laBusquedaDaElPrecioConDescuento` (RF-12; caso límite del mostrador)

### Tarea 6. La gestión de descuentos (RF-1 a RF-7)

- `dto/DescuentoRequest(Long idProducto, Long idCategoria, Integer
  porcentaje, LocalDate inicio, LocalDate fin)`, con `@NotNull` en el
  porcentaje y las fechas y `@Min(1) @Max(90)` en el porcentaje, y
  `@JsonIgnoreProperties` como los demás pedidos. `12.5` ya se rechaza:
  `spring.jackson.deserialization.accept-float-as-int=false`
  (`application.properties`). Para
  editar, `DescuentoCambioRequest(Integer porcentaje, LocalDate inicio,
  LocalDate fin)`.
- `dto/DescuentoResponse(Long idDescuento, Long idProducto, String producto,
  Long idCategoria, String categoria, int porcentaje, LocalDate inicio,
  LocalDate fin, EstadoDescuento estado)` y `dto/DescuentoGuardadoResponse(
  DescuentoResponse descuento, List<String> bajoElCosto)` (RF-6: nombres de
  los productos activos que quedan por debajo de su costo, hasta 10, y el
  total).
- `service/DescuentoService.java`: `listar(int pagina)`, `crear(…, Long
  idAdministrador)`, `cambiar(Long, …)`, `borrar(Long)`, `terminar(Long)`.
  Valida RF-1 (un id que no existe es `CAMPOS_INVALIDOS` en `idProducto` o
  `idCategoria`; inicio antes de hoy, fin antes del inicio o más de 366 días),
  RF-2 (`DescuentoSolapadoException`, 409 `DESCUENTO_SOLAPADO`, con
  `inicio`, `fin` y `porcentaje` del otro en `propiedades()`), RF-4 y RF-5
  (`DescuentoNoEditableException`, 409 `DESCUENTO_NO_EDITABLE`),
  `DescuentoNoEncontradoException` (404 `DESCUENTO_NO_ENCONTRADO`). Terminar
  uno que empezó hoy lo borra (RF-5).
- `controller/GestionDescuentosController.java`, `/api/gestion/descuentos`:
  `GET` (página de 20), `POST`, `PUT /{id}`, `DELETE /{id}`,
  `POST /{id}/terminar`. `SecurityConfig` no cambia: `/api/gestion/**` ya es
  solo del ADMINISTRADOR (RF-7).
- Pruebas, `DescuentosIntegracionTest` (nueva):
  - `creaUnoDeProductoYOtroDeCategoria` (RF-1)
  - `rechazaPorcentajeFueraDeRangoODecimal` (RF-1: 0, 91, −5, 12.5, texto)
  - `rechazaFechasInvalidas` (RF-1: inicio ayer, fin antes del inicio, más de
    366 días, texto)
  - `rechazaProductoYCategoriaANingunoOLosDos` (RF-1)
  - `rechazaUnProductoOCategoriaQueNoExiste` (RF-1)
  - `rechazaElQueSeCruzaYDiceConCual` (RF-2)
  - `productoYCategoriaNoSeCruzan` (RF-2, RF-9)
  - `listaVigentesProgramadosYVencidosDeA20` (RF-3)
  - `programadoSeEditaYSeBorra` (RF-4)
  - `editarNoCambiaElProductoNiLaCategoria` (RF-4)
  - `vigenteSeTerminaYDejaDeAplicarse` (RF-5: la tarjeta vuelve al precio
    normal)
  - `vigenteQueEmpezoHoySeBorraAlTerminar` (RF-5)
  - `vigenteNoSeEditaNiSeBorraYVencidoNoSeToca` (RF-5)
  - `avisaLosProductosQueQuedanBajoElCosto` (RF-6)
  - `idQueNoEsUnNumeroDa400` y `idQueNoExisteDa404` (datos basura)
  - `empleadoYContadorReciben403YSinSesion401` (RF-7)

## PR 2 — La tienda y el mostrador (frontend)

### Tarea 7. El precio con descuento en pantalla (RF-11)

- `shared/precio/precio.ts` (`app-precio`, nueva), sin negocio:
  `input.required<number>() precio`, `input<number | null>() precioNormal`,
  `input<number | null>() descuento`, `input<string>() sufijo` («/ metro»).
  Sin descuento, el precio como hoy. Con descuento: el normal en `<s>`, el
  cobrado y una marca «−15 %»; para lectores, «Precio normal Bs 100,00, ahora
  Bs 85,00, 15 % de descuento». Estilo con tokens y `:host`; la marca y el
  tachado se definen en `DESIGN.md` con `impeccable` (no hay token de rebaja).
- Modelos: `ProductoEnTienda`, `ProductoEnDetalle` (`core/modelos/catalogo.ts`),
  `LineaDeCarrito` (`carrito.ts`), `ProductoParaVenta` (`venta.ts`) y
  `LineaDePedido` (`pedido.ts`) suman `precioNormal: number | null` y
  `descuento: number | null` (`LineaDePedido` también `descontado`;
  `ProductoEnDetalle`, `finDescuento: string | null`). `DatosDeTarjeta`
  (`shared/tarjeta-producto`) y `aTarjeta` los pasan.
- Usan `app-precio`: `tarjeta-producto.html` (líneas 30 y 31),
  `detalle.page.html` (líneas 28 y 29), `carrito.page.html` (líneas 37 y 38),
  `venta.page.html` (línea 23) y `busqueda-de-productos.html` (línea 30).
  `venta.page.ts` → `centavos()` **no cambia**.
- Pruebas:
  - `precio.spec.ts`: `sin descuento muestra solo el precio`; `RF-11: con
    descuento tacha el normal, muestra el nuevo y «−15 %»`; `RF-11: lo dice
    entero a los lectores de pantalla`.
  - `tarjeta-producto.spec.ts`: `RF-11: muestra el descuento que recibe`.
  - `detalle.page.spec.ts`: `RF-11: el detalle con descuento`.
  - `carrito.page.spec.ts`: `RF-11: la línea con descuento; el total es el del
    servidor`.
  - `venta.page.spec.ts`: `RF-11: la línea con descuento`; `RF-15: el total con
    el precio con descuento coincide con el del servidor (4,50 al −15 % × 0,69
    = 2,64)`; `RF-16: ante TOTAL_CAMBIADO trae otra vez el precio con
    descuento`.
  - `busqueda-de-productos.spec.ts` (o la de `venta.page`): `RF-11: el
    resultado con descuento`.

### Tarea 8. JSON-LD (RF-14)

- `detalle.page.ts` → `datosDelProducto`: `price` ya es el cobrado; con
  `finDescuento`, `priceValidUntil` con esa fecha (`AAAA-MM-DD`).
- Pruebas, `detalle.page.spec.ts`: `RF-14: con descuento, el precio con
  descuento y priceValidUntil`; `RF-14: sin descuento, sin priceValidUntil`.

### Tarea 9. La pantalla de descuentos (RF-1 a RF-7)

- `core/modelos/descuento.ts`: `Descuento`, `EstadoDescuento`,
  `NOMBRES_DE_ESTADO_DESCUENTO`, `SolicitudDeDescuento`,
  `DescuentoGuardado`.
- `core/api/descuentos.service.ts`: `listar(pagina: Signal<number>)` con
  `httpResource`; `crear`, `cambiar`, `borrar` y `terminar` con `HttpClient`
  (`escribir`).
- `core/api/mensajes-error.ts`: `DESCUENTO_SOLAPADO` («Se cruza con el
  descuento del N % del dd/mm al dd/mm»), `DESCUENTO_NO_EDITABLE`,
  `DESCUENTO_NO_ENCONTRADO`.
- `core/sesion/modulos.ts`: `descuentos: ['ADMINISTRADOR']`. Ruta
  `descuentos` en `app.routes.ts` con `rolGuard`, `title: Descuentos · Tienda
  Montero`. Acceso en `features/panel/panel.page.html`, como «Categorías».
- `features/descuentos/descuentos.page.*`: la lista (producto o categoría,
  porcentaje, fechas, chip del estado; «Editar» y «Borrar» en los programados,
  «Terminar» con confirmación en los vigentes) y el formulario con Signal
  Forms: producto (búsqueda en `GET /api/gestion/productos?texto=`) o
  categoría (`GET /api/gestion/categorias`), porcentaje y las dos fechas. Al
  guardar, el aviso de RF-6 si `bajoElCosto` no está vacío.
- Pruebas:
  - `descuentos.service.spec.ts`: `pide la página`, `crea, cambia, borra y
    termina con la dirección y el cuerpo correctos`.
  - `descuentos.page.spec.ts`: `RF-3: lista con el estado de cada uno`;
    `RF-1: valida el porcentaje y las fechas antes de enviar`; `RF-2: muestra
    con cuál se cruza`; `RF-4: Editar y Borrar solo en los programados`;
    `RF-5: Terminar solo en los vigentes y pide confirmación`; `RF-6: avisa los
    productos bajo el costo`; `un error de red muestra «Reintentar»`.
  - `app.routes.spec.ts` y `panel.page.spec.ts`: `RF-7: la ruta y el acceso
    son solo del Administrador`.

### Tarea 10 (recortable). Filtro y orden por el precio con descuento (RF-13)

- `ProductoRepository`: el filtro y el orden usan la misma fórmula que
  `PrecioDeVenta`. **Delicado (dinero en una consulta), completo:**

  ```java
  // El precio con descuento de HU-24 RF-8 y RF-9 en la consulta: el mayor vigente del producto o de su categoría.
  // Repite PrecioDeVenta.de; CatalogoIntegracionTest compara los dos
  String PRECIO_DE_VENTA = """
  		case when round(p.precio * (100 - coalesce((select max(d.porcentaje) from Descuento d
  				where (d.producto = p or d.categoria = p.categoria) and d.inicio <= :hoy and d.fin >= :hoy), 0)) / 100, 2)
  			< 0.01 then 0.01
  		else round(p.precio * (100 - coalesce((select max(d.porcentaje) from Descuento d
  				where (d.producto = p or d.categoria = p.categoria) and d.inicio <= :hoy and d.fin >= :hoy), 0)) / 100, 2)
  		end""";
  ```
  En `CATALOGO`, `p.precio >= :precioMin` y `<= :precioMax` pasan a
  `PRECIO_DE_VENTA`. El orden: `order by case when p.stock > 0 then 0 else 1
  end, case when :porPrecio = 1 then <PRECIO_DE_VENTA> end asc, case when
  :porPrecio = -1 then <PRECIO_DE_VENTA> end desc`, y después el `Sort` de la
  página (Spring Data lo agrega al final). `catalogo(…)` suma `LocalDate hoy`
  e `int porPrecio`, también en su otra llamada,
  `VentaTiendaService.buscarProductos` (línea 120: `hoy` y `0`, ordena por
  nombre); en `OrdenCatalogo`, `PRECIO_ASC` y `PRECIO_DESC` quedan
  solo con `idProducto desc` para desempatar, y un método `porPrecio()` da
  1, −1 o 0.
- Pruebas, `CatalogoIntegracionTest` (ampliada):
  - `filtroHastaNoventaIncluyeElDeCienConQuince` (RF-13)
  - `ordenPorPrecioUsaElPrecioConDescuento` (RF-13, los dos sentidos)
  - `laConsultaRedondeaIgualQuePrecioDeVenta` (RF-8, RF-13: 4,50 al −15 %
    entra con «desde 3,83» y no con «desde 3,84»; 0,01 al −90 % entra con
    «hasta 0,01»)
  - `agotadosSiguenAlFinal` y `paginasYConteoSiguenIgual` (regresión de
    HU-09: el conteo usa `:hoy` y no `:porPrecio`)
- **H2 y MySQL:** el CI corre sobre H2. En la prueba manual, con Docker
  (MySQL 8.4), se repiten el filtro «desde 3,83» y el orden.

## PR 3 — Ticket y reportes

### Tarea 11. El ticket (RF-18)

- `core/modelos/ticket.ts`: `lineasDelTicket` agrega `(−15 %)` al `detalle`
  de cada línea con `descuento`; `ahorroDelTicket(lineas): string | null`
  suma `descontado` en centavos enteros (`Math.round(x * 100)`) y da
  `precio(…)`, o `null` sin descuentos.
- `shared/ticket/ticket.ts` y `.html`: `DatosDelTicket.ahorro: string | null`,
  una fila «Usted ahorró» bajo el total. `features/comprobante` y
  `features/comprobante-cliente` la llenan con `ahorroDelTicket`.
- Pruebas:
  - `ticket.spec.ts`: `RF-18: con ahorro muestra «Usted ahorró»; sin ahorro no`.
  - `core/modelos/ticket.spec.ts`: `RF-18: la línea con descuento
    lleva «(−15 %)»`; `RF-18: el ahorro suma en centavos sin error de coma
    flotante`; `RF-18: las líneas viejas sin descuento no cambian`.
  - `comprobante.page.spec.ts` y `comprobante-cliente.page.spec.ts`: `RF-18:
    el ticket con descuento`.

### Tarea 12. Reportes (RF-19, RF-20)

- `dto/reportes/DescuentosFila(long pedidos, BigDecimal vendido, BigDecimal
  descontado)` y en `ReportesRepository`:

  ```java
  // HU-24 RF-19: las líneas con descuento de lo vendido en el período, con los mismos filtros que lo demás
  @Query("""
  		select new bo.edu.uagrm.tienda.dto.reportes.DescuentosFila(count(distinct p.idPedido),
  			coalesce(sum(l.subtotal), 0), coalesce(sum(l.descontado), 0))
  		from LineaPedido l join l.pedido p left join p.usuario u
  		where l.descuento is not null and p.estado in :estados and p.pagado >= :inicio and p.pagado < :fin
  			and (:tipo is null or coalesce(u.tipoCliente, bo.edu.uagrm.tienda.entity.TipoCliente.PUBLICO_GENERAL) = :tipo)
  			and (:canal is null or p.canal = :canal)""")
  DescuentosFila descuentos(…mismos parámetros que lineasPorProducto…);
  ```
- `VentasResponse.Totales` suma `descontado`, `vendidoConDescuento` y
  `pedidosConDescuento`; `ReportesService.totales` (línea 253) los llena,
  así salen en el período y en el anterior (RF-19). Con `VENDIDOS` quedan
  fuera «Anulado» y «Pago a devolver».
- `SeccionesDelReporte` → «Cifras del período» (línea 179): las tres cifras
  con su comparación (RF-20).
- Frontend: `core/modelos/reportes.ts` (`Totales`) y
  `features/reportes/reportes-resumen.ts` y `.html`: las tres cifras con su
  comparación, como las demás.
- Pruebas, `ReportesIntegracionTest` (ampliada):
  - `resumenSumaLoDescontadoYLoVendidoConDescuento` (RF-19, el escenario del
    Contador: 220 / 30 / 170 / 1)
  - `comparaConElPeriodoAnterior` (RF-19)
  - `respetaLosFiltrosDeTipoYCanal` (RF-19)
  - `anuladaYPagoADevolverNoSuman` (RF-19, caso límite)
  - `lineasViejasSinDescuentoNoSuman` (caso límite)
- Pruebas, `ReportesExportarIntegracionTest` (ampliada):
  - `excelYPdfLlevanLasCifrasDeDescuentos` (RF-20)
- Pruebas, `reportes-resumen.spec.ts`: `RF-19: muestra lo descontado, lo
  vendido con descuento y los pedidos con descuento`.

## Casos borde (a qué prueba van)

| Caso | Prueba |
|---|---|
| Redondeo y piso de Bs 0,01 | `PrecioDeVentaTest`, `laConsultaRedondeaIgualQuePrecioDeVenta` |
| Primer y último día, día siguiente | `DescuentoTest.estadoSegunHoy`, `alDiaSiguienteDelFinVuelveElPrecioNormal` |
| Pendiente que vence o se cancela | `vencidoVuelveAlCarritoConElPrecioDelDia` |
| Pago tardío | `pagoTardioDespuesDelFinDelDescuentoQuedaConSuTotal` |
| QR del mostrador pendiente | `ventaConQrGeneraElCobroConElTotalConDescuento` |
| Anulación | `anularUnaVentaConDescuentoDevuelveElStock`, `anuladaYPagoADevolverNoSuman` |
| Precio editado durante el descuento | `terminarEditarElDescuentoOCambiarElPrecioNoTocaElPedido` y `PreciosDeVentaTest` (el cobrado sale del precio actual) |
| Categoría oculta o producto inactivo | `categoriaOcultaConDescuentoNoSeLista` |
| Cambió de categoría | `usaLaCategoriaActual` |
| Dos Administradores a la vez | `dosDelMismoProductoGanaElMayor` |
| Datos basura | `DescuentosIntegracionTest` (porcentaje, fechas, ids) |
| Líneas viejas | `lineasViejasSinDescuentoNoSuman`, ticket sin cambios |
| Mostrador y `TOTAL_CAMBIADO` | `venta.page.spec.ts` (RF-16), `totalDeLaPantallaSinDescuentoDaTotalCambiado` |

## Orden y pruebas a correr

1. PR 1: tareas 1 → 2 → 3 → 4 → 5 → 6. Tocan entidades y DTO compartidos:
   al terminar la 4 y la 5, `./mvnw test` completo (`docs/flujo-sdd.md`).
2. PR 2: tareas 7 → 8 → 9 → 10. La 7 toca `shared/` y `core/`: suite completa
   del frontend al terminarla. La 10 es backend: `./mvnw test` completo.
3. PR 3: tareas 11 → 12, con las dos suites al final.

Backend y frontend nunca a la vez.

## Cierre

`/sdd:cerrar HU-24` en cada PR (los RF de ese PR; el «Resultado» de abajo se
completa por partes), y en el PR 3 `docs/lista-de-cambios.md` → punto 3 pasa
a «hecho». Con las revisiones de
`docs/flujo-sdd.md` → «Revisión antes del PR». `/security-review` corresponde
en los PR 1 y 3 (dinero) y en el 2 por la tarea 10 (consulta con parámetros)
y la pantalla nueva de gestión. Lo propio de esta historia en la revisión de
interfaz: la tarjeta con descuento en celular (precio tachado, nuevo y marca
en un ancho angosto), un nombre largo con descuento en el carrito y en el
mostrador (`break-ui`), y la vista de impresión del ticket con «Usted
ahorró». En la prueba manual, con Docker y MySQL: el filtro y el orden por
precio (tarea 10) y un pedido con descuento pagado con el simulador.

## Resultado

### PR 1 — El cálculo, la gestión y lo cobrado (backend)

*18/11/2026.* Implementado con TDD por Claude (Opus); la historia, el plan y el
análisis, de Fable.

**Cobertura RF → pruebas (backend):**

| RF | Pruebas |
|---|---|
| RF-1 | `DescuentosIntegracionTest`: crea de producto y de categoría; porcentaje fuera de rango, decimal o texto; fechas inválidas (ayer, fin antes del inicio, más de 366 días, texto, año imposible); ni producto ni categoría o los dos; producto o categoría que no existe |
| RF-2 | `rechazaElQueSeCruzaYDiceConCual`, `productoYCategoriaNoSeCruzan`; `DescuentoRepositoryTest` (`cruzados…`) |
| RF-3 | `listaVigentesProgramadosYVencidosDeA20`; `DescuentoTest.estadoSegunHoy`; `DescuentoRepositoryTest.listar…` |
| RF-4 | `programadoSeEditaYSeBorra`, `editarNoCambiaElProductoNiLaCategoria`, `cruzadosSinSiMismoAlEditar` |
| RF-5 | `vigenteSeTerminaYDejaDeAplicarse`, `vigenteQueEmpezoHoySeBorraAlTerminar`, `vigenteNoSeEditaNiSeBorraYVencidoNoSeToca`, `DescuentoTest.terminarDejaElFinEnAyer` |
| RF-6 | `avisaLosProductosQueQuedanBajoElCosto`, `unProductoInactivoNoAvisaBajoElCosto` |
| RF-7 | `empleadoContadorYClienteReciben403YSinSesion401` |
| RF-8 | `PrecioDeVentaTest` (3,825 sube, piso de 0,01, sin rebaja real no hay descuento), `PedidoTest.subtotalConDescuentoYCantidadDecimal` |
| RF-9 | `PreciosDeVentaTest` (el mayor; dos del mismo producto; la categoría actual), `elDeLaCategoriaYElDelProductoGanaElMayor` |
| RF-10 | `vigentesIncluyeElPrimerYElUltimoDia`, `alDiaSiguienteDelFinVuelveElPrecioNormal`, `descuentoQueTerminaEntreVerYConfirmarDaTotalCambiado` |
| RF-12 | `CatalogoIntegracionTest` (tarjeta, detalle, portada, sin descuento, sin costo ni quién lo creó), `lineaYTotalConElPrecioConDescuento`, `laBusquedaDaElPrecioConDescuento` |
| RF-14 | `elDetalleTraeElFinDelDescuento` (el dato; el JSON-LD va en el PR 2) |
| RF-15 | `PedidoTest` (la línea guarda cobrado, normal, porcentaje y ahorro; sin ahorro por redondeo no cuenta), `confirmaConElPrecioConDescuentoYElCobroPideEseTotal`, `ventaEnEfectivoCobraElPrecioConDescuento`, `ventaConQrGeneraElCobroConElTotalConDescuento` |
| RF-16 | `descuentoQueEmpiezaEntreVerYConfirmarDaTotalCambiado`, `descuentoQueTerminaEntreVerYConfirmarDaTotalCambiado`, `totalDeLaPantallaSinDescuentoDaTotalCambiado` |
| RF-17 | `terminarEditarElDescuentoOCambiarElPrecioNoTocaElPedido`, `vencidoVuelveAlCarritoConElPrecioDelDia`, `pagoTardioDespuesDelFinDelDescuentoQuedaConSuTotal`, `anularUnaVentaConDescuentoDevuelveElStock` |

RF-11, RF-13, RF-18 a RF-20: PR 2 y PR 3.

**Suites:** `./mvnw test`, **906 pruebas**, 0 fallas. Sin cambios en el
frontend en este PR (los campos nuevos de la API se suman sin romper las
pantallas; `precio` sigue siendo el que la pantalla del mostrador usa para
su total, ahora con el descuento).

**Revisiones:**

- **`/security-review` (Fable):** sin hallazgos. Revisado: solo el
  Administrador llega a `/api/gestion/descuentos`; el monto cobrado sale
  siempre del servidor (`totalEsperado` solo se compara); el porcentaje no
  sale de 1 a 90 ni al editar y los descuentos no se suman; sin asignación
  masiva (`creadoPor` sale de la sesión); consultas con parámetros; los DTO
  públicos no llevan el costo ni quién creó el descuento.
- **`/code-review`, primera ronda (10 puntos):** corregidos borrar una
  categoría sin productos con descuentos (500 por la clave foránea: se
  borran con ella), la rebaja que el redondeo o el piso dejan en cero (no
  cuenta como descuento), el aviso bajo el costo de un producto inactivo y
  sin el total, el año imposible (500), la edición con su propia solicitud
  (`DescuentoCambioRequest`), `para(Producto)` sin uso y el precio tachado
  repetido en cuatro lugares (`PrecioDeVenta.normalTachado()`).
  **Descartados:** el filtro y el orden por precio con el normal (RF-13, ya
  previsto en el PR 2: entre el merge del PR 1 y el del PR 2, filtran y
  ordenan por el precio normal); la prueba del comprobante del cliente con
  descuento (usa el mismo `LineaPedidoResponse` que prueban el pedido y la
  venta).
- **`/code-review`, segunda ronda (7 puntos):** corregidos el descuento de
  una categoría que se borra a la vez (ahora toma su bloqueo, como
  `CategoriaService`), la línea con ahorro de Bs 0,00 por la cantidad, la
  regla de `PrecioDeVenta` en su constructor, un comentario viejo, un filtro
  repetido y la consulta de descuentos con una lista vacía. **Pendiente:** la
  validación del porcentaje y las fechas repetida en `DescuentoRequest` y
  `DescuentoCambioRequest`.

**Distinto de lo planeado:**

- `noventaSobreElMaximoDaDiez`: 99 999,99 al −90 % da Bs 10 000,00 (el plan
  decía 10,00 por error).
- `PrecioDeVenta.de` sin rebaja real devuelve sin descuento, y la línea sin
  ahorro tampoco guarda el porcentaje (de `/code-review`).
- Terminar responde 204; `DescuentoGuardadoResponse` suma `totalBajoElCosto`.
- Sin `ComprobanteIntegracionTest.lineasConDescuento…` (ver descartados).

**Prueba manual:** con el PR 2 (este PR no tiene pantallas; la API se probó
con las pruebas de integración).

### PR 2 — La tienda y el mostrador (frontend)

*19/11/2026.* Tareas 7 a 9 con TDD por Claude (Opus); las revisiones, de
Fable. La tarea 10 (RF-13, filtrar y ordenar por el precio con descuento)
queda **recortada** por ahora: Fabio la pospuso hasta terminar lo demás
(19/11/2026). Hasta entonces, el catálogo filtra y ordena por el precio
normal.

**Cobertura RF → pruebas (frontend):**

| RF | Pruebas |
|---|---|
| RF-1, RF-2 | `descuentos.page.spec.ts`: crea de producto y de categoría, pide el producto (y el error se va al escribir), porcentaje y fechas inválidos con foco en el primer error, muestra con cuál se cruza; `mensajes-error.spec.ts` (el cruce con sus fechas) |
| RF-3 | la lista con sus estados; una página que quedó vacía vuelve a la anterior |
| RF-4 | Editar y Borrar solo en los programados, borrar pide confirmación, cancelar la edición devuelve el foco a «Editar», «de la categoría» en minúscula |
| RF-5 | Terminar solo en los vigentes y pide confirmación |
| RF-6 | avisa los productos bajo el costo, con «Y N más» |
| RF-7 | `app.routes.spec.ts` y `panel.page.spec.ts`: solo el Administrador |
| RF-11 | `precio.spec.ts` (cuatro), la tarjeta, el detalle, el carrito, «Nueva venta» y `catalogo.service.spec.ts` |
| RF-14 | `detalle.page.spec.ts`: `priceValidUntil` con descuento y sin él |
| RF-15, RF-16 | `venta.page.spec.ts`: el total con descuento y el aviso «El total cambió», que trae el precio y el descuento nuevos (`ventas.service.spec.ts`, `precioDeHoy`) |

**Suites:** frontend **1014 pruebas**, `ng lint` y `ng build` sin avisos
(después de mezclar `main` con la búsqueda por nombre, #122). El backend no
cambia en este PR: 926 pruebas con la mezcla.

**Revisiones:**

- **Interfaz (subagente Fable, `playwright-cli` en Edge, escritorio oscuro y
  iPhone 15 claro, peores datos):** corregidos los nombres largos que se
  salían del aviso «bajo el costo» (la página pasaba a 509 px en el
  celular), el anillo de foco cortado en los resultados de la búsqueda, el
  foco que quedaba en `<body>` al cancelar la edición, el precio con
  descuento que aplastaba el nombre en la búsqueda de «Nueva venta» (la
  columna pasa de `max-content` a `auto`) y el tachado de 9,8 px y la marca
  de tamaño fijo (ahora `max(0.75em, 12px)` y `max(0.55em, 13px)`;
  `DESIGN.md` al día). También dos de los menores: el error del producto se
  va al escribir y, dentro de una frase, «de la categoría «X»».
  **Quedan como estaban:** la unidad fuera del texto oculto del precio en la
  búsqueda del mostrador; el aviso «bajo el costo» con la caja de error
  (una caja de aviso sería una decisión nueva de `DESIGN.md`); la búsqueda
  de producto en `/descuentos` muestra la primera página sin decir que hay
  más; el «Borrar» amarillo del diálogo compartido (es de antes).
  Se limpió lo creado para probar.
- **`/code-review` (subagente Fable):** corregido el importante: después de
  borrar o terminar el último descuento de una página que no es la primera,
  la lista quedaba vacía y sin navegación (ahora vuelve a la última página
  que hay, con su prueba). También el menor: «Recargue la página.» sobraba
  en `DESCUENTO_NO_EDITABLE` y `DESCUENTO_NO_ENCONTRADO`, porque la lista ya
  se recarga sola.
- **`/security-review`:** no corresponde. El PR 2 es solo frontend; el
  acceso lo valida el backend del PR 1, que ya la tuvo.

**Prueba manual:** pendiente. Fabio la hace al final, junto con las demás.
La tienda ya corre con esta rama en Docker (`http://localhost`).
