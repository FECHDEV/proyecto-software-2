package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import bo.edu.uagrm.tienda.exception.IntentosExcedidosException;

class LimiteIntentosTest {

	private static final String CORREO = "ana@mail.com";
	private static final String IP = "10.0.0.1";

	private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-09-14T15:00:00Z"));
	private final LimiteIntentos limite = new LimiteIntentos(reloj);

	// Intentos que terminan en credenciales incorrectas: se registran y no se anulan
	private void fallar(int veces, String correo, String ip) {
		for (int i = 0; i < veces; i++) {
			limite.registrarIntento(correo, ip);
		}
	}

	// HU-02 RF-5
	@Test
	void cuatroFallosTodaviaPermitenElQuintoIntento() {
		fallar(4, CORREO, IP);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	// HU-02 RF-5
	@Test
	void quintoFalloBloqueaEseCorreoDesdeEsaIpDuranteQuinceMinutos() {
		fallar(5, CORREO, IP);

		assertThatThrownBy(() -> limite.registrarIntento(CORREO, IP)).isInstanceOf(IntentosExcedidosException.class);
		reloj.avanzar(Duration.ofMinutes(15).minusSeconds(1));
		assertThatThrownBy(() -> limite.registrarIntento(CORREO, IP)).isInstanceOf(IntentosExcedidosException.class);
		reloj.avanzar(Duration.ofSeconds(1));
		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	// HU-02 RF-5
	@Test
	void otroCorreoUOtraIpNoQuedanBloqueados() {
		fallar(5, CORREO, IP);

		assertThatCode(() -> limite.registrarIntento(CORREO, "10.0.0.2")).doesNotThrowAnyException();
		assertThatCode(() -> limite.registrarIntento("beto@mail.com", IP)).doesNotThrowAnyException();
	}

	// HU-02 RF-5
	@Test
	void fallosFueraDeLaVentanaDeQuinceMinutosNoSeAcumulan() {
		fallar(4, CORREO, IP);
		reloj.avanzar(Duration.ofMinutes(15));
		fallar(1, CORREO, IP);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	// HU-02 RF-5
	@Test
	void cincoFallosDentroDeCualquierVentanaDeQuinceMinutosBloquean() {
		fallar(1, CORREO, IP);
		reloj.avanzar(Duration.ofMinutes(14).plusSeconds(50));
		fallar(3, CORREO, IP);
		reloj.avanzar(Duration.ofSeconds(11));
		fallar(2, CORREO, IP);

		// Los últimos 5 fallos ocurrieron en 11 segundos, aunque el primero ya salió de la ventana
		assertThatThrownBy(() -> limite.registrarIntento(CORREO, IP)).isInstanceOf(IntentosExcedidosException.class);
	}

	// HU-02 RF-5
	@Test
	void intentoAnuladoNoCuenta() {
		fallar(4, CORREO, IP);
		for (int i = 0; i < 3; i++) {
			limite.registrarIntento(CORREO, IP);
			limite.anularIntento(CORREO, IP);
		}

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limite.registrarIntento(CORREO, IP)).isInstanceOf(IntentosExcedidosException.class);
	}

	// HU-02 RF-6
	@Test
	void inicioDeSesionExitosoReiniciaElConteo() {
		fallar(4, CORREO, IP);
		limite.registrarExito(CORREO, IP);
		fallar(4, CORREO, IP);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	// HU-02 RF-5
	@Test
	void terminadoElBloqueoEmpiezaUnConteoNuevo() {
		fallar(5, CORREO, IP);
		reloj.avanzar(Duration.ofMinutes(15));
		fallar(1, CORREO, IP);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
	}

	@Test
	void reiniciarCorreoQuitaElBloqueoDeEseCorreoDesdeTodasLasIpsSinTocarOtrosCorreos() {
		fallar(5, CORREO, IP);
		fallar(5, CORREO, "10.0.0.2");
		fallar(5, "beto@mail.com", IP);

		limite.reiniciarCorreo(CORREO);

		assertThatCode(() -> limite.registrarIntento(CORREO, IP)).doesNotThrowAnyException();
		assertThatCode(() -> limite.registrarIntento(CORREO, "10.0.0.2")).doesNotThrowAnyException();
		assertThatThrownBy(() -> limite.registrarIntento("beto@mail.com", IP))
				.isInstanceOf(IntentosExcedidosException.class);
	}

	@Test
	void registrosSinEfectoSeEliminanAlLimpiarLaMemoria() {
		for (int i = 0; i < 100; i++) {
			limite.registrarIntento("correo" + i + "@mail.com", IP);
		}
		reloj.avanzar(Duration.ofMinutes(16));

		limite.registrarIntento(CORREO, IP);

		assertThat(limite.registrosEnMemoria()).isEqualTo(1);
	}

	@Test
	void laMemoriaSeLimpiaComoMuchoUnaVezPorMinuto() {
		limite.registrarIntento("a@mail.com", IP);
		reloj.avanzar(Duration.ofMinutes(14).plusSeconds(30));
		limite.registrarIntento("b@mail.com", IP);
		reloj.avanzar(Duration.ofSeconds(40));

		// El intento de a@mail.com ya salió de la ventana, pero pasaron solo 40 segundos desde la última limpieza
		limite.registrarIntento("c@mail.com", IP);
		assertThat(limite.registrosEnMemoria()).isEqualTo(3);

		reloj.avanzar(Duration.ofSeconds(21));
		limite.registrarIntento("d@mail.com", IP);
		assertThat(limite.registrosEnMemoria()).isEqualTo(3);
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
