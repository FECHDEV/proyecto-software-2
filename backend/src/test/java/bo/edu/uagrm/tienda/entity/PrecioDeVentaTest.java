package bo.edu.uagrm.tienda.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// HU-24 RF-8: el precio con descuento, redondeado a centavos con HALF_UP y nunca menor que Bs 0,01
class PrecioDeVentaTest {

	private static final LocalDate FIN = LocalDate.of(2026, 11, 30);

	// HU-24 RF-8
	@Test
	void sinDescuentoCobraElNormal() {
		PrecioDeVenta precio = PrecioDeVenta.sinDescuento(new BigDecimal("85.00"));

		assertThat(precio.cobrado()).isEqualByComparingTo("85.00");
		assertThat(precio.descuento()).isNull();
		assertThat(precio.fin()).isNull();
		assertThat(precio.conDescuento()).isFalse();
	}

	// HU-24 RF-8: 4,50 × 85 / 100 = 3,825, que sube a 3,83
	@Test
	void quinceSobreCuatroCincuentaDaTresOchentaYTres() {
		PrecioDeVenta precio = PrecioDeVenta.de(new BigDecimal("4.50"), 15, FIN);

		assertThat(precio.cobrado()).isEqualTo(new BigDecimal("3.83"));
		assertThat(precio.normal()).isEqualByComparingTo("4.50");
		assertThat(precio.descuento()).isEqualTo(15);
		assertThat(precio.fin()).isEqualTo(FIN);
		assertThat(precio.conDescuento()).isTrue();
	}

	// HU-24 RF-8
	@Test
	void noventaSobreElMaximoDaDiez() {
		assertThat(PrecioDeVenta.de(new BigDecimal("99999.99"), 90, FIN).cobrado()).isEqualTo(new BigDecimal("10000.00"));
	}

	// HU-24 RF-8: el piso de un centavo
	@Test
	void nuncaBajaDeUnCentavo() {
		assertThat(PrecioDeVenta.de(new BigDecimal("0.01"), 90, FIN).cobrado()).isEqualTo(new BigDecimal("0.01"));
		assertThat(PrecioDeVenta.de(new BigDecimal("0.04"), 90, FIN).cobrado()).isEqualTo(new BigDecimal("0.01"));
		assertThat(PrecioDeVenta.de(new BigDecimal("0.05"), 90, FIN).cobrado()).isEqualTo(new BigDecimal("0.01"));
	}

	// /code-review: si el redondeo o el piso dejan el mismo precio, no hay rebaja
	@Test
	void sinRebajaRealNoHayDescuento() {
		PrecioDeVenta cincuentaCentavos = PrecioDeVenta.de(new BigDecimal("0.50"), 1, FIN);
		PrecioDeVenta unCentavo = PrecioDeVenta.de(new BigDecimal("0.01"), 90, FIN);

		assertThat(cincuentaCentavos.conDescuento()).isFalse();
		assertThat(cincuentaCentavos.cobrado()).isEqualByComparingTo("0.50");
		assertThat(cincuentaCentavos.normalTachado()).isNull();
		assertThat(unCentavo.conDescuento()).isFalse();
		assertThat(PrecioDeVenta.de(new BigDecimal("4.50"), 15, FIN).normalTachado()).isEqualByComparingTo("4.50");
	}

	// /code-review: la regla vale también para quien use el constructor
	@Test
	void elConstructorNoAceptaUnDescuentoSinRebajaOSinFin() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new PrecioDeVenta(new BigDecimal("1.00"), 10, new BigDecimal("1.00"), FIN))
				.isInstanceOf(IllegalArgumentException.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new PrecioDeVenta(new BigDecimal("1.00"), 10, new BigDecimal("0.90"), null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// HU-24 RF-8
	@Test
	void unoPorCientoSobreUnBoliviano() {
		assertThat(PrecioDeVenta.de(new BigDecimal("1.00"), 1, FIN).cobrado()).isEqualTo(new BigDecimal("0.99"));
	}
}
