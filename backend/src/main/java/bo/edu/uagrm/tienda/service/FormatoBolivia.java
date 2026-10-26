package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

// Números como se leen en Bolivia y en la pantalla: miles con punto y decimales con coma. Los usan el reporte
// exportado (HU-19) y los correos del pedido (HU-21)
public final class FormatoBolivia {

	private FormatoBolivia() {
	}

	// «Bs 1.234,50»
	public static String dinero(BigDecimal valor) {
		return (valor.signum() < 0 ? "-Bs " : "Bs ") + numero(valor.abs(), 2, 2);
	}

	public static String numero(BigDecimal valor, int minimo, int maximo) {
		DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
		simbolos.setGroupingSeparator('.');
		simbolos.setDecimalSeparator(',');
		simbolos.setMinusSign('-');
		DecimalFormat formato = new DecimalFormat("#,##0", simbolos);
		formato.setMinimumFractionDigits(minimo);
		formato.setMaximumFractionDigits(maximo);
		return formato.format(valor);
	}
}
