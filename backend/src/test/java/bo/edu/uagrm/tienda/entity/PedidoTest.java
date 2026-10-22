package bo.edu.uagrm.tienda.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
