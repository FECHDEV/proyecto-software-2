package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
