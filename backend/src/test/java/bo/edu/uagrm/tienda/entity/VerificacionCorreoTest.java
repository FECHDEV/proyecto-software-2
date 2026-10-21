package bo.edu.uagrm.tienda.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// HU-20 RF-3: el enlace vale 24 horas y una sola vez
class VerificacionCorreoTest {

	private static final LocalDateTime CREADO = LocalDateTime.of(2026, 10, 21, 10, 0);

	private final VerificacionCorreo enlace = VerificacionCorreo.crear(
			Usuario.registrarCliente("Ana", "Paz", "ana@mail.com", "$2a$10$hash", null, CREADO), "hash", CREADO);

	@Test
	void sirveHastaUnSegundoAntesDeLas24Horas() {
		assertThat(enlace.estaVigente(CREADO.plusHours(24).minusSeconds(1))).isTrue();
	}

	@Test
	void aLas24HorasYaNoSirve() {
		assertThat(enlace.estaVigente(CREADO.plusHours(24))).isFalse();
	}

	@Test
	void usadoYaNoSirve() {
		enlace.marcarUsada();

		assertThat(enlace.estaVigente(CREADO)).isFalse();
	}
}
