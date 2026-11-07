# HU-23 — Venta en el mostrador

Historia: `docs/historias/HU-23.md` (RF-1 a RF-24). Decisión:
`decisiones.md` → «Venta en tienda»; `CLAUDE.md` → «Pago» (la excepción del
efectivo) y «Usuarios y roles». Issue #104. Corregido el 05/11/2026 tras
`/sdd:analizar`: la excepción al orden de bloqueo en `decisiones.md`, «pedidos
perdidos» solo en línea, sin «Reintentar QR», código del QR, de cancelar y de
vencer, y las pruebas que faltaban.

## Decisiones de diseño

- **Un `Pedido` con `canal = TIENDA`,** no una entidad aparte: `/pedidos`, los
  reportes, el cobro (`Cobro.pedido`), la conciliación y el vencimiento ya
  trabajan sobre `Pedido`. **Motivo:** una entidad aparte obligaba a unir dos
  tablas en cada reporte y a duplicar el cobro.
- **`Pedido.usuario` pasa a opcional** (el comprador; `null` = consumidor
  final) y se suma **`vendidoPor`** (el Empleado o el Administrador, siempre
  en `TIENDA`). En `TIENDA`, `nombreRetira` y `telefonoRetira` quedan vacíos.
- **Orden de bloqueo de una venta del mostrador:** el pedido, después su cobro
  y después los productos por id ascendente. **Nunca bloquea al cliente:**
  una venta del mostrador no toca el carrito ni el pedido en línea del
  cliente, y lo de en línea solo bloquea pedidos `EN_LINEA` de su cliente.
  Así ningún camino espera en círculo. Crear la venta inserta el pedido nuevo,
  que nadie más ve, y bloquea solo los productos. Es la excepción escrita en
  `decisiones.md` → «Pedidos» y «Venta en tienda».
- **Movimientos de stock sin tipo nuevo:** el efectivo y el QR usan
  `RESERVA`, y cancelar, vencer y anular usan `LIBERACION`, los dos con el
  pedido. La pantalla los nombra por el canal y el estado: «Venta en tienda»,
  «Anulación de venta». **Motivo:** `movimiento_stock.tipo` es un enum de
  MySQL y un valor nuevo pediría cambiar la columna al arrancar. El usuario
  del movimiento es quien lo hizo: `vendidoPor` en el mostrador y el
  Administrador al anular.
- **Estado nuevo `ANULADO`** (chip «Anulado»; en el texto, «venta anulada»). En una base creada después de HU-17, Hibernate
  dejó un `check` con los estados de entonces: `PedidoAlArrancar` (antes
  `EstadoPedidoAlArrancar`) lo quita.
- **En «Mis pedidos»,** una venta del mostrador aparece **recién pagada**
  (`ENTREGADO`, `PAGADO`, `PAGO_A_DEVOLVER` o `ANULADO`), nunca pendiente,
  cancelada ni vencida (RF-17; `decisiones.md` → «Venta en tienda»).
- **«Pedidos perdidos» solo en línea** (RF-21): la consulta de perdidos
  filtra `canal = EN_LINEA` aunque el filtro de canal sea «todos» o «en
  tienda»; con «en tienda», la sección muestra cero.
- **El precio y el stock los decide el servidor;** la pantalla solo los
  muestra, y el total que vio se compara (RF-14), como en el pedido.

## Tareas

