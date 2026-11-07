package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import bo.edu.uagrm.tienda.dto.CobroResponse;
import bo.edu.uagrm.tienda.dto.CompradorResponse;
import bo.edu.uagrm.tienda.dto.EstadoLineaCarrito;
import bo.edu.uagrm.tienda.dto.LineaVentaRequest;
import bo.edu.uagrm.tienda.dto.ProductoVentaResponse;
import bo.edu.uagrm.tienda.dto.VentaRequest;
import bo.edu.uagrm.tienda.dto.VentaResponse;
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.LineaPedido;
import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException.CampoInvalido;
import bo.edu.uagrm.tienda.exception.CobroNoDisponibleException;
import bo.edu.uagrm.tienda.exception.CobroNoEncontradoException;
import bo.edu.uagrm.tienda.exception.PedidoNoPendienteException;
import bo.edu.uagrm.tienda.exception.StockInsuficienteException;
import bo.edu.uagrm.tienda.exception.StockInsuficienteException.LineaRechazada;
import bo.edu.uagrm.tienda.exception.StockInsuficienteException.Motivo;
import bo.edu.uagrm.tienda.exception.TotalCambiadoException;
import bo.edu.uagrm.tienda.exception.UsuarioNoEncontradoException;
import bo.edu.uagrm.tienda.exception.VentaNoAnulableException;
import bo.edu.uagrm.tienda.exception.VentaNoEncontradaException;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.pago.CobroGenerado;
import bo.edu.uagrm.tienda.service.pago.IPago;
import bo.edu.uagrm.tienda.service.pago.SistemaDePagoNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// La venta del mostrador (HU-23; decisiones.md → Venta en tienda). Es un pedido de canal TIENDA, y se bloquea en este
// orden: la venta, después su cobro y después los productos por id ascendente. Nunca al cliente: una venta del mostrador
// no toca su carrito ni su pedido en línea, y lo de en línea solo bloquea pedidos en línea, así que nadie espera en
// círculo. Ninguna llamada al sistema de pago se hace con filas bloqueadas ni con una transacción abierta. READ_COMMITTED,
// como todo lo que puede vencer un pedido (decisiones.md → Pagos). Quién puede cada cosa lo decide SecurityConfig
@Slf4j
@Service
public class VentaTiendaService {

	// En línea son 30 minutos: en el mostrador el cliente está parado enfrente (decisiones.md → Venta en tienda)
	public static final Duration VIGENCIA = Duration.ofMinutes(10);
	static final int RESULTADOS = 10;
	private static final BigDecimal MAXIMA_CANTIDAD = CarritoService.MAXIMO_CANTIDAD;

	private final PedidoRepository pedidoRepository;
	private final ProductoRepository productoRepository;
	private final UsuarioRepository usuarioRepository;
	private final MovimientoStockRepository movimientoRepository;
	private final CobroRepository cobroRepository;
	private final PedidoService pedidoService;
	private final ConciliacionDePagos conciliacionDePagos;
	private final IPago iPago;
	private final Clock clock;
	private final String marca;
	private final boolean simulador;
	private final TransactionTemplate transaccion;

