package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.reportes.CategoriaFila;
import bo.edu.uagrm.tienda.dto.reportes.ClienteInactivo;
import bo.edu.uagrm.tienda.dto.reportes.CompraFila;
import bo.edu.uagrm.tienda.dto.reportes.EstadoFila;
import bo.edu.uagrm.tienda.dto.reportes.LineasFila;
import bo.edu.uagrm.tienda.dto.reportes.PerdidoFila;
import bo.edu.uagrm.tienda.dto.reportes.ProductoPerdido;
import bo.edu.uagrm.tienda.dto.reportes.ProductoSinVenta;
import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
import bo.edu.uagrm.tienda.dto.reportes.ResumenDePerdidos;
import bo.edu.uagrm.tienda.dto.reportes.UltimaVentaFila;
import bo.edu.uagrm.tienda.dto.reportes.VentaFila;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorCategoria;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorDiaSemana;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorFecha;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorHora;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorTipo;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.Totales;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException.CampoInvalido;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.ReportesRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// Los reportes del Contador y el Administrador (HU-18), de solo lectura y sin bloqueos: un reporte no necesita la foto
// exacta de un instante. Venta es un pedido Pagado o Entregado, en la fecha del pago (RF-4). Las listas se ordenan y se
// paginan acá, de a 10: una tienda de un local tiene cientos o pocos miles de productos y clientes
@Service
@RequiredArgsConstructor
public class ReportesService {