1. **Modelo y arranque** (`entity/`, `service/PedidoAlArrancar.java`).
   - `Canal { EN_LINEA, TIENDA }` y `MedioPago { EFECTIVO, QR }`;
     `EstadoPedido` suma `ANULADO`.
   - `Pedido`: `canal` (varchar, como `estado`), `medioPago`, `recibido`
     (`precision 12, scale 2`), `vendidoPor`, `anulado`, `anuladoPor`,
     `motivoAnulacion` (200). `usuario`, `nombreRetira` y `telefonoRetira`
     pasan a `nullable = true`.
     - `static Pedido enTienda(Usuario comprador, Usuario vendedor, MedioPago medio, BigDecimal recibido, LocalDateTime ahora, Duration vigencia)`.
     - `void cobrarEnTienda(LocalDateTime fecha)`: de `PENDIENTE_PAGO` a
       `ENTREGADO`, con `pagado = entregado = fecha` y
       `entregadoPor = vendidoPor`.
     - `void anular(Usuario admin, String motivo, LocalDateTime fecha)`:
       solo desde `ENTREGADO`, en `TIENDA` y en `EFECTIVO`.
     - `BigDecimal cambio()`: `recibido − total`.
     - `Usuario responsable()`: `vendidoPor` en `TIENDA`, `usuario` en
       `EN_LINEA`.
   - `MovimientoStock.reserva` y `liberacion` usan `pedido.responsable()`; una
     sobrecarga `liberacion(…, Usuario quien)` es para la anulación.
   - **Delicado, `PedidoAlArrancar`** (se ejecuta antes de que arranquen el servidor
     web y las tareas, como el de ahora; si falla, la API no arranca):

     ```java
     public void ajustar() {
     	// 1. Lo de siempre: el enum viejo de antes de HU-17 pasa a varchar
     	if (tipoDeColumna("estado").equalsIgnoreCase("enum")) {
     		jdbc.execute("alter table pedido modify estado varchar(15) not null");
     	}
     	// 2. El check que Hibernate creó con los estados de entonces no admite ANULADO (HU-23)
     	for (String check : jdbc.queryForList("""
     			select tc.constraint_name from information_schema.table_constraints tc
     			join information_schema.check_constraints cc
     			  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
     			where tc.table_schema = database() and tc.table_name = 'pedido'
     			  and tc.constraint_type = 'CHECK' and cc.check_clause like '%PENDIENTE_PAGO%'
     			  and cc.check_clause not like '%ANULADO%'""", String.class)) {
     		jdbc.execute("alter table pedido drop check `" + check + "`");
     	}
     	// 3. Una venta del mostrador puede no tener comprador ni datos de retiro (HU-23)
     	for (String columna : List.of("id_usuario", "nombre_retira", "telefono_retira")) {
     		if ("NO".equals(admiteNulos(columna))) {
     			jdbc.execute("alter table pedido modify " + columna + " " + tipoCompleto(columna) + " null");
     		}
     	}
     	// 4. Los pedidos de antes son todos en línea
     	jdbc.update("update pedido set canal = 'EN_LINEA' where canal is null");
     }
     ```

     `tipoDeColumna`, `admiteNulos` y `tipoCompleto` leen
     `information_schema.columns` (`data_type`, `is_nullable` y
     `column_type`). En H2 las consultas 1 a 3 no encuentran nada; la 4 corre
     igual. El nombre del check sale de `information_schema` y no del usuario.
   - Pruebas:
     - `PedidoTest`:
       - `cobrarEnTiendaDejaEntregadoConLasFechasYQuienVendio` (RF-6);
       - `soloSeAnulaUnaVentaEnEfectivoEntregada` (RF-20);
       - `elCambioEsLoRecibidoMenosElTotal` (RF-5).
     - `PedidoAlArrancarTest` (H2):
       - `losPedidosSinCanalQuedanEnLinea`;
       - `sinEnumNiCheckNoHaceNada`.

2. **Separar lo de en línea** (`PedidoRepository`, `PedidoService`,
   `PagoService`, `ConciliacionDePagos`, `VencimientoPedidos`, `misPedidos`).
   - Las consultas del cliente filtran `canal = EN_LINEA`: el pendiente al
     confirmar, `vencerSiCorresponde`, `pendientesBloqueados`,
     `idDelPendiente`, el pendiente de arriba de «Mis pedidos», y los
     `findConBloqueoByIdPedidoAndUsuario` de pagar, cancelar y simular. `ver`
     admite `TIENDA` (RF-17), y la lista de «Mis pedidos» suma las `TIENDA`
     ya pagadas (ver «Decisiones de diseño»).
   - **Delicado, `ConciliacionDePagos.aplicar`:** con `canal = TIENDA` bloquea
     el pedido y el cobro sin el cliente, y no manda correo; a tiempo,
     `cobrarEnTienda`; tarde, `revivir` sin `restarDelCarrito`.

     ```java
     Pedido pedido = datos.enTienda()
     		? pedidoRepository.findConBloqueoByIdPedido(datos.idPedido()).orElseThrow()
     		: bloquearClienteYPedido(datos);
     Cobro cobro = cobroRepository.findConBloqueoByIdCobro(datos.idCobro()).orElseThrow();
     …
     case PAGADO -> {
     	cobro.registrarPago(fecha);
     	if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO) {
     		if (datos.enTienda()) pedido.cobrarEnTienda(fecha); else pedido.pagar(fecha);
     	} else {
     		pedidoService.revivir(pedido, fecha, ahora); // en TIENDA, sin tocar ningún carrito
     	}
     	if (!datos.enTienda()) { /* el correo de HU-21, como ahora */ }
     }
     ```

     `CobroAConciliar` suma `canal`; su `idUsuario` puede ser `null`.
   - `VencimientoPedidos`: un `TIENDA` vence con `VentaTiendaService.vencer(idPedido)`.
     `PedidoAVencer` suma `canal`.
   - Pruebas, en las clases de integración que ya existen:
     - `PedidoIntegracionTest`:
       - `unaVentaDelMostradorPendienteNoBloqueaElPedidoEnLineaDelCliente` (casos límite);
       - `elClienteNoPagaNiCancelaUnaVentaDelMostrador` (RF-23).
     - `PagoIntegracionTest`:
       - `elPagoDeUnaVentaDelMostradorNoMandaCorreo` (decisiones de la historia);
       - `elPagoTardioDeUnaVentaDelMostradorNoTocaElCarrito` (RF-11).
     - `MisPedidosIntegracionTest`:
       - `muestraLaVentaDelMostradorPagadaYNoLaPendiente` (RF-17).
     - Las de HU-12, HU-13 y HU-14 siguen en verde sin cambios.

