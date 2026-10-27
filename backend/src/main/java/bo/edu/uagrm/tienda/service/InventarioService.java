package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.AjusteRequest;
import bo.edu.uagrm.tienda.dto.EntradaRequest;
import bo.edu.uagrm.tienda.dto.InventarioResponse;
import bo.edu.uagrm.tienda.dto.MinimoRequest;
import bo.edu.uagrm.tienda.dto.MovimientoResponse;
import bo.edu.uagrm.tienda.entity.MotivoAjuste;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.exception.AjusteSinCambioException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException.CampoInvalido;
import bo.edu.uagrm.tienda.exception.ConteoMenorQueComprometidoException;
import bo.edu.uagrm.tienda.exception.ProductoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.StockFueraDeRangoException;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository.Comprometido;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// Stock y movimientos (HU-08; decisiones.md → Inventario). Cada movimiento bloquea la fila del producto antes de
// leer el stock: dos a la vez se aplican uno después del otro y ninguno se pierde (RF-8)
@Service
@RequiredArgsConstructor
public class InventarioService {

	static final BigDecimal STOCK_MAXIMO = new BigDecimal("9999999.99");
	private static final Sort ORDEN_INVENTARIO = Sort.by("nombre", "idProducto");
	private static final Sort ORDEN_HISTORIAL = Sort.by(Sort.Direction.DESC, "fecha", "idMovimiento");

	private final ProductoRepository productoRepository;
	private final MovimientoStockRepository movimientoRepository;
	private final PedidoRepository pedidoRepository;
	private final UsuarioRepository usuarioRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Page<InventarioResponse> listar(String texto, Long idCategoria, Boolean activo, boolean soloBajo,
			int pagina, int tamano) {
		Page<Producto> productos = productoRepository.inventario(Busquedas.patron(texto), idCategoria, activo,
				soloBajo, Busquedas.pagina(pagina, tamano, ORDEN_INVENTARIO));
		// Lo comprometido de la página en una sola consulta (RF-16)
		Map<Long, BigDecimal> comprometidos = productos.isEmpty() ? Map.of()
				: pedidoRepository.comprometidos(productos.map(Producto::getIdProducto).toList()).stream()
						.collect(Collectors.toMap(Comprometido::idProducto, Comprometido::cantidad));
		return productos.map(producto -> InventarioResponse.from(producto,
				comprometidos.getOrDefault(producto.getIdProducto(), BigDecimal.ZERO)));
	}

	@Transactional(readOnly = true)
	public InventarioResponse obtener(Long idProducto) {
		return respuesta(productoRepository.findById(idProducto).orElseThrow(ProductoNoEncontradoException::new));
	}

	@Transactional(readOnly = true)
	public Page<MovimientoResponse> historial(Long idProducto, int pagina, int tamano) {
		if (!productoRepository.existsById(idProducto)) {
			throw new ProductoNoEncontradoException();
		}
		return movimientoRepository
				.findByProducto_IdProducto(idProducto, Busquedas.pagina(pagina, tamano, ORDEN_HISTORIAL))
				.map(MovimientoResponse::from);
	}

	// Lo que hay en el estante (disponible y comprometido) tampoco pasa del máximo: si no, lo contado ya no se podría
	// cargar en un ajuste, que admite hasta ese máximo (/code-review de la ampliación del 27/10/2026)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InventarioResponse registrarEntrada(Long idProducto, Long idUsuario, EntradaRequest solicitud) {
		Producto producto = bloquear(idProducto);
		exigirDecimales(producto.getUnidad(), solicitud.cantidad(), "cantidad");
		BigDecimal comprometido = comprometido(idProducto);
		BigDecimal nuevo = producto.getStock().add(solicitud.cantidad());
		if (nuevo.add(comprometido).compareTo(STOCK_MAXIMO) > 0) {
			throw new StockFueraDeRangoException();
		}
		producto.aplicarMovimiento(nuevo);
		movimientoRepository.save(MovimientoStock.entrada(producto, solicitud.cantidad(), nuevo,
				usuarioRepository.getReferenceById(idUsuario), LocalDateTime.now(clock), solicitud.nota()));
		return InventarioResponse.from(producto, comprometido);
	}

	// Se escribe lo contado en el estante; el disponible es eso menos lo comprometido en pedidos abiertos, y el
	// movimiento guarda la diferencia, lo contado y lo comprometido (RF-17). Con el producto bloqueado primero, como
	// confirmar, cancelar, vencer y el pago tardío, que cambian lo comprometido: no lo cambian hasta el commit. La
	// entrega no bloquea el producto (no cambia el disponible): el conteo vale para el momento en que se registra.
	// READ_COMMITTED, como todo lo que lee sin bloqueo después de bloquear: con REPEATABLE_READ, una lectura anterior
	// al bloqueo fijaría la vista y no vería las líneas del pedido que se confirmó mientras esperaba
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InventarioResponse registrarAjuste(Long idProducto, Long idUsuario, AjusteRequest solicitud) {
		Producto producto = bloquear(idProducto);
		exigirDecimales(producto.getUnidad(), solicitud.contada(), "contada");
		if (solicitud.motivo() == MotivoAjuste.OTRO && (solicitud.nota() == null || solicitud.nota().isBlank())) {
			throw new CamposInvalidosException(
					List.of(new CampoInvalido("nota", "es obligatoria con el motivo «otro»")));
		}
		BigDecimal comprometido = comprometido(idProducto);
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
				comprometido, solicitud.motivo(), usuarioRepository.getReferenceById(idUsuario),
				LocalDateTime.now(clock), solicitud.nota()));
		return InventarioResponse.from(producto, comprometido);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InventarioResponse cambiarMinimo(Long idProducto, MinimoRequest solicitud) {
		Producto producto = bloquear(idProducto);
		if (solicitud.minimo() != null) {
			exigirDecimales(producto.getUnidad(), solicitud.minimo(), "minimo");
		}
		producto.cambiarMinimo(solicitud.minimo());
		return respuesta(producto);
	}

	private InventarioResponse respuesta(Producto producto) {
		return InventarioResponse.from(producto, comprometido(producto.getIdProducto()));
	}

	// Con la misma consulta que la lista: qué pedidos cuentan está en un solo lugar (RF-15)
	private BigDecimal comprometido(Long idProducto) {
		return pedidoRepository.comprometidos(List.of(idProducto)).stream().findFirst().map(Comprometido::cantidad)
				.orElse(BigDecimal.ZERO);
	}

	// «2,00» en una unidad entera es 2: cuentan los decimales que no son cero
	static boolean admiteDecimales(UnidadVenta unidad, BigDecimal cantidad) {
		return cantidad.stripTrailingZeros().scale() <= unidad.decimales();
	}

	private static void exigirDecimales(UnidadVenta unidad, BigDecimal cantidad, String campo) {
		if (!admiteDecimales(unidad, cantidad)) {
			throw new CamposInvalidosException(List.of(new CampoInvalido(campo, mensajeDecimales(unidad))));
		}
	}

	static String mensajeDecimales(UnidadVenta unidad) {
		return unidad.decimales() == 0 ? "tiene que ser un número entero" : "admite hasta dos decimales";
	}

	private Producto bloquear(Long idProducto) {
		return productoRepository.findConBloqueoByIdProducto(idProducto)
				.orElseThrow(ProductoNoEncontradoException::new);
	}
}
