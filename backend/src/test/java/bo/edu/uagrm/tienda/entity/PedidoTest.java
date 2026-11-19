package bo.edu.uagrm.tienda.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class PedidoTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 0);

	private final Usuario cliente = Usuario.registrarCliente("Ana", "Paz", "ana@mail.com", "$2a$10$hash", null, AHORA);
	private final Usuario empleado = Usuario.registrarCliente("Eva", "Rojas", "eva@mail.com", "$2a$10$hash", null,
			AHORA);

	private Pedido pendiente() {
		return Pedido.confirmar(cliente, "Ana Paz", "70000000", null, AHORA, AHORA.plusMinutes(30));
	}

	// HU-17 RF-6, RF-7: solo un pedido pagado se entrega, y queda quién y cuándo
	@Test
	void entregarSoloDesdePagado() {
		Pedido pedido = pendiente();
		assertThatThrownBy(() -> pedido.entregar(empleado, AHORA)).isInstanceOf(IllegalStateException.class);

		pedido.pagar(AHORA.plusMinutes(5));
		pedido.entregar(empleado, AHORA.plusHours(2));

		assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(pedido.getEntregado()).isEqualTo(AHORA.plusHours(2));
		assertThat(pedido.getEntregadoPor()).isSameAs(empleado);
		// HU-17 RF-8: una vez entregado, no se entrega otra vez
		assertThatThrownBy(() -> pedido.entregar(empleado, AHORA.plusHours(3)))
				.isInstanceOf(IllegalStateException.class);
	}

	// HU-17 RF-3: el nombre de quien retira se guarda también sin tildes ni mayúsculas, para buscarlo
	@Test
	void guardaElNombreDeQuienRetiraParaBuscarlo() {
		Pedido pedido = Pedido.confirmar(cliente, "  José   PÉREZ ", "70000000", null, AHORA, AHORA.plusMinutes(30));

		assertThat(pedido.getBusquedaRetira()).isEqualTo("jose perez");
	}

	// HU-23: una venta del mostrador, sin comprador (consumidor final) y a nombre de quien vende
	private Pedido ventaEnTienda(MedioPago medio, BigDecimal recibido) {
		return Pedido.enTienda(null, empleado, medio, recibido, AHORA, Duration.ofMinutes(10));
	}

	// HU-23 RF-6: cobrada en el mostrador queda entregada en el acto, con las fechas y quién vendió
	@Test
	void cobrarEnTiendaDejaEntregadoConLasFechasYQuienVendio() {
		Pedido venta = ventaEnTienda(MedioPago.EFECTIVO, new BigDecimal("100.00"));
		assertThat(venta.getCanal()).isEqualTo(Canal.TIENDA);
		assertThat(venta.getEstado()).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
		assertThat(venta.getVence()).isEqualTo(AHORA.plusMinutes(10));
		assertThat(venta.getUsuario()).isNull();
		assertThat(venta.getNombreRetira()).isNull();
		assertThat(venta.responsable()).isSameAs(empleado);

		venta.cobrarEnTienda(AHORA.plusSeconds(5));

		assertThat(venta.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(venta.getPagado()).isEqualTo(AHORA.plusSeconds(5));
		assertThat(venta.getEntregado()).isEqualTo(AHORA.plusSeconds(5));
		assertThat(venta.getEntregadoPor()).isSameAs(empleado);
		assertThat(venta.getVendidoPor()).isSameAs(empleado);
		// Ya cobrada, no se cobra otra vez
		assertThatThrownBy(() -> venta.cobrarEnTienda(AHORA.plusMinutes(1))).isInstanceOf(IllegalStateException.class);
	}

	// Un pedido en línea no se cobra en el mostrador, y su responsable es el cliente
	@Test
	void unPedidoEnLineaNoSeCobraEnTienda() {
		Pedido pedido = pendiente();

		assertThat(pedido.getCanal()).isEqualTo(Canal.EN_LINEA);
		assertThat(pedido.responsable()).isSameAs(cliente);
		assertThatThrownBy(() -> pedido.cobrarEnTienda(AHORA)).isInstanceOf(IllegalStateException.class);
	}

	// HU-23 RF-5: el cambio es lo recibido menos el total; con QR no hay cambio
	@Test
	void elCambioEsLoRecibidoMenosElTotal() {
		Pedido venta = ventaEnTienda(MedioPago.EFECTIVO, new BigDecimal("100.00"));
		venta.agregarLinea(producto("85.00"), new BigDecimal("1"), PrecioDeVenta.sinDescuento(producto("85.00").getPrecio()));
		venta.agregarLinea(producto("4.50"), new BigDecimal("2.50"), PrecioDeVenta.sinDescuento(producto("4.50").getPrecio()));

		assertThat(venta.getTotal()).isEqualByComparingTo("96.25");
		assertThat(venta.cambio()).isEqualByComparingTo("3.75");
		assertThat(ventaEnTienda(MedioPago.QR, null).cambio()).isNull();
	}

	// HU-23 RF-19, RF-20: solo se anula una venta en efectivo ya entregada, y queda quién, cuándo y por qué
	@Test
	void soloSeAnulaUnaVentaEnEfectivoEntregada() {
		Pedido venta = ventaEnTienda(MedioPago.EFECTIVO, new BigDecimal("50.00"));
		assertThatThrownBy(() -> venta.anular(empleado, "Error", AHORA)).isInstanceOf(IllegalStateException.class);
		venta.cobrarEnTienda(AHORA);

		venta.anular(cliente, "Producto equivocado", AHORA.plusMinutes(3));

		assertThat(venta.getEstado()).isEqualTo(EstadoPedido.ANULADO);
		assertThat(venta.getAnulado()).isEqualTo(AHORA.plusMinutes(3));
		assertThat(venta.getAnuladoPor()).isSameAs(cliente);
		assertThat(venta.getMotivoAnulacion()).isEqualTo("Producto equivocado");
		assertThatThrownBy(() -> venta.anular(cliente, "Otra vez", AHORA.plusMinutes(4)))
				.isInstanceOf(IllegalStateException.class);

		Pedido conQr = ventaEnTienda(MedioPago.QR, null);
		conQr.cobrarEnTienda(AHORA);
		assertThatThrownBy(() -> conQr.anular(cliente, "Error", AHORA)).isInstanceOf(IllegalStateException.class);

		Pedido enLinea = pendiente();
		enLinea.pagar(AHORA);
		enLinea.entregar(empleado, AHORA);
		assertThatThrownBy(() -> enLinea.anular(cliente, "Error", AHORA)).isInstanceOf(IllegalStateException.class);
	}

	private static Producto producto(String precio) {
		return Producto.crear("Producto " + precio, null, null, new BigDecimal(precio), UnidadVenta.METRO,
				Categoria.crear("Herramientas", null), java.util.List.of(), AHORA);
	}

	// --- HU-24: la línea guarda lo cobrado con descuento ---

	private static final java.time.LocalDate FIN = java.time.LocalDate.of(2026, 10, 31);

	private static Producto producto(String precio, UnidadVenta unidad) {
		return Producto.crear("Cable", null, null, new BigDecimal(precio), unidad, Categoria.crear("Electricidad", null),
				java.util.List.of(), AHORA);
	}

	// HU-24 RF-15
	@Test
	void lineaConDescuentoGuardaCobradoNormalPorcentajeYDescontado() {
		Producto martillo = producto("100.00", UnidadVenta.UNIDAD);
		Pedido pedido = pendiente();

		pedido.agregarLinea(martillo, new BigDecimal("2"), PrecioDeVenta.de(martillo.getPrecio(), 15, FIN));

		LineaPedido linea = pedido.getLineas().get(0);
		assertThat(linea.getPrecio()).isEqualByComparingTo("85.00");
		assertThat(linea.getSubtotal()).isEqualByComparingTo("170.00");
		assertThat(linea.getPrecioNormal()).isEqualByComparingTo("100.00");
		assertThat(linea.getDescuento()).isEqualTo(15);
		assertThat(linea.getDescontado()).isEqualByComparingTo("30.00");
	}

	// HU-24 RF-8: 4,50 al −15 % = 3,83; × 0,69 = 2,6427 → 2,64. El normal, 4,50 × 0,69 = 3,105 → 3,11: se ahorró 0,47
	@Test
	void subtotalConDescuentoYCantidadDecimal() {
		Producto cable = producto("4.50", UnidadVenta.METRO);
		Pedido pedido = pendiente();

		pedido.agregarLinea(cable, new BigDecimal("0.69"), PrecioDeVenta.de(cable.getPrecio(), 15, FIN));

		LineaPedido linea = pedido.getLineas().get(0);
		assertThat(linea.getSubtotal()).isEqualTo(new BigDecimal("2.64"));
		assertThat(linea.getDescontado()).isEqualTo(new BigDecimal("0.47"));
	}

	// /code-review: 0,50 al −2 % = 0,49; × 0,10 = 0,049 → 0,05, igual que el normal: sin ahorro, la línea no cuenta como
	// con descuento
	@Test
	void lineaSinAhorroPorElRedondeoNoCuentaComoConDescuento() {
		Producto cable = producto("0.50", UnidadVenta.METRO);
		Pedido pedido = pendiente();

		pedido.agregarLinea(cable, new BigDecimal("0.10"), PrecioDeVenta.de(cable.getPrecio(), 2, FIN));

		LineaPedido linea = pedido.getLineas().get(0);
		assertThat(linea.getSubtotal()).isEqualByComparingTo("0.05");
		assertThat(linea.getDescuento()).isNull();
		assertThat(linea.getDescontado()).isNull();
	}

	// HU-24 RF-15: sin descuento, como antes
	@Test
	void lineaSinDescuentoDejaNulos() {
		Producto martillo = producto("85.00", UnidadVenta.UNIDAD);
		Pedido pedido = pendiente();

		pedido.agregarLinea(martillo, new BigDecimal("1"), PrecioDeVenta.sinDescuento(martillo.getPrecio()));

		LineaPedido linea = pedido.getLineas().get(0);
		assertThat(linea.getPrecio()).isEqualByComparingTo("85.00");
		assertThat(linea.getPrecioNormal()).isNull();
		assertThat(linea.getDescuento()).isNull();
		assertThat(linea.getDescontado()).isNull();
	}

	// HU-24 RF-15: el total es la suma de lo cobrado
	@Test
	void totalSumaLosSubtotalesConDescuento() {
		Producto martillo = producto("100.00", UnidadVenta.UNIDAD);
		Producto cable = producto("4.50", UnidadVenta.METRO);
		Pedido pedido = pendiente();

		pedido.agregarLinea(martillo, new BigDecimal("1"), PrecioDeVenta.de(martillo.getPrecio(), 15, FIN));
		pedido.agregarLinea(cable, new BigDecimal("2.5"), PrecioDeVenta.sinDescuento(cable.getPrecio()));

		assertThat(pedido.getTotal()).isEqualByComparingTo("96.25");
	}
}