3. **API de la venta** (`service/VentaTiendaService.java`,
   `controller/VentaTiendaController.java`, `dto/Venta*`).
   - Endpoints, en `SecurityConfig`:
     - `GET` con Empleado, Administrador y Contador;
     - lo demás con Empleado y Administrador;
     - `…/anulacion`, solo el Administrador.

     | Método | Ruta | Qué hace |
     |---|---|---|
     | `GET` | `/api/ventas/productos?texto=` | Activos, con precio, unidad y disponible, 10 como mucho (RF-1, RF-3) |
     | `GET` | `/api/ventas/clientes?correo=` | La cuenta de Cliente activa con ese correo, o 404 (RF-4) |
     | `POST` | `/api/ventas` | `VentaRequest(List<LineaVentaRequest> lineas, Long idCliente, MedioPago medio, BigDecimal recibido, BigDecimal totalEsperado)` → `VentaResponse` |
     | `GET` | `/api/ventas/{id}` | El comprobante y el estado (RF-15) |
     | `POST` | `/api/ventas/{id}/consulta` | Consulta el cobro (RF-8) |
     | `POST` | `/api/ventas/{id}/cancelacion` | RF-9 |
     | `POST` | `/api/ventas/{id}/anulacion` | `AnulacionRequest(String motivo)` (RF-18, RF-19) |
     | `POST` | `/api/ventas/{id}/simulacion` | Solo con el simulador, como `SimulacionDePagoController` |

   - Validación (RF-13, 400 `DATOS_INVALIDOS`, con `@Valid` y, lo que depende de los datos, `CamposInvalidosException`; `campoInvalido(campo, mensaje)` la arma con un solo campo): de 1 a 50 líneas, sin
     productos repetidos, cantidad > 0, ≤ 999 y con los decimales de su
     unidad; con efectivo, `recibido` ≤ 999 999,99 y con 2 decimales; con QR,
     sin `recibido`.
   - `StockInsuficienteException` suma un constructor para la venta («Algunos
     productos de la venta no se pueden registrar.»), y `LineaRechazada` suma
     `disponible` (solo en la venta; en línea va `null`, que no se serializa)
     (RF-12).
   - **Delicado, `registrar`**: todo o nada, con los productos bloqueados
     antes de leerlos.

     ```java
     public static final Duration VIGENCIA = Duration.ofMinutes(10);

     @Transactional(isolation = Isolation.READ_COMMITTED)
     Pedido registrar(VentaRequest venta, Long idVendedor) {
     	LocalDateTime ahora = LocalDateTime.now(clock);
     	Usuario vendedor = usuarioRepository.getReferenceById(idVendedor);
     	Usuario comprador = venta.idCliente() == null ? null
     			: usuarioRepository.findById(venta.idCliente())
     					.filter(u -> u.getRol() == Rol.CLIENTE && u.getEstado() == EstadoCuenta.ACTIVA)
     					.orElseThrow(() -> campoInvalido("idCliente", "no es una cuenta de Cliente activa"));
     	// Por id ascendente, como al confirmar un pedido: dos ventas, un pedido y un ajuste no se esperan en círculo
     	Map<Long, Producto> productos = new TreeMap<>();
     	venta.lineas().stream().map(LineaVentaRequest::idProducto).sorted()
     			.forEach(id -> productos.put(id, productoRepository.findConBloqueoByIdProducto(id)
     					.orElseThrow(() -> campoInvalido("lineas", "un producto no existe"))));
     	List<LineaRechazada> rechazadas = venta.lineas().stream()
     			.map(linea -> rechazoEnTienda(productos.get(linea.idProducto()), linea.cantidad()))
     			.flatMap(Optional::stream).toList();
     	if (!rechazadas.isEmpty()) {
     		throw StockInsuficienteException.enLaVenta(rechazadas); // RF-12
     	}
     	Pedido pedido = Pedido.enTienda(comprador, vendedor, venta.medio(), venta.recibido(), ahora, VIGENCIA);
     	venta.lineas().forEach(linea -> pedido.agregarLinea(productos.get(linea.idProducto()), linea.cantidad()));
     	if (pedido.getTotal().compareTo(venta.totalEsperado()) != 0) {
     		throw new TotalCambiadoException(pedido.getTotal()); // RF-14
     	}
     	if (venta.medio() == MedioPago.EFECTIVO && venta.recibido().compareTo(pedido.getTotal()) < 0) {
     		throw campoInvalido("recibido", "no puede ser menor que el total"); // RF-13
     	}
     	pedidoRepository.save(pedido);
     	for (LineaPedido linea : pedido.lineasPorProducto()) {
     		Producto producto = productos.get(linea.getIdProducto());
     		BigDecimal nuevo = producto.getStock().subtract(linea.getCantidad()); // revisado con el producto bloqueado
     		producto.aplicarMovimiento(nuevo);
     		movimientoRepository.save(MovimientoStock.reserva(producto, linea.getCantidad(), nuevo, pedido, ahora));
     	}
     	if (venta.medio() == MedioPago.EFECTIVO) {
     		pedido.cobrarEnTienda(ahora); // RF-6: lo confirma quien recibió el dinero (CLAUDE.md → Pago)
     	}
     	return pedido;
     }
     ```

     `rechazoEnTienda`: `CarritoService.estado` (agotado, no disponible,
     cantidad inválida) y, si pide más que el stock, `NO_ALCANZA` con
     `disponible`.
   - **Delicado, el QR** (como `PagoService.pagar`: ninguna llamada al
     sistema de pago con filas bloqueadas ni con una transacción abierta).
     `transaccion` es un `TransactionTemplate` con `READ_COMMITTED`, como en
     `PagoService`.

     ```java
     public VentaResponse crear(VentaRequest venta, Long idVendedor) {
     	Pedido registrada = transaccion.execute(estado -> registrar(venta, idVendedor));
     	Long idPedido = registrada.getIdPedido();
     	if (venta.medio() == MedioPago.EFECTIVO) {
     		return ver(idPedido);
     	}
     	CobroGenerado generado;
     	try {
     		generado = iPago.generarCobro(idPedido, registrada.getTotal(), "Venta N.º " + idPedido + " — " + marca);
     	} catch (SistemaDePagoNoDisponibleException error) {
     		log.warn("No se pudo generar el cobro de la venta {}", idPedido, error);
     		// Sin «Reintentar QR» (decisiones.md → Venta en tienda): la venta se cancela y el stock vuelve
     		transaccion.executeWithoutResult(estado -> cerrar(idPedido, EstadoPedido.CANCELADO));
     		throw new CobroNoDisponibleException(); // 503; las líneas siguen en la pantalla
     	}
     	return transaccion.execute(estado -> {
     		Pedido pedido = pedidoRepository.findConBloqueoByIdPedido(idPedido).orElseThrow();
     		LocalDateTime ahora = LocalDateTime.now(clock);
     		// Mientras se generaba, otro pudo cancelarla o pasó la hora: el cobro no se guarda y nadie ve ese QR
     		if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO && pedido.getVence().isAfter(ahora)) {
     			cobroRepository.save(Cobro.generar(pedido, generado.referencia(), generado.codigoQr(),
     					generado.urlPasarela(), ahora));
     		}
     		return VentaResponse.from(pedido, cobroPendiente(pedido), ahora);
     	});
     }
     ```

   - **Delicado, cancelar y vencer:** el pedido y después los productos por id
     ascendente; ningún carrito. `puedeVencer` es el de `PedidoService`, que
     pasa a paquete.

     ```java
     @Transactional(isolation = Isolation.READ_COMMITTED)
     public VentaResponse cancelar(Long idPedido) {
     	Pedido pedido = bloquearVenta(idPedido); // 404 VENTA_NO_ENCONTRADA si no existe o es en línea
     	if (pedido.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
     		throw new PedidoNoPendienteException(); // RF-9: ya pagada, cancelada o vencida
     	}
     	cerrar(pedido, EstadoPedido.CANCELADO);
     	return ver(pedido);
     }

     // Para VencimientoPedidos, después de consultar el cobro sin nada bloqueado (RF-10)
     @Transactional(isolation = Isolation.READ_COMMITTED)
     public void vencer(Long idPedido) {
     	Pedido pedido = bloquearVenta(idPedido);
     	if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO
     			&& pedidoService.puedeVencer(pedido, LocalDateTime.now(clock))) {
     		cerrar(pedido, EstadoPedido.VENCIDO);
     	}
     }

     // Con el pedido ya bloqueado. Lo devuelto no pasa del máximo, como PedidoService.liberar
     private void cerrar(Pedido pedido, EstadoPedido estadoFinal) {
     	LocalDateTime ahora = LocalDateTime.now(clock);
     	for (LineaPedido linea : pedido.lineasPorProducto()) {
     		Producto producto = productoRepository.findConBloqueoByIdProducto(linea.getIdProducto()).orElseThrow();
     		BigDecimal tope = InventarioService.STOCK_MAXIMO.setScale(producto.getUnidad().decimales(), RoundingMode.FLOOR);
     		BigDecimal nuevo = producto.getStock().add(linea.getCantidad()).min(tope);
     		BigDecimal devuelto = nuevo.subtract(producto.getStock());
     		if (devuelto.signum() > 0) {
     			producto.aplicarMovimiento(nuevo);
     			movimientoRepository.save(MovimientoStock.liberacion(producto, devuelto, nuevo, pedido, ahora));
     		}
     	}
     	if (estadoFinal == EstadoPedido.CANCELADO) {
     		pedido.cancelar(ahora);
     	} else {
     		pedido.vencer(ahora);
     	}
     }
     ```

     `cerrar(Long idPedido, …)` bloquea la venta y llama a este. `anular`
     reutiliza el mismo bucle, con `liberacion(…, admin)`.
   - `VentaNoEncontradaException`: 404 `VENTA_NO_ENCONTRADA`, también para el
     id de un pedido en línea.
   - **Delicado, `anular`:**

     ```java
     @Transactional(isolation = Isolation.READ_COMMITTED)
     public VentaResponse anular(Long idPedido, Long idAdmin, String motivo) {
     	Pedido pedido = pedidoRepository.findConBloqueoByIdPedido(idPedido).orElseThrow(VentaNoEncontradaException::new);
     	LocalDateTime ahora = LocalDateTime.now(clock);
     	if (pedido.getCanal() != Canal.TIENDA || pedido.getMedioPago() != MedioPago.EFECTIVO
     			|| pedido.getEstado() != EstadoPedido.ENTREGADO
     			|| !pedido.getPagado().toLocalDate().equals(ahora.toLocalDate())) { // el reloj es el de La Paz
     		throw new VentaNoAnulableException(); // RF-20, 409
     	}
     	Usuario admin = usuarioRepository.getReferenceById(idAdmin);
     	for (LineaPedido linea : pedido.lineasPorProducto()) {
     		Producto producto = productoRepository.findConBloqueoByIdProducto(linea.getIdProducto()).orElseThrow();
     		BigDecimal tope = InventarioService.STOCK_MAXIMO.setScale(producto.getUnidad().decimales(), RoundingMode.FLOOR);
     		BigDecimal nuevo = producto.getStock().add(linea.getCantidad()).min(tope);
     		BigDecimal devuelto = nuevo.subtract(producto.getStock());
     		if (devuelto.signum() > 0) {
     			producto.aplicarMovimiento(nuevo);
     			movimientoRepository.save(MovimientoStock.liberacion(producto, devuelto, nuevo, pedido, ahora, admin));
     		}
     	}
     	pedido.anular(admin, motivo.strip(), ahora); // RF-19
     	return VentaResponse.from(pedido, ahora);
     }
     ```

     El motivo, de 1 a 200 caracteres sin los espacios de alrededor (400 si
     no).
   - `VentaResponse`: número, estado, canal, fechas, comprador (nombre o
     `null`), vendedor, líneas, total, medio, recibido, cambio, cobro (QR y
     tiempo, mientras está pendiente), anulación (quién, cuándo y motivo) y
     `anulable` (RF-18, calculado para el Administrador).
   - Pruebas (`VentaTiendaIntegracionTest`, con el reloj fijo y el simulador):
     - `buscaSoloProductosActivosConSuDisponible` (RF-1, RF-3);
     - `unPedidoEnLineaNoEsUnaVenta` (casos límite: 404 `VENTA_NO_ENCONTRADA` en
       ver, cancelar y anular);
     - `encuentraAlClientePorCorreoExactoYNoAlPersonalNiAlDesactivado` (RF-4);
     - `enEfectivoRestaElStockYQuedaEntregadaConElCambio` (RF-5, RF-6);
     - `conQrReservaYGeneraElCobro` (RF-7);
     - `conQrPagadaATiempoQuedaEntregada` (RF-8);
     - `cancelarDevuelveElStock` (RF-9);
     - `venceALosDiezMinutosYConsultaAntes` (RF-10);
     - `elPagoTardioReservaDeNuevoOQuedaParaDevolver` (RF-11);
     - `siNoAlcanzaNoRegistraNadaYDiceCuantoHay` (RF-12);
     - `unProductoDesactivadoSaleComoNoDisponible` (RF-12, casos límite);
     - `siElSistemaDePagoNoGeneraElQrLaVentaSeCancela` (casos límite: 503, la
       venta queda «Cancelado» y el stock vuelve);
     - `rechazaLineasYMontosInvalidos` (RF-13, parametrizada);
     - `aceptaElRecibidoIgualAlTotalConCambioCero` (RF-5, casos límite);
     - `rechazaSiCambioElPrecio` (RF-14);
     - `elComprobanteTraeTodo` (RF-15);
     - `elAdministradorAnulaLaDeHoyEnEfectivo` (RF-18, RF-19);
     - `noSeAnulaLaDeAyerNiLaDeQrNiDosVeces` (RF-20, con el reloj a las 00:01);
     - `permisosPorRol` (RF-23: el Contador lee y no escribe; el Cliente, 403);
     - `dosVentasDeLaUltimaUnidadALaVez` (casos límite, dos hilos como en
       `PedidoIntegracionTest`).

