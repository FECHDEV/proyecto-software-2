package bo.edu.uagrm.tienda.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import bo.edu.uagrm.tienda.exception.PeriodoInvalidoException;

// El período de los reportes (HU-18), en días de Bolivia: los pagos se guardan con la hora de La Paz. La pantalla
// corrige la dirección antes de llamar (RF-3); acá llega solo lo que alguien escribió a mano
public record Periodo(LocalDate desde, LocalDate hasta) {

	static final int MAXIMO_DIAS = 366;
	static final int MAXIMO_DIAS_POR_DIA = 62;

	// Sin fechas, del 1 del mes a hoy (RF-2); con una sola, o una que no se entiende, es un error
	public static Periodo de(String desde, String hasta, LocalDate hoy) {
		if (desde == null && hasta == null) {
			return new Periodo(hoy.withDayOfMonth(1), hoy);
		}
		LocalDate inicio = leer(desde);
		LocalDate fin = leer(hasta);
		if (fin.isAfter(hoy) || inicio.isAfter(fin) || ChronoUnit.DAYS.between(inicio, fin) + 1 > MAXIMO_DIAS) {
			throw new PeriodoInvalidoException();
		}
		return new Periodo(inicio, fin);
	}

	private static LocalDate leer(String fecha) {
		if (fecha == null) {
			throw new PeriodoInvalidoException();
		}
		try {
			return LocalDate.parse(fecha.strip());
		} catch (DateTimeParseException e) {
			throw new PeriodoInvalidoException();
		}
	}

	public long dias() {
		return ChronoUnit.DAYS.between(desde, hasta) + 1;
	}

	// Los días inmediatamente antes, del mismo largo (RF-7)
	public Periodo anterior() {
		return new Periodo(desde.minusDays(dias()), desde.minusDays(1));
	}

	public LocalDateTime inicio() {
		return desde.atStartOfDay();
	}

	// Excluido: un pago a las 23:59:59,9 del último día entra (RF-4)
	public LocalDateTime fin() {
		return hasta.plusDays(1).atStartOfDay();
	}

	// RF-9
	public boolean porMes() {
		return dias() > MAXIMO_DIAS_POR_DIA;
	}
}
