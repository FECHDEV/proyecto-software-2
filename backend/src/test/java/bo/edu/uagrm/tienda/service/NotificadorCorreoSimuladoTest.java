package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoSimuladoTest {

	@Test
	void escribeEnElLogElDestinatarioYElToken(CapturedOutput salida) {
		NotificadorCorreo notificador = new NotificadorCorreoSimulado();

		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		assertThat(salida.getOut()).contains("ana@mail.com").contains("token-de-prueba");
	}

	// HU-20 RF-1
	@Test
	void escribeEnElLogLaVerificacionConSuToken(CapturedOutput salida) {
		new NotificadorCorreoSimulado().enviarVerificacion("ana@mail.com", "Ana", "token-de-verificacion");

		assertThat(salida.getOut()).contains("Verificación del correo").contains("ana@mail.com")
				.contains("token-de-verificacion");
	}

	// HU-21: el número, el destinatario y el total; sin el teléfono de quien retira
	@Test
	void losCorreosDelPedidoQuedanEnElLogSinElTelefono(CapturedOutput salida) {
		CorreoDelPedido pedido = new CorreoDelPedido("ana@mail.com", "Ana", 12L, LocalDateTime.of(2026, 10, 26, 14, 5),
				List.of(), new BigDecimal("215.50"), "Ana Pérez", "70012345");

		new NotificadorCorreoSimulado().enviarPedidoPagado(pedido);
		new NotificadorCorreoSimulado().enviarPagoADevolver(pedido);

		assertThat(salida.getOut()).contains("Pedido pagado N.º 12", "Pago a devolver N.º 12", "ana@mail.com",
				"215.50").doesNotContain("70012345");
	}
}