4. **`/pedidos` con canal** (`DespachoService`, `PedidoRepository.despacho`,
   `PedidoDespachoResponse`, `PedidoDespachoResumenResponse`).
   - La lista suma el parámetro `canal` (`TODOS` por defecto; uno que no
     existe, `TODOS`), y cada fila suma su canal (RF-16).
   - `join p.usuario` pasa a `left join`.
   - La ficha suma el canal, el medio, quién vendió y la anulación; el
     cliente es `null` en el consumidor final.
   - `entregar` sigue igual: una `TIENDA` «Pagado» por pago tardío se entrega
     como cualquiera (RF-11).
   - Pruebas, en `DespachoIntegracionTest`:
     - `filtraPorCanal` (RF-16);
     - `laFichaDeUnaVentaDelMostradorSinComprador` (RF-16);
     - las de HU-17 siguen en verde.

5. **Reportes por canal** (`ReportesRepository`, `ReportesService`,
   `Periodo`, `exportar/`).
   - Todas las consultas pasan a `left join p.usuario u`, con
     `coalesce(u.tipo, PUBLICO_GENERAL)` para el tipo (decisiones de la
     historia), y suman `and (:canal is null or p.canal = :canal)` (RF-22).
     `ANULADO` no está en `VENDIDOS` ni en `PERDIDOS`, así que no suma (RF-21);
     clientes inactivos ignora las de `usuario` nulo.
   - `ReportesController` suma `?canal=` (vacío o desconocido = todos). El
     resumen suma `porCanal` y `porMedio` (ingresos y cantidad).
   - Excel y PDF: una línea «Canal: …» bajo el período (RF-22).
   - Pruebas:
     - `ReportesIntegracionTest`:
       - `cuentaLasVentasDelMostradorYNoLasAnuladas` (RF-21);
       - `filtraPorCanalYRepartePorMedio` (RF-22);
       - `unaVentaDelMostradorCanceladaNoEsPedidoPerdido` (RF-21);
       - `elConsumidorFinalCuentaComoPublicoGeneral` (decisiones de la historia).
     - `ReportesExportarIntegracionTest`:
       - `elExcelYElPdfNombranElCanal` (RF-22).
     - Las de HU-18 y HU-19 siguen en verde.

