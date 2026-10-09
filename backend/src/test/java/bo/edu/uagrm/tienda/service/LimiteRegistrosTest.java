package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import bo.edu.uagrm.tienda.exception.RegistrosExcedidosException;

// HU-01 RF-13: 5 cuentas por IP en una hora
class LimiteRegistrosTest {

	private static final String IP = "10.0.0.1";

	private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-10-09T15:00:00Z"));
	private final LimiteRegistros limite = new LimiteRegistros(reloj);

	private void registrar(int veces) {
		for (int i = 0; i < veces; i++) {
			limite.registrarIntento(IP);
		}
	}

	@Test
	void cincoRegistrosDesdeUnaIpBloqueanElSexto() {
		registrar(4);
		assertThatCode(() -> limite.registrarIntento(IP)).doesNotThrowAnyException();

		assertThatThrownBy(() -> limite.registrarIntento(IP))
				.isInstanceOfSatisfying(RegistrosExcedidosException.class, ex -> {
					assertThat(ex.getCodigo()).isEqualTo("REGISTROS_EXCEDIDOS");
					assertThat(ex.getEstadoHttp()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
				});
	}

	@Test
	void otraIpNoQuedaBloqueada() {
		registrar(5);

		assertThatCode(() -> limite.registrarIntento("10.0.0.2")).doesNotThrowAnyException();
	}

	@Test
	void pasadaLaHoraSePuedeVolverARegistrar() {
		registrar(5);

		reloj.avanzar(Duration.ofHours(1).minusSeconds(1));
		assertThatThrownBy(() -> limite.registrarIntento(IP)).isInstanceOf(RegistrosExcedidosException.class);
		reloj.avanzar(Duration.ofSeconds(1));
		assertThatCode(() -> limite.registrarIntento(IP)).doesNotThrowAnyException();
	}

	@Test
	void registroAnuladoNoCuentaParaElLimite() {
		registrar(5);
		limite.anularIntento(IP);

		assertThatCode(() -> limite.registrarIntento(IP)).doesNotThrowAnyException();
	}

	@Test
	void registrosSimultaneosNoPasanDeCinco() throws Exception {
		AtomicInteger aceptados = new AtomicInteger();
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<?>> tareas = new ArrayList<>();
		try (ExecutorService hilos = Executors.newFixedThreadPool(20)) {
			for (int i = 0; i < 20; i++) {
				tareas.add(hilos.submit(() -> {
					largada.await();
					try {
						limite.registrarIntento(IP);
						aceptados.incrementAndGet();
					} catch (RegistrosExcedidosException rechazado) {
						// esperado a partir del sexto
					}
					return null;
				}));
			}
			largada.countDown();
			for (Future<?> tarea : tareas) {
				tarea.get();
			}
		}

		assertThat(aceptados).hasValue(5);
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
