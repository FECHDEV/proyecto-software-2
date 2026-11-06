package bo.edu.uagrm.tienda;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.LineaPedido;
import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.pago.CobroGenerado;
import bo.edu.uagrm.tienda.service.pago.IPago;

// HU-23: una venta del mostrador guardada como la dejaría VentaTiendaService, para las pruebas de lo que la rodea
// (pedidos en línea, pago, «Mis pedidos»). Reserva el stock y, con QR, genera el cobro en el simulador. La venta en sí
// se prueba por su API en VentaTiendaIntegracionTest
record VentasDePrueba(PedidoRepository pedidos, ProductoRepository productos, UsuarioRepository usuarios,
		MovimientoStockRepository movimientos, CobroRepository cobros, IPago iPago, Clock clock,
		PlatformTransactionManager transacciones) {

	static final Duration VIGENCIA = Duration.ofMinutes(10);

	// Las cantidades por id de producto; el comprador, null para el consumidor final
	Long enEfectivo(String correoComprador, String correoVendedor, Map<Integer, String> lineas) {
		return guardar(correoComprador, correoVendedor, MedioPago.EFECTIVO, lineas);
	}

	Long conQr(String correoComprador, String correoVendedor, Map<Integer, String> lineas) {
		return guardar(correoComprador, correoVendedor, MedioPago.QR, lineas);
	}

	private Long guardar(String correoComprador, String correoVendedor, MedioPago medio, Map<Integer, String> lineas) {
		TransactionTemplate transaccion = new TransactionTemplate(transacciones);
		Pedido pedido = transaccion.execute(estado -> {
			LocalDateTime ahora = LocalDateTime.now(clock);
			Usuario comprador = correoComprador == null ? null : usuarios.findByCorreo(correoComprador).orElseThrow();
			Usuario vendedor = usuarios.findByCorreo(correoVendedor).orElseThrow();
			Pedido venta = Pedido.enTienda(comprador, vendedor, medio,
					medio == MedioPago.EFECTIVO ? new BigDecimal("999999.99") : null, ahora, VIGENCIA);
			lineas.forEach((id, cantidad) -> venta.agregarLinea(productos.findById(id.longValue()).orElseThrow(),
					new BigDecimal(cantidad)));
			pedidos.save(venta);
			for (LineaPedido linea : venta.lineasPorProducto()) {
				Producto producto = productos.findConBloqueoByIdProducto(linea.getIdProducto()).orElseThrow();
				BigDecimal nuevo = producto.getStock().subtract(linea.getCantidad());
				producto.aplicarMovimiento(nuevo);
				movimientos.save(MovimientoStock.reserva(producto, linea.getCantidad(), nuevo, venta, ahora));
			}
			if (medio == MedioPago.EFECTIVO) {
				venta.cobrarEnTienda(ahora);
			}
			return venta;
		});
		if (medio == MedioPago.QR) {
			CobroGenerado generado = iPago.generarCobro(pedido.getIdPedido(), pedido.getTotal(), "Venta de prueba");
			transaccion.executeWithoutResult(estado -> cobros.save(Cobro.generar(
					pedidos.findById(pedido.getIdPedido()).orElseThrow(), generado.referencia(), generado.codigoQr(),
					generado.urlPasarela(), LocalDateTime.now(clock))));
		}
		return pedido.getIdPedido();
	}
}
