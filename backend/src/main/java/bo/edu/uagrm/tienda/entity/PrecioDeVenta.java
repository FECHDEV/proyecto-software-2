package bo.edu.uagrm.tienda.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

// El precio de un producto hoy (HU-24 RF-8): el normal, el porcentaje vigente (nulo sin descuento), el que se cobra y
// el último día del descuento (para el JSON-LD, RF-14). Un valor sin dependencias: LineaPedido lo recibe y la entidad
// no importa de service/
public record PrecioDeVenta(BigDecimal normal, Integer descuento, BigDecimal cobrado, LocalDate fin) {

	static final BigDecimal MINIMO = new BigDecimal("0.01");
	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	// La regla vale para cualquiera que lo construya, no solo para las fábricas: con descuento, una rebaja real y su
	// último día (/code-review)
	public PrecioDeVenta {
		Objects.requireNonNull(normal);
		Objects.requireNonNull(cobrado);
		if (descuento != null && (fin == null || cobrado.compareTo(normal) >= 0)) {
			throw new IllegalArgumentException("Un descuento sin rebaja o sin fin");
		}
	}

	public static PrecioDeVenta sinDescuento(BigDecimal normal) {
		return new PrecioDeVenta(normal, null, normal, null);
	}

	// normal × (100 − p) tiene dos decimales y dividir por 100 solo corre la coma: el único redondeo es el de los
	// centavos, con el medio centavo hacia arriba, y nunca menos de un centavo. Si el redondeo o el piso dejan el mismo
	// precio (Bs 0,50 al −1 %, Bs 0,01 a cualquier porcentaje), no hay rebaja: ni tachado, ni «−1 %», ni la línea
	// cuenta como vendida con descuento (/code-review)
	public static PrecioDeVenta de(BigDecimal normal, int descuento, LocalDate fin) {
		BigDecimal cobrado = normal.multiply(BigDecimal.valueOf(100L - descuento)).divide(CIEN, 2, RoundingMode.HALF_UP)
				.max(MINIMO);
		if (cobrado.compareTo(normal) >= 0) {
			return sinDescuento(normal);
		}
		return new PrecioDeVenta(normal, descuento, cobrado, Objects.requireNonNull(fin));
	}

	public boolean conDescuento() {
		return descuento != null;
	}

	// El precio para tachar en la tienda y el mostrador (HU-24 RF-11): el normal si hay rebaja, nulo si no
	public BigDecimal normalTachado() {
		return conDescuento() ? normal : null;
	}
}
