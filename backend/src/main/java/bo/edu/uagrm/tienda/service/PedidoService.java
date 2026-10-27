package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.config.RetiroProperties;
import bo.edu.uagrm.tienda.dto.CancelacionResponse;
import bo.edu.uagrm.tienda.dto.CobroResponse;
import bo.edu.uagrm.tienda.dto.ConfirmacionRequest;
import bo.edu.uagrm.tienda.dto.EstadoLineaCarrito;
import bo.edu.uagrm.tienda.dto.MisPedidosResponse;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.PedidoResponse;
import bo.edu.uagrm.tienda.dto.PedidoResumenResponse;
import bo.edu.uagrm.tienda.dto.RetiroResponse;
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.LineaCarrito;
import bo.edu.uagrm.tienda.entity.LineaPedido;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CarritoVacioException;
import bo.edu.uagrm.tienda.exception.CorreoSinVerificarException;
import bo.edu.uagrm.tienda.exception.PedidoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.PedidoNoPendienteException;
import bo.edu.uagrm.tienda.exception.PedidoPendienteException;
import bo.edu.uagrm.tienda.exception.StockInsuficienteException;
import bo.edu.uagrm.tienda.exception.StockInsuficienteException.LineaRechazada;
import bo.edu.uagrm.tienda.exception.StockInsuficienteException.Motivo;
import bo.edu.uagrm.tienda.exception.TotalCambiadoException;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository.FilaDePedido;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// Los pedidos del Cliente (HU-12; decisiones.md → Pedidos). Las filas se bloquean siempre en el mismo orden: el
// cliente, después el pedido y después los productos por id ascendente. Así dos confirmaciones, una cancelación, el
// vencimiento y el inventario nunca se esperan en círculo (RF-7, RF-12). Cada fila se bloquea antes de leerla en la
// transacción: una entidad ya leída no se vuelve a leer al bloquearla y quedaría con datos viejos. Los cobros (HU-13) se
// leen sin bloqueo y con el cliente ya bloqueado: todo el que escribe un cobro bloquea antes al cliente. Por eso lo
// que puede vencer un pedido va con READ_COMMITTED: con REPEATABLE_READ, una lectura sin bloqueo anterior al bloqueo
// del cliente fija la vista y no vería un cobro guardado mientras esperaba (HU-13 RF-7)
@Service
@RequiredArgsConstructor
public class PedidoService {

	public static final Duration VIGENCIA = Duration.ofMinutes(30);
	static final int POR_PAGINA = 10;

	private final PedidoRepository pedidoRepository;
	private final LineaCarritoRepository lineaCarritoRepository;
	private final ProductoRepository productoRepository;
	private final UsuarioRepository usuarioRepository;
	private final MovimientoStockRepository movimientoRepository;
	private final CobroRepository cobroRepository;
	private final RetiroProperties retiroProperties;
	private final Clock clock;

	// Con el simulador, la página muestra sus botones (HU-13 RF-12)
	@Value("${tienda.pago.proveedor:simulador}")
	private String proveedor;

	public RetiroResponse retiro() {
		return RetiroResponse.de(retiroProperties.direccion(), retiroProperties.horario());
	}

