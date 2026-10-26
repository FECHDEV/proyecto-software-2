package bo.edu.uagrm.tienda.service.exportar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import bo.edu.uagrm.tienda.service.FormatoBolivia;

// Un valor de una tabla del reporte exportado (HU-19), con su tipo: el Excel lo guarda como número o fecha de
// verdad (RF-10) y el PDF lo escribe como se lee en Bolivia, con texto() (RF-11)
sealed interface Celda {

	DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	String texto();

	static Celda texto(String valor) {
		return new Texto(valor == null ? "" : valor);
	}

	record Texto(String valor) implements Celda {

		@Override
		public String texto() {
			return valor;
		}
	}

	// En bolivianos: «Bs 1.234,50»
	record Dinero(BigDecimal valor) implements Celda {

		@Override
		public String texto() {
			return FormatoBolivia.dinero(valor);
		}
	}

	// Una cantidad, con los decimales de su unidad (o 0 para las cuentas)
	record Numero(BigDecimal valor, int decimales) implements Celda {

		Numero(long valor) {
			this(BigDecimal.valueOf(valor), 0);
		}

		@Override
		public String texto() {
			return FormatoBolivia.numero(valor, 0, decimales);
		}
	}

	// valor en por ciento: 15,6 es «15,6 %»
	record Porcentaje(BigDecimal valor) implements Celda {

		@Override
		public String texto() {
			return FormatoBolivia.numero(valor, 0, 1) + " %";
		}
	}

	record Fecha(LocalDate valor) implements Celda {

		@Override
		public String texto() {
			return FECHA.format(valor);
		}
	}

	record FechaHora(LocalDateTime valor) implements Celda {

		@Override
		public String texto() {
			return FECHA_HORA.format(valor);
		}
	}
}
