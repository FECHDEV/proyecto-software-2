package bo.edu.uagrm.tienda.service.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

// plan-implementacion.md, decisión 4: devuelve los mismos estados que el proveedor y permite forzar cada uno (RF-22)
class SimuladorDePagoTest {

	// 22/09/2026 a las 20:00 en La Paz
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneId.of("America/La_Paz"));

	private final SimuladorDePago simulador = new SimuladorDePago(RELOJ);

	@Test
	void generaUnCobroPendienteConReferenciaYQrFicticios() {
		CobroGenerado cobro = simulador.generarCobro(40L, new BigDecimal("150.00"), "Entradas General");

		assertThat(cobro.referencia()).startsWith("SIM-");
		assertThat(cobro.codigoQr()).contains(cobro.referencia()).contains("150.00");
		assertThat(cobro.urlPasarela()).isNull();
		assertThat(simulador.consultar(cobro.referencia())).isEqualTo(ResultadoDelCobro.pendiente());
	}

	@Test
	void cadaCobroTieneSuReferencia() {
		assertThat(simulador.generarCobro(40L, BigDecimal.TEN, "a").referencia())
				.isNotEqualTo(simulador.generarCobro(40L, BigDecimal.TEN, "a").referencia());
	}

	@Test
	void forzarPagadoGuardaLaFechaDelPago() {
		String referencia = simulador.generarCobro(40L, BigDecimal.TEN, "a").referencia();

		simulador.forzar(referencia, EstadoDelCobro.PAGADO);

		assertThat(simulador.consultar(referencia))
				.isEqualTo(new ResultadoDelCobro(EstadoDelCobro.PAGADO, LocalDateTime.of(2026, 9, 22, 20, 0)));
	}

	@Test
	void forzarUnError() {
		String referencia = simulador.generarCobro(40L, BigDecimal.TEN, "a").referencia();

		simulador.forzar(referencia, EstadoDelCobro.ERROR);

		assertThat(simulador.consultar(referencia).estado()).isEqualTo(EstadoDelCobro.ERROR);
	}

	// Tras un reinicio el simulador olvida sus cobros: uno desconocido sigue pendiente, como un pago que no llegó
	@Test
	void unaReferenciaDesconocidaEstaPendiente() {
		assertThat(simulador.consultar("SIM-desconocida")).isEqualTo(ResultadoDelCobro.pendiente());
	}

	// Tras un reinicio el cobro sigue en la base aunque el simulador lo olvidó: se puede forzar igual. La referencia
	// llega desde la base y solo para el dueño (PagoService.referenciaParaSimular)
	@Test
	void seFuerzaUnCobroQueOlvidoTrasReiniciar() {
		simulador.forzar("SIM-de-antes", EstadoDelCobro.PAGADO);

		assertThat(simulador.consultar("SIM-de-antes").estado()).isEqualTo(EstadoDelCobro.PAGADO);
	}

	@Test
	void noSeFuerzaAPendiente() {
		String referencia = simulador.generarCobro(40L, BigDecimal.TEN, "a").referencia();

		assertThatThrownBy(() -> simulador.forzar(referencia, EstadoDelCobro.PENDIENTE))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
