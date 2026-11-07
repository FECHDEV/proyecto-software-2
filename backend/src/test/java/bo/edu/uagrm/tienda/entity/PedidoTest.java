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
		venta.agregarLinea(producto("85.00"), new BigDecimal("1"));
		venta.agregarLinea(producto("4.50"), new BigDecimal("2.50"));

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
}