	// Todo o nada, en una sola transacción (RF-2, RF-3)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PedidoResponse confirmar(Long idUsuario, ConfirmacionRequest solicitud) {
		Usuario cliente = bloquearCliente(idUsuario);
		// Con la cuenta bloqueada, una verificación simultánea ya terminó o espera (HU-20 RF-6)
		if (!cliente.isCorreoVerificado()) {
			throw new CorreoSinVerificarException();
		}
		LocalDateTime ahora = LocalDateTime.now(clock);
		// Uno vencido no bloquea (RF-5): vence acá mismo y sus líneas vuelven al carrito antes de leerlo. Se leen sin
		// bloquearlos: todo lo que cambia un pedido bloquea antes al cliente, y en MySQL un SELECT … FOR UPDATE sin
		// filas bloquea el hueco del índice y traba las confirmaciones de dos clientes a la vez
		List<Pedido> vencidos = new ArrayList<>();
		for (Pedido pendiente : pedidoRepository.findByUsuario_IdUsuarioAndEstadoOrderByIdPedido(idUsuario,
				EstadoPedido.PENDIENTE_PAGO)) {
			// Tampoco uno que pasó la hora pero espera la consulta de su cobro (HU-13 RF-7)
			if (!puedeVencer(pendiente, ahora)) {
				throw new PedidoPendienteException(pendiente.getIdPedido());
			}
			vencidos.add(pendiente);
		}
		// Los productos del carrito y los de lo que vence, todos juntos por id ascendente, y antes de leer el
		// carrito, que los trae con su stock
		SortedSet<Long> idsProductos = new TreeSet<>(lineaCarritoRepository.idsDeProductos(idUsuario));
		vencidos.forEach(vencido -> vencido.getLineas().forEach(linea -> idsProductos.add(linea.getIdProducto())));
		idsProductos.forEach(this::bloquearProducto);
		vencidos.forEach(vencido -> liberar(vencido, EstadoPedido.VENCIDO, ahora));
		List<LineaCarrito> lineas = lineaCarritoRepository.findByUsuario_IdUsuarioOrderByFechaAgregadaAscIdLineaAsc(
				idUsuario);
		if (lineas.isEmpty()) {
			throw new CarritoVacioException(); // RF-4
		}
		List<LineaRechazada> rechazadas = lineas.stream().map(PedidoService::rechazo).flatMap(Optional::stream)
				.toList();
		if (!rechazadas.isEmpty()) {
			throw new StockInsuficienteException(rechazadas);
		}
		Pedido pedido = Pedido.confirmar(cliente, solicitud.nombreRetira(), solicitud.telefonoRetira(),
				solicitud.nota(), ahora, ahora.plus(VIGENCIA));
		lineas.forEach(linea -> pedido.agregarLinea(linea.getProducto(), linea.getCantidad()));
		// Con los precios del servidor; el del cliente solo se compara (RF-15)
		if (pedido.getTotal().compareTo(solicitud.totalEsperado()) != 0) {
			throw new TotalCambiadoException(pedido.getTotal());
		}
		pedidoRepository.save(pedido);
		for (LineaPedido linea : pedido.getLineas()) {
			Producto producto = linea.getProducto();
			// Nunca negativo: se revisó con el producto bloqueado (RF-14)
			BigDecimal nuevo = producto.getStock().subtract(linea.getCantidad());
			producto.aplicarMovimiento(nuevo);
			movimientoRepository.save(MovimientoStock.reserva(producto, linea.getCantidad(), nuevo, pedido, ahora));
		}
		lineaCarritoRepository.deleteByUsuario_IdUsuario(idUsuario);
		return respuesta(pedido, ahora);
	}

	// Primero vence el del cliente si ya pasó la hora (RF-10). Uno con cobro sigue «Pendiente» con el tiempo agotado
	// hasta que el sistema de pago diga que no se pagó: leer no lo consulta (HU-13 RF-7)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PedidoResponse ver(Long idUsuario, Long idPedido) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		vencerSiCorresponde(idUsuario);
		Pedido pedido = pedidoRepository.findByIdPedidoAndUsuario_IdUsuario(idPedido, idUsuario)
				.orElseThrow(PedidoNoEncontradoException::new);
		return respuesta(pedido, ahora);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CancelacionResponse cancelar(Long idUsuario, Long idPedido) {
		Pedido pedido = bloquear(idUsuario, idPedido);
		if (pedido.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
			throw new PedidoNoPendienteException(); // RF-12
		}
		LocalDateTime ahora = LocalDateTime.now(clock);
		int noVolvieron = liberar(pedido, EstadoPedido.CANCELADO, ahora);
		return new CancelacionResponse(respuesta(pedido, ahora), noVolvieron);
	}

	// Para la tarea programada (VencimientoPedidos), un pedido por transacción. Si ya se canceló, venció o pagó, o
	// todavía no puede vencer, no hace nada (RF-12; HU-13 RF-7)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void vencer(Long idUsuario, Long idPedido) {
		Pedido pedido = bloquear(idUsuario, idPedido);
		LocalDateTime ahora = LocalDateTime.now(clock);
		if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO && puedeVencer(pedido, ahora)) {
			liberar(pedido, EstadoPedido.VENCIDO, ahora);
		}
	}

	// Al leer el carrito o un pedido. Sin nada vencido no bloquea nada: es lo de casi siempre
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void vencerSiCorresponde(Long idUsuario) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		if (pedidoRepository.existsByUsuario_IdUsuarioAndEstadoAndVenceLessThanEqual(idUsuario,
				EstadoPedido.PENDIENTE_PAGO, ahora)) {
			bloquearCliente(idUsuario);
			vencerLosDelCliente(idUsuario, ahora);
		}
	}

	// «Mis pedidos» (HU-14). Primero vence el del Cliente si ya pasó la hora (RF-4), con READ_COMMITTED como todo lo
	// que puede vencer un pedido; el reloj se lee después, ya con el cliente bloqueado si hizo falta. La página se acota
	// acá (RF-8): negativa, la primera; después de la última, la última. El pendiente de arriba es el que la lista
	// muestra pendiente, sin volver a decidir por la hora: la fila y el recuadro siempre coinciden
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MisPedidosResponse misPedidos(Long idUsuario, int pagina) {
		vencerSiCorresponde(idUsuario);
		LocalDateTime ahora = LocalDateTime.now(clock);
		Page<FilaDePedido> filas = pedidoRepository.misPedidos(idUsuario,
				PageRequest.of(Busquedas.paginaValida(pagina, POR_PAGINA), POR_PAGINA));
		if (filas.isEmpty() && filas.getTotalPages() > 0) {
			filas = pedidoRepository.misPedidos(idUsuario, PageRequest.of(filas.getTotalPages() - 1, POR_PAGINA));
		}
		// RF-2: el que espera el pago (también uno con la hora pasada y el cobro sin consultar, HU-13 RF-7)
		PedidoResumenResponse pendiente = pedidoRepository
				.findByUsuario_IdUsuarioAndEstadoOrderByIdPedido(idUsuario, EstadoPedido.PENDIENTE_PAGO).stream()
				.findFirst().map(pedido -> PedidoResumenResponse.from(pedido, ahora)).orElse(null);
		return new MisPedidosResponse(pendiente,
				PaginaResponse.from(filas, fila -> PedidoResumenResponse.de(fila.idPedido(), fila.estado(),
						fila.confirmado(), fila.cantidadProductos(), fila.total(), fila.vence(), ahora)));
	}

	// Uno que ya pasó la hora no cuenta, aunque todavía no se haya vencido (las escrituras del carrito no vencen), salvo
	// que espere la consulta de su cobro: ese todavía no deja confirmar otro (HU-13 RF-7)
	@Transactional(readOnly = true)
	public Optional<Long> idDelPendiente(Long idUsuario) {
		return pendienteSinVencer(idUsuario, LocalDateTime.now(clock)).map(Pedido::getIdPedido);
	}

	private Optional<Pedido> pendienteSinVencer(Long idUsuario, LocalDateTime ahora) {
		return pedidoRepository.findByUsuario_IdUsuarioAndEstadoOrderByIdPedido(idUsuario, EstadoPedido.PENDIENTE_PAGO)
				.stream().filter(pedido -> !puedeVencer(pedido, ahora)).findFirst();
	}

	// Para PagoService: cliente y después pedido, como todo lo que cambia un pedido. 404 si es de otro; 409 si ya no
	// espera el pago (HU-13 RF-1, RF-14)
	@Transactional
	public Pedido bloquearPendiente(Long idUsuario, Long idPedido) {
		Pedido pedido = bloquear(idUsuario, idPedido);
		if (pedido.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
			throw new PedidoNoPendienteException();
		}
		return pedido;
	}

	// El cobro que se muestra y que decide si el pedido vence. Sin bloqueo, con el cliente ya bloqueado: un
	// FOR UPDATE sobre un pedido sin cobros bloquearía el hueco del índice (decisiones.md → Pedidos)
	public Optional<Cobro> cobroPendiente(Long idPedido) {
		return cobroRepository.findFirstByPedido_IdPedidoAndEstado(idPedido, EstadoCobro.PENDIENTE);
	}

	// Lee las líneas y el cobro: dentro de la transacción de quien la llama. El QR, solo mientras espera el pago
	public PedidoResponse respuesta(Pedido pedido, LocalDateTime ahora) {
		CobroResponse cobro = pedido.getEstado() != EstadoPedido.PENDIENTE_PAGO ? null
				: cobroPendiente(pedido.getIdPedido())
						.map(pendiente -> CobroResponse.from(pendiente, "simulador".equals(proveedor))).orElse(null);
		return PedidoResponse.from(pedido, retiro(), ahora, cobro);
	}

	// El pago tardío de un pedido vencido o cancelado (HU-13 RF-9), con el cliente y el pedido ya bloqueados
	// (ConciliacionDePagos). Todo o nada, como al confirmar: si cada producto sigue a la venta, con una cantidad que su
	// unidad admite y con stock, vuelve a reservar y sus cantidades salen del carrito; si no, queda para devolver
	@Transactional
	public void revivir(Pedido pedido, LocalDateTime fechaPago, LocalDateTime ahora) {
		Map<Long, Producto> productos = new LinkedHashMap<>();
		for (LineaPedido linea : pedido.lineasPorProducto()) {
			productos.put(linea.getIdProducto(), bloquearProducto(linea.getIdProducto()));
		}
		boolean alcanza = pedido.getLineas().stream().allMatch(linea -> {
			Producto producto = productos.get(linea.getIdProducto());
			return CarritoService.estado(producto, linea.getCantidad()) == EstadoLineaCarrito.DISPONIBLE
					&& linea.getCantidad().compareTo(producto.getStock()) <= 0;
		});
		if (!alcanza) {
			pedido.marcarPagoADevolver(fechaPago);
			return;
		}
		for (LineaPedido linea : pedido.lineasPorProducto()) {
			Producto producto = productos.get(linea.getIdProducto());
			BigDecimal nuevo = producto.getStock().subtract(linea.getCantidad());
			producto.aplicarMovimiento(nuevo);
			movimientoRepository.save(MovimientoStock.reserva(producto, linea.getCantidad(), nuevo, pedido, ahora));
		}
		pedido.pagar(fechaPago);
		restarDelCarrito(pedido);
	}

	// Vence si pasó la hora y, si tiene un cobro pendiente, el sistema de pago dijo después de la hora que no estaba
	// pagado (HU-13 RF-7). Una vista vieja del cobro solo puede hacer que no venza todavía: lo seguro
	boolean puedeVencer(Pedido pedido, LocalDateTime ahora) {
		if (pedido.getVence().isAfter(ahora)) {
			return false;
		}
		Optional<Cobro> cobro = cobroPendiente(pedido.getIdPedido());
		if (cobro.isEmpty()) {
			return true;
		}
		LocalDateTime consultado = cobro.get().getConsultadoSinPago();
		return consultado != null && !consultado.isBefore(pedido.getVence());
	}

	// Con el cliente ya bloqueado. Cada pedido libera sus productos por id ascendente
	private void vencerLosDelCliente(Long idUsuario, LocalDateTime ahora) {
		for (Pedido pedido : pendientesBloqueados(idUsuario)) {
			if (puedeVencer(pedido, ahora)) {
				liberar(pedido, EstadoPedido.VENCIDO, ahora);
			}
		}
	}

	// Uno como mucho (RF-5); la lista no lo da por supuesto
	private List<Pedido> pendientesBloqueados(Long idUsuario) {
		return pedidoRepository.findConBloqueoByUsuario_IdUsuarioAndEstadoOrderByIdPedido(idUsuario,
				EstadoPedido.PENDIENTE_PAGO);
	}

	// Cliente y después pedido: el mismo orden que confirmar. De otro cliente o inexistente, 404 (RF-13)
	private Pedido bloquear(Long idUsuario, Long idPedido) {
		usuarioRepository.findConBloqueoByIdUsuario(idUsuario).orElseThrow(PedidoNoEncontradoException::new);
		return pedidoRepository.findConBloqueoByIdPedidoAndUsuario_IdUsuario(idPedido, idUsuario)
				.orElseThrow(PedidoNoEncontradoException::new);
	}

	private Usuario bloquearCliente(Long idUsuario) {
		return usuarioRepository.findConBloqueoByIdUsuario(idUsuario).orElseThrow(PedidoNoEncontradoException::new);
	}

	private Producto bloquearProducto(Long idProducto) {
		return productoRepository.findConBloqueoByIdProducto(idProducto).orElseThrow();
	}

	// Devuelve el stock con los productos bloqueados por id ascendente, cierra el pedido y devuelve las líneas al
	// carrito (RF-9, RF-10, RF-11). Desde el 27/10/2026 una entrada no deja el estante (disponible y comprometido) por
	// encima del máximo, así que lo devuelto siempre entra; el tope queda para el stock cargado antes de esa regla:
	// pasarlo haría fallar el guardado y el pedido no se cerraría nunca. Cuenta las líneas que no volvieron (RF-16)
	private int liberar(Pedido pedido, EstadoPedido estadoFinal, LocalDateTime ahora) {
		for (LineaPedido linea : pedido.lineasPorProducto()) {
			Producto producto = bloquearProducto(linea.getIdProducto());
			// El máximo con los decimales que admite la unidad: por unidad, 9 999 999
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
		return devolverAlCarrito(pedido, ahora);
	}

	// Se suman a las que ya estén, con los límites del carrito: la línea queda en 999 como mucho y, con 50 líneas,
	// las que faltan no vuelven (RF-11, RF-16). Un producto que ya no se vende vuelve igual y el carrito lo marca
	private int devolverAlCarrito(Pedido pedido, LocalDateTime ahora) {
		// El carrito se lee una vez: con el cliente bloqueado, nadie más lo cambia
		Map<Long, LineaCarrito> carrito = new HashMap<>();
		lineaCarritoRepository.findByUsuario_IdUsuarioOrderByFechaAgregadaAscIdLineaAsc(
				pedido.getUsuario().getIdUsuario())
				.forEach(linea -> carrito.put(linea.getProducto().getIdProducto(), linea));
		int noVolvieron = 0;
		for (LineaPedido linea : pedido.getLineas()) {
			LineaCarrito existente = carrito.get(linea.getIdProducto());
			if (existente != null) {
				BigDecimal antes = existente.getCantidad();
				existente.cambiar(antes.add(linea.getCantidad()).min(CarritoService.MAXIMO_CANTIDAD));
				linea.volvioAlCarrito(existente.getCantidad().subtract(antes), existente.getIdLinea());
			} else if (carrito.size() < CarritoService.MAXIMO_LINEAS) {
				LineaCarrito nueva = lineaCarritoRepository.save(
						LineaCarrito.crear(pedido.getUsuario(), linea.getProducto(), linea.getCantidad(), ahora));
				carrito.put(linea.getIdProducto(), nueva);
				linea.volvioAlCarrito(linea.getCantidad(), nueva.getIdLinea());
			} else {
				noVolvieron++;
			}
		}
		return noVolvieron;
	}

	// A cada línea del carrito se le resta lo que el pedido devolvió al cancelar o vencer (no más: lo que no entró por
	// los topes no volvió); en cero o menos, se quita. Lo que el Cliente agregó aparte se queda (HU-13 RF-9), también
	// una línea que no es la que recibió lo devuelto: esa ya se quitó (revisión total). Con el cliente bloqueado, nadie
	// más cambia el carrito
	private void restarDelCarrito(Pedido pedido) {
		Map<Long, LineaCarrito> carrito = new HashMap<>();
		lineaCarritoRepository.findByUsuario_IdUsuarioOrderByFechaAgregadaAscIdLineaAsc(
				pedido.getUsuario().getIdUsuario())
				.forEach(linea -> carrito.put(linea.getProducto().getIdProducto(), linea));
		for (LineaPedido linea : pedido.getLineas()) {
			LineaCarrito existente = carrito.get(linea.getIdProducto());
			if (existente == null || linea.getCantidadDevuelta() == null
					|| (linea.getIdLineaCarrito() != null && !linea.getIdLineaCarrito().equals(existente.getIdLinea()))) {
				continue;
			}
			BigDecimal queda = existente.getCantidad().subtract(linea.getCantidadDevuelta());
			if (queda.signum() > 0) {
				existente.cambiar(queda);
			} else {
				lineaCarritoRepository.delete(existente);
			}
		}
	}

	// Lo mismo que marca el carrito, y además si pide más de lo que hay (RF-3)
	private static Optional<LineaRechazada> rechazo(LineaCarrito linea) {
		Producto producto = linea.getProducto();
		EstadoLineaCarrito estado = CarritoService.estado(producto, linea.getCantidad());
		if (estado != EstadoLineaCarrito.DISPONIBLE) {
			return Optional.of(new LineaRechazada(producto.getIdProducto(), Motivo.valueOf(estado.name())));
		}
		if (linea.getCantidad().compareTo(producto.getStock()) > 0) {
			return Optional.of(new LineaRechazada(producto.getIdProducto(), Motivo.NO_ALCANZA));
		}
		return Optional.empty();
	}
}