	public VentaTiendaService(PedidoRepository pedidoRepository, ProductoRepository productoRepository,
			UsuarioRepository usuarioRepository, MovimientoStockRepository movimientoRepository,
			CobroRepository cobroRepository, PedidoService pedidoService, ConciliacionDePagos conciliacionDePagos,
			IPago iPago, Clock clock, @Value("${tienda.correo.marca}") String marca,
			@Value("${tienda.pago.proveedor:simulador}") String proveedor,
			PlatformTransactionManager transactionManager) {
		this.pedidoRepository = pedidoRepository;
		this.productoRepository = productoRepository;
		this.usuarioRepository = usuarioRepository;
		this.movimientoRepository = movimientoRepository;
		this.cobroRepository = cobroRepository;
		this.pedidoService = pedidoService;
		this.conciliacionDePagos = conciliacionDePagos;
		this.iPago = iPago;
		this.clock = clock;
		this.marca = marca;
		this.simulador = "simulador".equals(proveedor);
		this.transaccion = new TransactionTemplate(transactionManager);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	// RF-1, RF-3: los que el catálogo vende (activos de categorías visibles), con el disponible. Sin texto, nada
	@Transactional(readOnly = true)
	public List<ProductoVentaResponse> buscarProductos(String texto) {
		String patron = Busquedas.patron(texto, Producto::textoDeBusqueda);
		if (patron == null) {
			return List.of();
		}
		return productoRepository.catalogo(patron, null, null, null, null, false,
				PageRequest.of(0, RESULTADOS, Sort.by("nombre", "idProducto"))).map(ProductoVentaResponse::from)
				.getContent();
	}

	// RF-4: el correo exacto, sin distinguir mayúsculas; solo una cuenta de Cliente activa. Si no, 404
	@Transactional(readOnly = true)
	public CompradorResponse buscarCliente(String correo) {
		if (correo == null || correo.isBlank()) {
			throw new UsuarioNoEncontradoException();
		}
		return usuarioRepository.findByCorreo(Usuario.normalizarCorreo(correo)).filter(VentaTiendaService::esComprador)
				.map(CompradorResponse::from).orElseThrow(UsuarioNoEncontradoException::new);
	}

	// RF-5 a RF-7. En efectivo, todo en una transacción. Con QR, como PagoService.pagar: la venta se registra en una,
	// el cobro se genera sin nada abierto y se guarda en otra corta. Si el sistema de pago no lo genera, la venta se
	// cancela y el stock vuelve (sin «Reintentar QR»: decisiones.md → Venta en tienda)
	public VentaResponse crear(VentaRequest venta, Long idVendedor) {
		Pedido registrada = transaccion.execute(estado -> registrar(venta, idVendedor));
		Long idPedido = registrada.getIdPedido();
		if (venta.medio() == MedioPago.EFECTIVO) {
			return transaccion.execute(estado -> leer(idPedido));
		}
		CobroGenerado generado;
		try {
			generado = iPago.generarCobro(idPedido, registrada.getTotal(), "Venta N.º " + idPedido + " — " + marca);
		} catch (SistemaDePagoNoDisponibleException error) {
			log.warn("No se pudo generar el cobro de la venta {}", idPedido, error);
			// Mientras se generaba, otro pudo cancelarla o vencerla: ya devolvió el stock
			transaccion.executeWithoutResult(estado -> {
				Pedido pedido = bloquearVenta(idPedido);
				if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO) {
					cerrar(pedido, EstadoPedido.CANCELADO);
				}
			});
			throw new CobroNoDisponibleException(); // 503; las líneas siguen en la pantalla
		}
		return transaccion.execute(estado -> {
			Pedido pedido = bloquearVenta(idPedido);
			LocalDateTime ahora = LocalDateTime.now(clock);
			// Mientras se generaba, otro pudo cancelarla o pasó la hora: el cobro no se guarda y nadie ve ese QR
			if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO && pedido.getVence().isAfter(ahora)) {
				cobroRepository.save(Cobro.generar(pedido, generado.referencia(), generado.codigoQr(),
						generado.urlPasarela(), ahora));
			}
			return respuesta(pedido, ahora);
		});
	}

	// Todo o nada, en el mismo orden que un pedido en línea: primero se inserta la venta (la clave foránea toma el
	// bloqueo compartido de la cuenta del comprador, como el bloqueo del cliente al confirmar) y después los productos,
	// por id ascendente y antes de leerlos: dos ventas, un pedido y un ajuste no se esperan en círculo (RF-12, casos
	// límite)
	private Pedido registrar(VentaRequest venta, Long idVendedor) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		exigirMonto(venta);
		Set<Long> vistos = new HashSet<>();
		if (!venta.lineas().stream().allMatch(linea -> vistos.add(linea.idProducto()))) {
			throw campoInvalido("lineas", "tiene el mismo producto dos veces");
		}
		Usuario vendedor = usuarioRepository.getReferenceById(idVendedor);
		Usuario comprador = venta.idCliente() == null ? null
				: usuarioRepository.findById(venta.idCliente()).filter(VentaTiendaService::esComprador)
						.orElseThrow(() -> campoInvalido("idCliente", "no es una cuenta de Cliente activa"));
		Pedido pedido = pedidoRepository.save(
				Pedido.enTienda(comprador, vendedor, venta.medio(), venta.recibido(), ahora, VIGENCIA));
		Map<Long, Producto> productos = new TreeMap<>();
		venta.lineas().stream().map(LineaVentaRequest::idProducto).sorted()
				.forEach(id -> productos.put(id, productoRepository.findConBloqueoByIdProducto(id)
						.orElseThrow(() -> campoInvalido("lineas", "un producto no existe"))));
		List<LineaRechazada> rechazadas = venta.lineas().stream()
				.map(linea -> rechazo(productos.get(linea.idProducto()), linea.cantidad())).flatMap(Optional::stream)
				.toList();
		if (!rechazadas.isEmpty()) {
			throw StockInsuficienteException.enLaVenta(rechazadas); // RF-12
		}
		venta.lineas().forEach(linea -> pedido.agregarLinea(productos.get(linea.idProducto()), linea.cantidad()));
		// Con los precios del servidor; el de la pantalla solo se compara (RF-14)
		if (pedido.getTotal().compareTo(venta.totalEsperado()) != 0) {
			throw new TotalCambiadoException(pedido.getTotal());
		}
		if (venta.medio() == MedioPago.EFECTIVO && venta.recibido().compareTo(pedido.getTotal()) < 0) {
			throw campoInvalido("recibido", "no puede ser menor que el total"); // RF-13
		}
		for (LineaPedido linea : pedido.lineasPorProducto()) {
			Producto producto = productos.get(linea.getIdProducto());
			// Nunca negativo: se revisó con el producto bloqueado
			BigDecimal nuevo = producto.getStock().subtract(linea.getCantidad());
			producto.aplicarMovimiento(nuevo);
			movimientoRepository.save(MovimientoStock.reserva(producto, linea.getCantidad(), nuevo, pedido, ahora));
		}
		if (venta.medio() == MedioPago.EFECTIVO) {
			// RF-6: lo confirma quien recibió el dinero (CLAUDE.md → Pago)
			pedido.cobrarEnTienda(ahora);
		}
		return pedido;
	}

	// RF-15: el comprobante, también para el Contador
	@Transactional(readOnly = true)
	public VentaResponse ver(Long idPedido) {
		return leer(idPedido);
	}

	// RF-8: pregunta al sistema de pago, sin nada bloqueado ni transacción abierta, y devuelve cómo quedó. Al llegar a
	// cero vence acá si la consulta dijo que no se pagó (RF-10), como la página del pedido en línea. Las llamadas a los
	// métodos de esta clase no pasan por el proxy de @Transactional: van con transaccion
	public VentaResponse consultar(Long idPedido) {
		Optional<Long> cobro = transaccion.execute(estado -> {
			leer(idPedido);
			return pedidoService.cobroPendiente(idPedido).map(Cobro::getIdCobro);
		});
		cobro.ifPresent(conciliacionDePagos::conciliar);
		transaccion.executeWithoutResult(estado -> vencerSiCorresponde(bloquearVenta(idPedido)));
		return transaccion.execute(estado -> leer(idPedido));
	}

	// RF-9: el Empleado o el Administrador la cancela mientras espera el pago, por ejemplo para cobrar en efectivo
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public VentaResponse cancelar(Long idPedido) {
		Pedido venta = bloquearVenta(idPedido);
		if (venta.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
			throw new PedidoNoPendienteException(); // ya cobrada, cancelada o vencida
		}
		cerrar(venta, EstadoPedido.CANCELADO);
		return respuesta(venta, LocalDateTime.now(clock));
	}

	// Para VencimientoPedidos y la consulta, después de consultar el cobro sin nada bloqueado (RF-10). Si ya se cobró,
	// se canceló o todavía no puede vencer, no hace nada
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void vencer(Long idPedido) {
		vencerSiCorresponde(bloquearVenta(idPedido));
	}

	// Con la venta ya bloqueada
	private void vencerSiCorresponde(Pedido venta) {
		if (venta.getEstado() == EstadoPedido.PENDIENTE_PAGO
				&& pedidoService.puedeVencer(venta, LocalDateTime.now(clock))) {
			cerrar(venta, EstadoPedido.VENCIDO);
		}
	}

	// RF-18 a RF-20: solo en efectivo, entregada y del mismo día; el reloj es el de Bolivia (ClockConfig). El stock
	// vuelve a nombre del Administrador. Que anule un Administrador lo decide SecurityConfig
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public VentaResponse anular(Long idPedido, Long idAdministrador, String motivo) {
		Pedido venta = bloquearVenta(idPedido);
		LocalDateTime ahora = LocalDateTime.now(clock);
		if (!anulable(venta, ahora)) {
			throw new VentaNoAnulableException();
		}
		Usuario administrador = usuarioRepository.getReferenceById(idAdministrador);
		devolverElStock(venta, ahora, administrador);
		venta.anular(administrador, motivo, ahora);
		return respuesta(venta, ahora);
	}

	// Solo con el simulador (VentaSimulacionController): la referencia del cobro pendiente de la venta
	@Transactional(readOnly = true)
	public String referenciaParaSimular(Long idPedido) {
		leer(idPedido);
		return pedidoService.cobroPendiente(idPedido).map(Cobro::getReferencia)
				.orElseThrow(CobroNoEncontradoException::new);
	}

	// Dentro de una transacción
	private VentaResponse leer(Long idPedido) {
		Pedido venta = pedidoRepository.findById(idPedido).filter(Pedido::esEnTienda)
				.orElseThrow(VentaNoEncontradaException::new);
		return respuesta(venta, LocalDateTime.now(clock));
	}

	private Pedido bloquearVenta(Long idPedido) {
		return pedidoRepository.findConBloqueoByIdPedido(idPedido).filter(Pedido::esEnTienda)
				.orElseThrow(VentaNoEncontradaException::new);
	}

	// Con la venta ya bloqueada: devuelve el stock y la cierra (RF-9, RF-10)
	void cerrar(Pedido venta, EstadoPedido estadoFinal) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		devolverElStock(venta, ahora, venta.responsable());
		if (estadoFinal == EstadoPedido.CANCELADO) {
			venta.cancelar(ahora);
		} else {
			venta.vencer(ahora);
		}
	}

	// Con los productos bloqueados por id ascendente. Lo devuelto no pasa del máximo, como PedidoService.liberar
	private void devolverElStock(Pedido venta, LocalDateTime ahora, Usuario quien) {
		for (LineaPedido linea : venta.lineasPorProducto()) {
			Producto producto = productoRepository.findConBloqueoByIdProducto(linea.getIdProducto()).orElseThrow();
			BigDecimal tope = InventarioService.STOCK_MAXIMO.setScale(producto.getUnidad().decimales(),
					RoundingMode.FLOOR);
			BigDecimal nuevo = producto.getStock().add(linea.getCantidad()).min(tope);
			BigDecimal devuelto = nuevo.subtract(producto.getStock());
			if (devuelto.signum() > 0) {
				producto.aplicarMovimiento(nuevo);
				movimientoRepository.save(MovimientoStock.liberacion(producto, devuelto, nuevo, venta, ahora, quien));
			}
		}
	}

	// Lee el cobro sin bloqueo: con la venta ya bloqueada, o en una lectura
	private VentaResponse respuesta(Pedido venta, LocalDateTime ahora) {
		CobroResponse cobro = venta.getEstado() != EstadoPedido.PENDIENTE_PAGO ? null
				: pedidoService.cobroPendiente(venta.getIdPedido()).map(c -> CobroResponse.from(c, simulador))
						.orElse(null);
		return VentaResponse.from(venta, cobro, anulable(venta, ahora), ahora);
	}

	private static boolean anulable(Pedido venta, LocalDateTime ahora) {
		return venta.esEnTienda() && venta.getMedioPago() == MedioPago.EFECTIVO
				&& venta.getEstado() == EstadoPedido.ENTREGADO
				&& venta.getPagado().toLocalDate().equals(ahora.toLocalDate());
	}

	private static boolean esComprador(Usuario usuario) {
		return usuario.getRol() == Rol.CLIENTE && usuario.getEstado() == EstadoCuenta.ACTIVA;
	}

	// RF-13: en efectivo, lo recibido es obligatorio; con QR no se recibe nada
	private static void exigirMonto(VentaRequest venta) {
		if (venta.medio() == MedioPago.EFECTIVO && venta.recibido() == null) {
			throw campoInvalido("recibido", "es obligatorio en efectivo");
		}
		if (venta.medio() == MedioPago.QR && venta.recibido() != null) {
			throw campoInvalido("recibido", "no corresponde con QR");
		}
	}

	// Lo mismo que rechaza la confirmación del pedido y, si pide más de lo que hay, cuánto hay (RF-12, RF-13)
	private static Optional<LineaRechazada> rechazo(Producto producto, BigDecimal cantidad) {
		if (cantidad.compareTo(MAXIMA_CANTIDAD) > 0) {
			throw campoInvalido("lineas", "una cantidad pasa de 999");
		}
		EstadoLineaCarrito estado = CarritoService.estado(producto, cantidad);
		if (estado == EstadoLineaCarrito.CANTIDAD_INVALIDA) {
			throw campoInvalido("lineas", "una cantidad no admite decimales en su unidad");
		}
		if (estado != EstadoLineaCarrito.DISPONIBLE) {
			return Optional.of(new LineaRechazada(producto.getIdProducto(), Motivo.valueOf(estado.name()),
					producto.getStock()));
		}
		if (cantidad.compareTo(producto.getStock()) > 0) {
			return Optional.of(new LineaRechazada(producto.getIdProducto(), Motivo.NO_ALCANZA, producto.getStock()));
		}
		return Optional.empty();
	}

	private static CamposInvalidosException campoInvalido(String campo, String mensaje) {
		return new CamposInvalidosException(List.of(new CampoInvalido(campo, mensaje)));
	}
}