6. **Frontend: modelos y servicio** (`core/modelos/venta.ts`,
   `core/api/ventas.service.ts`, `core/api/despacho.service.ts`,
   `core/api/reportes.service.ts`, `mensajes-error.ts`).
   - `VentasService`: las lecturas con `httpResource` (productos, comprobante)
     y las escrituras con `HttpClient` (`registrar`, `consultar`, `cancelar`,
     `anular`, `simular`, `buscarCliente`).
   - `canal` en los filtros de despacho y de reportes.
   - Mensajes: `STOCK_INSUFICIENTE` en la venta, `VENTA_NO_ANULABLE` y
     `VENTA_NO_ENCONTRADA`.
   - Pruebas: `ventas.service.spec.ts` (cada endpoint y sus errores);
     `mensajes-error.spec.ts`.

7. **Pantalla «Nueva venta»** (`features/venta/`, ruta `/ventas/nueva` con
   `rolGuard` con `data: { roles: ['EMPLEADO', 'ADMINISTRADOR'] }`). Con Signal Forms, estilos por
   tokens; pasa por `impeccable` antes de la prueba manual.
   - Búsqueda con espera de 300 ms y resultados con el disponible; las líneas
     se editan con `PasoDeCantidad` (`shared/paso-de-cantidad/`) según la unidad; total;
     comprador (correo → nombre, o «Consumidor final»); «Cobrar en efectivo»
     (recibido y cambio en vivo) y «Cobrar con QR».
   - Con QR, el QR, la cuenta regresiva, la consulta cada 5 s mientras está a
     la vista, «Ya pagó» y «Cancelar venta», que deja las líneas para cobrar
     de otra forma (RF-9); con el simulador, sus botones.
   - Los rechazos marcan sus líneas (RF-12, RF-14). «Confirmar» con
     `aria-disabled` mientras espera.
   - Pruebas (`venta.page.spec.ts`):
     - `agregaYSumaUnaLineaPorProducto` (RF-2);
     - `marcaLaLineaQuePasaDelDisponible` (RF-3);
     - `buscaAlCompradorPorCorreo` (RF-4);
     - `calculaElCambioYNoDejaCobrarMenos` (RF-5);
     - `enEfectivoVaAlComprobante` (RF-6);
     - `conQrMuestraElQrYConsulta` (RF-7, RF-8);
     - `cancelarDejaLasLineas` (RF-9);
     - `marcaLasLineasRechazadas` (RF-12, RF-14);
     - `siNoSeGeneraElQrQuedanLasLineas` (casos límite, `COBRO_NO_DISPONIBLE`);
     - `unSegundoToqueNoRegistraDosVeces` (casos límite).

