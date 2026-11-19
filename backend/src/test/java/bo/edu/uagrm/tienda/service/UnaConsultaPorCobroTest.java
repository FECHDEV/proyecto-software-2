package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// HU-13 RF-10, RF-11 (auditoría 18/11/2026): los avisos del mismo cobro no consultan todos a la vez
class UnaConsultaPorCobroTest {

	private final UnaConsultaPorCobro avisos = new UnaConsultaPorCobro();
	private final ExecutorService hilos = Executors.newFixedThreadPool(12);

	@AfterEach
	void cerrar() {
		hilos.shutdownNow();
	}

	// HU-13 RF-11: mientras una consulta corre, las demás del mismo cobro no llaman al sistema de pago
	@Test
	void unaSolaConsultaALaVezPorCobro() throws Exception {
		CountDownLatch adentro = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		AtomicInteger corriendo = new AtomicInteger();
		AtomicInteger maximo = new AtomicInteger();
		Runnable consulta = () -> {
			maximo.accumulateAndGet(corriendo.incrementAndGet(), Math::max);
			adentro.countDown();
			esperar(soltar);
			corriendo.decrementAndGet();
		};
		Future<?> primera = hilos.submit(() -> avisos.ejecutar(1L, consulta));
		assertThat(adentro.await(5, TimeUnit.SECONDS)).isTrue();

		List<Future<?>> demas = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			demas.add(hilos.submit(() -> avisos.ejecutar(1L, consulta)));
		}
		for (Future<?> aviso : demas) {
			aviso.get(5, TimeUnit.SECONDS);
		}
		soltar.countDown();
		primera.get(5, TimeUnit.SECONDS);

		assertThat(maximo.get()).isEqualTo(1);
	}

	// HU-13 RF-10: los avisos que llegan durante la consulta piden una sola vuelta más, para no perder un pago
	@Test
	void lasQueLleganDuranteLaConsultaPidenUnaSolaVueltaMas() throws Exception {
		CountDownLatch adentro = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		AtomicInteger consultas = new AtomicInteger();
		Runnable consulta = () -> {
			if (consultas.incrementAndGet() == 1) {
				adentro.countDown();
				esperar(soltar);
			}
		};
		Future<?> primera = hilos.submit(() -> avisos.ejecutar(1L, consulta));
		assertThat(adentro.await(5, TimeUnit.SECONDS)).isTrue();
		for (int i = 0; i < 10; i++) {
			hilos.submit(() -> avisos.ejecutar(1L, consulta)).get(5, TimeUnit.SECONDS);
		}

		soltar.countDown();
		primera.get(5, TimeUnit.SECONDS);

		assertThat(consultas.get()).isEqualTo(2);
	}

	// HU-13 RF-10: cobros distintos no se esperan entre sí
	@Test
	void cobrosDistintosConsultanEnParalelo() throws Exception {
		CountDownLatch los2Adentro = new CountDownLatch(2);
		CountDownLatch soltar = new CountDownLatch(1);
		Runnable consulta = () -> {
			los2Adentro.countDown();
			esperar(soltar);
		};
		Future<?> uno = hilos.submit(() -> avisos.ejecutar(1L, consulta));
		Future<?> otro = hilos.submit(() -> avisos.ejecutar(2L, consulta));

		assertThat(los2Adentro.await(5, TimeUnit.SECONDS)).isTrue();
		soltar.countDown();
		uno.get(5, TimeUnit.SECONDS);
		otro.get(5, TimeUnit.SECONDS);
	}

	// HU-13 RF-10: una consulta que falla no deja el cobro trabado
	@Test
	void siLaConsultaFallaElCobroQuedaLibre() {
		assertThatThrownBy(() -> avisos.ejecutar(1L, () -> {
			throw new IllegalStateException("sin conexión");
		})).isInstanceOf(IllegalStateException.class);

		AtomicInteger consultas = new AtomicInteger();
		avisos.ejecutar(1L, consultas::incrementAndGet);

		assertThat(consultas.get()).isEqualTo(1);
	}

	private static void esperar(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}
}
