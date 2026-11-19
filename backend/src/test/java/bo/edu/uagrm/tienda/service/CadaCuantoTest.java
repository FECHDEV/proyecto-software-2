package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import bo.edu.uagrm.tienda.dto.reportes.CompradorFila;

// HU-18 RF-24: cada cuántos días compra una cuenta (ReportesService.cadaCuanto)
class CadaCuantoTest {

	private static CompradorFila fila(long compras, LocalDateTime primera, LocalDateTime ultima) {
		return new CompradorFila(1L, compras, BigDecimal.TEN, primera, ultima, compras, 0L);
	}

	@Test
	void diasDeCalendarioEntreComprasConUnDecimal() {
		assertThat(ReportesService.cadaCuanto(fila(3, LocalDateTime.of(2025, 10, 1, 23, 0),
				LocalDateTime.of(2025, 10, 21, 1, 0)))).isEqualByComparingTo("10.0");
		assertThat(ReportesService.cadaCuanto(fila(1, LocalDateTime.of(2025, 10, 1, 9, 0),
				LocalDateTime.of(2025, 10, 1, 9, 0)))).isNull();
		assertThat(ReportesService.cadaCuanto(fila(2, LocalDateTime.of(2025, 10, 1, 9, 0),
				LocalDateTime.of(2025, 10, 1, 18, 0)))).isEqualByComparingTo("0");
	}

	// Revisión final: 22 compras en dos días seguidos daban 1/21 = 0,0 y se leía «El mismo día»; en días distintos,
	// como mínimo 0,1
	@Test
	void comprasEnDiasDistintosNuncaDanCero() {
		assertThat(ReportesService.cadaCuanto(fila(22, LocalDateTime.of(2025, 10, 1, 9, 0),
				LocalDateTime.of(2025, 10, 2, 9, 0)))).isEqualByComparingTo("0.1");
	}
}
