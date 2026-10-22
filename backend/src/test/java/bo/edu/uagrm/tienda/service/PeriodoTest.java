package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import bo.edu.uagrm.tienda.exception.PeriodoInvalidoException;

// HU-18: el período de los reportes, leído de la dirección
class PeriodoTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 22);

	// HU-18 RF-2
	@Test
	void sinFechasEsEsteMes() {
		Periodo periodo = Periodo.de(null, null, HOY);

		assertThat(periodo.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(periodo.hasta()).isEqualTo(HOY);
		assertThat(periodo.inicio()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
		// El fin queda afuera: un pago de las 23:59 del último día entra (RF-4)
		assertThat(periodo.fin()).isEqualTo(LocalDateTime.of(2026, 10, 23, 0, 0));
	}

	// HU-18 RF-7: el escenario de la historia, 01/10–22/10 contra 09/09–30/09
	@Test
	void elAnteriorTieneElMismoLargo() {
		Periodo anterior = Periodo.de("2026-10-01", "2026-10-22", HOY).anterior();

		assertThat(anterior.desde()).isEqualTo(LocalDate.of(2026, 9, 9));
		assertThat(anterior.hasta()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(anterior.dias()).isEqualTo(22);
		assertThat(Periodo.de("2026-10-22", "2026-10-22", HOY).anterior().desde()).isEqualTo(LocalDate.of(2026, 10, 21));
	}

	// HU-18 RF-3: ilegible, una sola fecha, al revés, futura o de más de 366 días
	@Test
	void fechasInvalidasSonError() {
		for (String[] fechas : new String[][] { { "xx", "2026-10-22" }, { "2026-10-01", null },
				{ null, "2026-10-01" }, { "2026-10-10", "2026-10-01" }, { "2026-10-01", "2026-10-23" },
				{ "2025-10-21", "2026-10-22" }, { "2026-02-30", "2026-10-01" } }) {
			assertThatThrownBy(() -> Periodo.de(fechas[0], fechas[1], HOY)).as(String.join(" a ", fechas[0] + "",
					fechas[1] + "")).isInstanceOf(PeriodoInvalidoException.class);
		}
		assertThat(Periodo.de("2025-10-22", "2026-10-22", HOY).dias()).isEqualTo(366);
	}

	// HU-18 RF-9: hasta 62 días, por día; más, por mes
	@Test
	void masDe62DiasEsPorMes() {
		assertThat(Periodo.de("2026-08-22", "2026-10-22", HOY).porMes()).isFalse();
		assertThat(Periodo.de("2026-08-21", "2026-10-22", HOY).porMes()).isTrue();
	}
}