8. **Comprobante** (`features/comprobante/`, ruta `/ventas/:id` para el
   personal).
   - Ticket con la marca, el número, los datos de RF-15 y «No válido como
     factura».
   - «Imprimir» llama a `window.print()`; `@media print` esconde el encabezado,
     los botones y el salto al contenido, a 80 mm de ancho.
   - «Anular venta» (diálogo con el motivo) solo si `anulable`.
   - Un id que no es un número o que es de un pedido en línea: «No
     encontramos esa venta».
   - Pruebas (`comprobante.page.spec.ts`):
     - `muestraElTicketCompleto` (RF-15);
     - `enEfectivoMuestraRecibidoYCambio` (RF-15);
     - `anularPideMotivoYMarcaAnulada` (RF-18, RF-19);
     - `sinAnularParaElEmpleado` (RF-20);
     - `idBasuraOEnLinea` (casos límite).
   - A mano (no se prueba en jsdom): la impresión sin encabezado ni botones
     (RF-15), en la prueba manual y en la revisión de interfaz.

9. **El resto de las pantallas**:
   - `despacho` (filtro y columna «Canal») y `despacho-pedido` (canal, medio,
     vendedor, anulación y enlace al comprobante) (RF-16);
   - `mis-pedidos` y `pedido` («En tienda», sin datos de retiro) (RF-17);
   - el encabezado y el panel («Nueva venta», RF-24);
   - `inventario` y `producto` (los movimientos de una venta se leen «Venta
     en tienda N.º …» y «Anulación de venta N.º …»; `MovimientoResponse` suma
     el canal y el estado del pedido).
   - Pruebas:
     - `despacho.page.spec.ts` → `filtraYMuestraElCanal` (RF-16);
     - `despacho-pedido.page.spec.ts` → `fichaDeVentaDelMostrador` (RF-16);
     - `mis-pedidos.page.spec.ts` y `pedido.page.spec.ts` →
       `ventaEnTiendaSinDatosDeRetiro` (RF-17);
     - `app.component.spec.ts` → `nuevaVentaSoloParaEmpleadoYAdministrador`
       (RF-24);
     - `panel.page.spec.ts` → `tarjetaNuevaVentaSegunElRol` (RF-24, escenario
       «El Contador mira»);
     - `producto.page.spec.ts` → `movimientosDeVentaYAnulacion` (decisiones
       de la historia);
     - `app.routes.spec.ts` → `nuevaVentaSoloConElRol` (casos límite: el
       Cliente y el Contador en `/ventas/nueva`).

