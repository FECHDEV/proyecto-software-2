package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import bo.edu.uagrm.tienda.exception.SolicitudesExcedidasException;

class LimiteSolicitudesRecuperacionTest {

	private static final String CORREO = "ana@mail.com";
	private static final String IP = "10.0.0.1";

	private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-09-15T15:00:00Z"));
	private final LimiteSolicitudesRecuperacion limite = new LimiteSolicitudesRecuperacion(reloj);

	private void solicitar(int veces) {
		for (int i = 0; i < veces; i++) {
			limite.registrarIntento(CORREO, IP);
		}
	}

	@Test
	void tresSolicitudesSePermiten() {
		solicitar(2);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	@Test
	void cuartaSolicitudSeRechazaDuranteQuinceMinutos() {
		solicitar(3);

		assertThatThrownBy(() -> limite.registrarIntento(CORREO, IP))
				.isInstanceOfSatisfying(SolicitudesExcedidasException.class, ex -> {
					assertThat(ex.getCodigo()).isEqualTo("SOLICITUDES_EXCEDIDAS");
					assertThat(ex.getEstadoHttp()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
				});
		reloj.avanzar(Duration.ofMinutes(15).minusSeconds(1));
		assertThatThrownBy(() -> limite.registrarIntento(CORREO, IP)).isInstanceOf(SolicitudesExcedidasException.class);
		reloj.avanzar(Duration.ofSeconds(1));
		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	@Test
	void otroCorreoUOtraIpNoQuedanBloqueados() {
		solicitar(3);

		assertThatCode(() -> limite.registrarIntento(CORREO, "10.0.0.2")).doesNotThrowAnyException();
		assertThatCode(() -> limite.registrarIntento("beto@mail.com", IP)).doesNotThrowAnyException();
	}

	@Test
	void solicitudesFueraDeLaVentanaDeQuinceMinutosNoSeAcumulan() {
		solicitar(2);
		reloj.avanzar(Duration.ofMinutes(15));
		solicitar(2);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	private static final class RelojDePrueba extends Clock {

		private Instant ahora;

		RelojDePrueba(Instant inicio) {
			this.ahora = inicio;
		}

		void avanzar(Duration tiempo) {
			ahora = ahora.plus(tiempo);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("America/La_Paz");
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

		@Override
		public Instant instant() {
			return ahora;
		}
	}
}