	static final Set<EstadoPedido> VENDIDOS = EnumSet.of(EstadoPedido.PAGADO, EstadoPedido.ENTREGADO);
	private static final Set<EstadoPedido> A_DEVOLVER = EnumSet.of(EstadoPedido.PAGO_A_DEVOLVER);
	private static final Set<EstadoPedido> PERDIDOS = EnumSet.of(EstadoPedido.VENCIDO, EstadoPedido.CANCELADO);
	private static final BigDecimal CIEN = BigDecimal.valueOf(100);
	private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);
	static final int POR_PAGINA = 10;
	private static final List<Integer> DIAS = List.of(30, 60, 90);

	private final ReportesRepository reportesRepository;
	private final ProductoRepository productoRepository;
	private final UsuarioRepository usuarioRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public VentasResponse ventas(String desde, String hasta, TipoCliente tipo) {
		Periodo periodo = Periodo.de(desde, hasta, LocalDate.now(clock));
		Periodo anterior = periodo.anterior();
		List<VentaFila> ventas = pagados(periodo, tipo);
		List<VentaFila> todosLosTipos = tipo == null ? ventas : pagados(periodo, null);
		return new VentasResponse(
				new VentasResponse.PeriodoResponse(periodo.desde(), periodo.hasta(), anterior.desde(), anterior.hasta(),
						periodo.porMes() ? "MES" : "DIA"),
				totales(periodo, tipo, ventas), totales(anterior, tipo, pagados(anterior, tipo)),
				porFecha(periodo, ventas), porDiaSemana(ventas), porHora(ventas), porCategoria(periodo, tipo),
				porTipo(todosLosTipos));
	}

	// RF-11: los vendidos, inactivos incluidos, de mayor a menor importe. RF-12 (menos): los activos, con lo que
	// vendieron o 0, de menor a mayor. A igual importe, por nombre
	@Transactional(readOnly = true)
	public PaginaResponse<ProductoVendido> productos(String desde, String hasta, TipoCliente tipo, boolean menos,
			int pagina) {
		Periodo periodo = Periodo.de(desde, hasta, LocalDate.now(clock));
		Map<Long, LineasFila> vendidos = reportesRepository
				.lineasPorProducto(VENDIDOS, periodo.inicio(), periodo.fin(), tipo).stream()
				.collect(Collectors.toMap(LineasFila::idProducto, Function.identity()));
		List<Producto> productos = menos ? productoRepository.findByActivoTrue()
				: productoRepository.findAllById(vendidos.keySet());
		Comparator<ProductoVendido> porImporte = Comparator.comparing(ProductoVendido::importe);
		List<ProductoVendido> filas = productos.stream()
				.map(producto -> vendido(producto, vendidos.get(producto.getIdProducto())))
				.sorted((menos ? porImporte : porImporte.reversed()).thenComparing(ProductoVendido::nombre)
						.thenComparing(ProductoVendido::idProducto))
				.toList();
		return paginar(filas, pagina);
	}

	// RF-13: activos con stock, por días desde la última venta, o desde el alta si nunca se vendió. No depende del
	// período ni del tipo
	@Transactional(readOnly = true)
	public PaginaResponse<ProductoSinVenta> sinVenta(String dias, int pagina) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		int minimo = dias(dias);
		Map<Long, LocalDateTime> ultimas = reportesRepository.ultimasVentas(VENDIDOS).stream()
				.collect(Collectors.toMap(UltimaVentaFila::idProducto, UltimaVentaFila::ultima));
		Function<Producto, LocalDateTime> desde = producto -> ultimas.getOrDefault(producto.getIdProducto(),
				producto.getFechaAlta());
		List<ProductoSinVenta> filas = productoRepository.findByActivoTrue().stream()
				// En días enteros, los mismos que se muestran: 30 días y 5 horas no son «más de 30 días»
				.filter(producto -> producto.getStock().signum() > 0
						&& ChronoUnit.DAYS.between(desde.apply(producto), ahora) > minimo)
				.sorted(Comparator.comparing(desde).thenComparing(Producto::getIdProducto))
				.map(producto -> sinVenta(producto, ultimas.get(producto.getIdProducto()), ahora)).toList();
		return paginar(filas, pagina);
	}

	// RF-16: clientes activos que compraron alguna vez y no desde hace más de los días pedidos, del mayor total al
	// menor
	@Transactional(readOnly = true)
	public PaginaResponse<ClienteInactivo> clientesInactivos(String dias, TipoCliente tipo, int pagina) {
		// En días enteros, como «Sin venderse»: más de 30 días enteros es 31 o más
		LocalDateTime limite = LocalDateTime.now(clock).minusDays(dias(dias) + 1L);
		List<CompraFila> compras = reportesRepository
				.comprasHasta(VENDIDOS, Rol.CLIENTE, EstadoCuenta.ACTIVA, tipo, limite).stream()
				.sorted(Comparator.comparing(CompraFila::total).reversed().thenComparing(CompraFila::idUsuario))
				.toList();
		PaginaResponse<CompraFila> deLaPagina = paginar(compras, pagina);
		Map<Long, Usuario> usuarios = usuarioRepository
				.findAllById(deLaPagina.contenido().stream().map(CompraFila::idUsuario).toList()).stream()
				.collect(Collectors.toMap(Usuario::getIdUsuario, Function.identity()));
		return new PaginaResponse<>(deLaPagina.contenido().stream().map(compra -> {
			Usuario usuario = usuarios.get(compra.idUsuario());
			return new ClienteInactivo(usuario.getIdUsuario(), usuario.getNombre(), usuario.getApellido(),
					usuario.getCorreo(), usuario.getTelefono(), usuario.getTipoCliente(), compra.ultima(),
					compra.pedidos(), compra.total().setScale(2));
		}).toList(), deLaPagina.pagina(), deLaPagina.tamano(), deLaPagina.totalElementos(),
				deLaPagina.totalPaginas());
	}

	// RF-18: por importe perdido, de mayor a menor
	@Transactional(readOnly = true)
	public PaginaResponse<ProductoPerdido> perdidos(String desde, String hasta, TipoCliente tipo, int pagina) {
		Periodo periodo = Periodo.de(desde, hasta, LocalDate.now(clock));
		Map<Long, PerdidoFila> perdidos = reportesRepository
				.perdidosPorProducto(PERDIDOS, periodo.inicio(), periodo.fin(), tipo).stream()
				.collect(Collectors.toMap(PerdidoFila::idProducto, Function.identity()));
		List<ProductoPerdido> filas = productoRepository.findAllById(perdidos.keySet()).stream().map(producto -> {
			PerdidoFila fila = perdidos.get(producto.getIdProducto());
			return new ProductoPerdido(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
					producto.getUnidad(), fila.cantidad(), fila.importe().setScale(2));
		}).sorted(Comparator.comparing(ProductoPerdido::importe).reversed().thenComparing(ProductoPerdido::nombre)
				.thenComparing(ProductoPerdido::idProducto)).toList();
		return paginar(filas, pagina);
	}

	private static ProductoVendido vendido(Producto producto, LineasFila fila) {
		if (fila == null) {
			return new ProductoVendido(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
					producto.getUnidad(), producto.isActivo(), BigDecimal.ZERO.setScale(2), CERO, null, CERO);
		}
		return new ProductoVendido(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
				producto.getUnidad(), producto.isActivo(), fila.cantidad(), fila.importe().setScale(2),
				margen(fila.margen(), fila.importe().subtract(fila.sinCosto())), fila.sinCosto().setScale(2));
	}

	private static ProductoSinVenta sinVenta(Producto producto, LocalDateTime ultimaVenta, LocalDateTime ahora) {
		LocalDateTime desde = ultimaVenta == null ? producto.getFechaAlta() : ultimaVenta;
		boolean aPrecio = producto.getCosto() == null;
		BigDecimal inmovilizado = producto.getStock().multiply(aPrecio ? producto.getPrecio() : producto.getCosto())
				.setScale(2, RoundingMode.HALF_UP);
		return new ProductoSinVenta(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
				producto.getUnidad(), producto.getStock(), producto.getPrecio(), producto.getCosto(), ultimaVenta,
				ChronoUnit.DAYS.between(desde, ahora), inmovilizado, aPrecio);
	}

	// 30, 60 o 90; sin decirlo, 30 (RF-13, RF-16)
	private static int dias(String dias) {
		if (dias == null) {
			return DIAS.getFirst();
		}
		return DIAS.stream().filter(valor -> String.valueOf(valor).equals(dias.strip())).findFirst()
				.orElseThrow(() -> new CamposInvalidosException(
						List.of(new CampoInvalido("dias", "tiene que ser 30, 60 o 90"))));
	}

	// Después de la última página, la última, como en las listas de pedidos (HU-17 RF-12)
	static <T> PaginaResponse<T> paginar(List<T> filas, int pagina) {
		int paginas = (filas.size() + POR_PAGINA - 1) / POR_PAGINA;
		int numero = paginas == 0 ? 0 : Math.min(Math.max(pagina, 0), paginas - 1);
		int desde = numero * POR_PAGINA;
		return new PaginaResponse<>(List.copyOf(filas.subList(desde, Math.min(desde + POR_PAGINA, filas.size()))),
				numero, POR_PAGINA, filas.size(), paginas);
	}

	private List<VentaFila> pagados(Periodo periodo, TipoCliente tipo) {
		return reportesRepository.pagados(VENDIDOS, periodo.inicio(), periodo.fin(), tipo);
	}

	// RF-6 a RF-8
	private Totales totales(Periodo periodo, TipoCliente tipo, List<VentaFila> ventas) {
		BigDecimal ingresos = sumar(ventas, VentaFila::total);
		List<LineasFila> lineas = reportesRepository.lineasPorProducto(VENDIDOS, periodo.inicio(), periodo.fin(), tipo);
		BigDecimal sinCosto = sumar(lineas, LineasFila::sinCosto);
		BigDecimal conCosto = sumar(lineas, LineasFila::importe).subtract(sinCosto);
		BigDecimal margen = margen(sumar(lineas, LineasFila::margen), conCosto);
		List<VentaFila> devoluciones = reportesRepository.pagados(A_DEVOLVER, periodo.inicio(), periodo.fin(), tipo);
		return new Totales(ingresos, ventas.size(), promedio(ingresos, ventas.size()), margen,
				margen == null ? null : porcentaje(margen, conCosto), sinCosto, devoluciones.size(),
				sumar(devoluciones, VentaFila::total));
	}

	// Todos los días (o meses) del período, también los que no vendieron (RF-9)
	private static List<PorFecha> porFecha(Periodo periodo, List<VentaFila> ventas) {
		Map<String, Acumulado> grupos = new LinkedHashMap<>();
		if (periodo.porMes()) {
			for (YearMonth mes = YearMonth.from(periodo.desde()); !mes.isAfter(YearMonth.from(periodo.hasta())); mes = mes
					.plusMonths(1)) {
				grupos.put(mes.toString(), new Acumulado());
			}
			ventas.forEach(venta -> grupos.get(YearMonth.from(venta.pagado()).toString()).sumar(venta));
		} else {
			for (LocalDate dia = periodo.desde(); !dia.isAfter(periodo.hasta()); dia = dia.plusDays(1)) {
				grupos.put(dia.toString(), new Acumulado());
			}
			ventas.forEach(venta -> grupos.get(venta.pagado().toLocalDate().toString()).sumar(venta));
		}
		List<PorFecha> resultado = new ArrayList<>();
		grupos.forEach((fecha, acumulado) -> resultado.add(new PorFecha(fecha, acumulado.ingresos, acumulado.pedidos)));
		return resultado;
	}

	// RF-10: de lunes a domingo
	private static List<PorDiaSemana> porDiaSemana(List<VentaFila> ventas) {
		Acumulado[] dias = acumular(7, ventas, venta -> venta.pagado().getDayOfWeek().getValue() - 1);
		List<PorDiaSemana> resultado = new ArrayList<>();
		for (DayOfWeek dia : DayOfWeek.values()) {
			Acumulado acumulado = dias[dia.getValue() - 1];
			resultado.add(new PorDiaSemana(dia.getValue(), acumulado.ingresos, acumulado.pedidos));
		}
		return resultado;
	}

	// RF-10: en la hora guardada, que es la de Bolivia
	private static List<PorHora> porHora(List<VentaFila> ventas) {
		Acumulado[] horas = acumular(24, ventas, venta -> venta.pagado().getHour());
		List<PorHora> resultado = new ArrayList<>();
		for (int hora = 0; hora < 24; hora++) {
			resultado.add(new PorHora(hora, horas[hora].ingresos, horas[hora].pedidos));
		}
		return resultado;
	}

	// RF-14
	private List<PorCategoria> porCategoria(Periodo periodo, TipoCliente tipo) {
		List<CategoriaFila> filas = reportesRepository.porCategoria(VENDIDOS, periodo.inicio(), periodo.fin(), tipo);
		return filas.stream()
				.map(fila -> new PorCategoria(fila.categoria(), fila.importe().setScale(2), fila.pedidos(),
						margen(fila.margen(), fila.importe().subtract(fila.sinCosto()))))
				.toList();
	}

	// RF-15: los cuatro tipos, en el orden del enum, también sin ventas
	private static List<PorTipo> porTipo(List<VentaFila> ventas) {
		Acumulado[] tipos = acumular(TipoCliente.values().length, ventas, venta -> venta.tipo().ordinal());
		List<PorTipo> resultado = new ArrayList<>();
		for (TipoCliente tipo : TipoCliente.values()) {
			Acumulado acumulado = tipos[tipo.ordinal()];
			resultado.add(new PorTipo(tipo, acumulado.ingresos, acumulado.pedidos,
					promedio(acumulado.ingresos, acumulado.pedidos)));
		}
		return resultado;
	}

	// RF-17, solo las cifras de los perdidos: la sección de perdidos no necesita el resumen entero
	@Transactional(readOnly = true)
	public ResumenDePerdidos resumenDePerdidos(String desde, String hasta, TipoCliente tipo) {
		return perdidos(Periodo.de(desde, hasta, LocalDate.now(clock)), tipo);
	}

	// RF-17: los que esperan el pago todavía no se vendieron ni se perdieron, y un pago a devolver se pagó pero no
	// se vendió (se devuelve fuera del sistema): ninguno de los dos cuenta en la proporción
	private ResumenDePerdidos perdidos(Periodo periodo, TipoCliente tipo) {
		long confirmados = 0;
		long vendidos = 0;
		long perdidos = 0;
		BigDecimal importe = CERO;
		for (EstadoFila fila : reportesRepository.confirmadosPorEstado(periodo.inicio(), periodo.fin(), tipo)) {
			if (fila.estado() == EstadoPedido.PENDIENTE_PAGO || fila.estado() == EstadoPedido.PAGO_A_DEVOLVER) {
				continue;
			}
			confirmados += fila.pedidos();
			if (VENDIDOS.contains(fila.estado())) {
				vendidos += fila.pedidos();
			} else if (PERDIDOS.contains(fila.estado())) {
				perdidos += fila.pedidos();
				importe = importe.add(fila.importe());
			}
		}
		BigDecimal proporcion = confirmados == 0 ? null
				: BigDecimal.valueOf(vendidos).multiply(CIEN).divide(BigDecimal.valueOf(confirmados), 1,
						RoundingMode.HALF_UP);
		return new ResumenDePerdidos(perdidos, importe, confirmados, vendidos, proporcion);
	}

	// Nulo si nada vendido tenía costo (RF-8)
	private static BigDecimal margen(BigDecimal margen, BigDecimal conCosto) {
		return conCosto.signum() == 0 ? null : margen.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
		return parte.multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
	}

	private static BigDecimal promedio(BigDecimal ingresos, long pedidos) {
		return pedidos == 0 ? null : ingresos.divide(BigDecimal.valueOf(pedidos), 2, RoundingMode.HALF_UP);
	}

	private static <T> BigDecimal sumar(List<T> filas, Function<T, BigDecimal> valor) {
		return filas.stream().map(valor).reduce(CERO, BigDecimal::add);
	}

	private static Acumulado[] acumular(int grupos, List<VentaFila> ventas, Function<VentaFila, Integer> grupo) {
		Acumulado[] acumulados = new Acumulado[grupos];
		for (int i = 0; i < grupos; i++) {
			acumulados[i] = new Acumulado();
		}
		ventas.forEach(venta -> acumulados[grupo.apply(venta)].sumar(venta));
		return acumulados;
	}

	private static final class Acumulado {

		private BigDecimal ingresos = CERO;
		private long pedidos;

		void sumar(VentaFila venta) {
			ingresos = ingresos.add(venta.total());
			pedidos++;
		}
	}
}