10. **Reportes en pantalla** (`features/reportes/`): el selector «Canal» junto
    al período, en la dirección (`?canal=`) y en la descarga; en el resumen,
    los ingresos por canal y por medio (RF-22).
    - Pruebas: `reportes.page.spec.ts` → `elCanalFiltraYViajaEnLaDescarga`,
      `elResumenRepartePorCanalYMedio`.

11. **Extremo a extremo** (`frontend/e2e/venta.spec.ts`):
    - el Empleado vende en efectivo a un consumidor final;
    - ve el comprobante;
    - la venta aparece en «Pedidos» con el canal;
    - el Administrador la anula;
    - axe y la CSP en cada pantalla, en escritorio y en celular.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR» que le tocan: el
subagente de interfaz (tiene pantallas), `/code-review` y `/security-review`
(toca pagos, dinero y roles), `web-design-guidelines` sobre los componentes
nuevos y `break-ui` sobre «Nueva venta» y el comprobante.

## Orden y suites

Las tareas 1 y 2 tocan código compartido: después de cada una, la suite completa
del backend. La 3 a la 5, sus clases y al final la suite. Del 6 al 10, sus
pruebas y `ng build`, y al terminar la suite del frontend y `npm run lint`. En
`/sdd:cerrar`, todo y el e2e.

## Resultado

Tareas 1 a 11 hechas con TDD en una sesión en la nube; el cierre, en la PC
(06/11/2026). Backend con 831 pruebas (1 saltada), frontend con 79 archivos y
933 pruebas, `npm run lint` y `ng build` sin avisos. De extremo a extremo, en la PC: «desde el celular se vende en el mostrador»
en verde; las de escritorio chocan con un error que viene de `main` y solo
pasa en Windows (el hash del script del tema se calcula con los CRLF del
checkout: PR aparte `fix/politica-crlf`), y en el CI, en Linux, corren
completas.

### Cobertura RF → pruebas

| RF | Pruebas |
|---|---|
| RF-1 | `VentaTiendaIntegracionTest`, `ventas.service.spec` |
| RF-2 | `venta.page.spec` |
| RF-3 | `VentaTiendaIntegracionTest`, `venta.page.spec` |
| RF-4 | `VentaTiendaIntegracionTest`, `venta.page.spec`, `ventas.service.spec` |
| RF-5, RF-6 | `PedidoTest`, `VentaTiendaIntegracionTest`, `venta.page.spec`, `ventas.service.spec` |
| RF-7 | `VentaTiendaIntegracionTest`, `venta.page.spec` |
| RF-8 | `PagoIntegracionTest`, `VentaTiendaIntegracionTest`, `venta.page.spec`, `ventas.service.spec` |
| RF-9 | `VentaTiendaIntegracionTest`, `venta.page.spec`, `ventas.service.spec` |
| RF-10 | `PagoIntegracionTest`, `VentaTiendaIntegracionTest` |
| RF-11 | `DespachoIntegracionTest`, `PagoIntegracionTest`, `VentaTiendaIntegracionTest` |
| RF-12 | `VentaTiendaIntegracionTest`, `venta.page.spec`, `ventas.service.spec` |
| RF-13 | `VentaTiendaIntegracionTest` (la pantalla no deja mandar lo inválido: `venta.page.spec`) |
| RF-14 | `VentaTiendaIntegracionTest`, `venta.page.spec` |
| RF-15 | `VentaTiendaIntegracionTest`, `comprobante.page.spec`, `ventas.service.spec` |
| RF-16 | `DespachoIntegracionTest`, `despacho.page.spec`, `despacho-pedido.page.spec` |
| RF-17 | `MisPedidosIntegracionTest`, `mis-pedidos.page.spec`, `pedido.page.spec` |
| RF-18, RF-19 | `PedidoTest`, `VentaTiendaIntegracionTest`, `comprobante.page.spec`, `ventas.service.spec` |
| RF-20 | `PedidoTest`, `VentaTiendaIntegracionTest`, `comprobante.page.spec` |
| RF-21 | `ReportesIntegracionTest` |
| RF-22 | `ReportesIntegracionTest`, `ReportesExportarIntegracionTest`, `reportes.page.spec`, `reportes-resumen.spec`, `reportes.spec` |
| RF-23 | `VentaTiendaIntegracionTest`, `PedidoIntegracionTest`, `app.routes.spec` |
| RF-24 | `app.component.spec`, `panel.page.spec` |

De extremo a extremo, `e2e/venta.spec.ts` (efectivo, comprobante, «Pedidos»
con el canal y la anulación) y `e2e/celular.spec.ts`.

Distinto del plan:
- **La e2e la hace el Administrador,** no el Empleado: una sola sesión, del
  que también vende y es quien anula. El Empleado lo cubren las pruebas de la
  API y la prueba manual.
- **Al fallar la generación del QR, la venta se cierra solo si sigue
  pendiente** (`/code-review`): otro pudo cancelarla o vencerla mientras tanto.
- **Los importes de «Nueva venta» en centavos enteros**
  (`decisiones.md` → «Decisiones técnicas del frontend», 06/11/2026).
- **`Canal.deTexto`** lee el filtro de canal de `/pedidos` y de los reportes,
  en un solo lugar.

### Revisiones

- **Subagente de interfaz** (tienda de pruebas, escritorio e iPhone 15, los dos
  temas, peores datos). Confirmó el comprador, el cambio, los rechazos con
  `role="alert"`, el QR del simulador, la impresión (solo el ticket, negro
  sobre blanco), el filtro «Canal», nombres larguísimos, objetivos de 44 px y
  sin desplazamiento horizontal. **Corregido:**
  - en modo oscuro las líneas usaban el error y el foco del tema oscuro sobre
    la lona clara (1,73:1 y 1,3:1): ahora los de la lona, como el carrito;
  - lo recibido, el correo del comprador y el motivo de la anulación no
    mostraban el borde de error de 2 px;
  - en el celular un subtotal grande tapaba el «+» (ahora va en su fila) y el
    total se salía de la tarjeta;
  - durante el QR, «Quitar» y la búsqueda no se veían desactivados (la búsqueda
    ahora es de solo lectura);
  - al rechazar por stock el foco se quedaba en el botón: ahora va a la
    primera línea marcada; al anular, al título del ticket;
  - lo recibido de más de Bs 999.999,99 decía «Escriba con números»: ahora
    dice el tope.
  **Pendiente:** una venta de más de Bs 999.999,99 no se puede cobrar en
  efectivo (lo recibido tiene ese tope, RF-13); no se pregunta por ahora.
- **`/security-review`** (controladores, servicios, `SecurityConfig`, DTO y
  `Pedido`): sin hallazgos de confianza ≥ 8. Se revisaron el orden de las
  reglas de `SecurityConfig`, el simulador solo con `simulador`, que un Cliente
  no vea la venta de otro, precio y total desde la pantalla, cantidades y lo
  recibido, anular dos veces, el QR confirmado por consulta, `canal` como
  parámetro y los datos de la búsqueda del comprador.
- **`/code-review`, dos rondas.**
  - **Corregido en la primera:**
    - el total sumaba con los restos de JavaScript y rechazaba un pago justo
      (85 + 8,46);
    - si cancelaban la venta mientras se generaba el QR y la generación
      fallaba, respondía 500 en lugar de 503;
    - la lectura del canal estaba repetida.
  - **Corregido en la segunda:** el subtotal redondeaba el medio centavo hacia
    abajo y el servidor hacia arriba (4,50 × 0,69): la venta no se podía
    cobrar nunca. Pruebas más estrictas del cambio en cero y de una sola
    devolución de stock.
  - **Descartado:** rearmar los temporizadores del QR en cada consulta (así la
    cuenta regresiva toma la del servidor); bloquear la venta en cada consulta
    y leer dos veces la venta en efectivo (rendimiento, sin efecto con este
    tráfico); las fotos de la búsqueda (hasta 10 por consulta); el precio viejo
    tras un 404 (un producto oculto se rechaza antes como «no disponible»);
    `cerrar` sin revisar el estado (cada llamada ya lo revisa); un registro
    aparte para la venta cancelada mientras tanto; `Canal.deTexto` en
    `entity`; un canal inválido que da los dos (así lo piden RF-16 y RF-22).

### Prueba manual

Fabio, 06/11/2026, con Libélula real (el producto «prueba» para el QR): los
nueve pasos pasaron (efectivo, stock que no alcanza, a nombre de un Cliente,
QR pagado y cancelado, «Pedidos» con el canal, anular, Contador y reportes,
direcciones con basura y celular). Preguntó por qué la venta con QR cancelada y
la cobrada después en efectivo quedan como dos ventas: así lo pide RF-9, y
cada cobro conserva su venta (un pago tardío del QR cae en la cancelada, RF-11).
**Lo dejó así.**
