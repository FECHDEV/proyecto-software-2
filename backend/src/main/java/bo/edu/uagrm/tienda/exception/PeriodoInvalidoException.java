package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class PeriodoInvalidoException extends ErrorNegocioException {

	public PeriodoInvalidoException() {
		super("PERIODO_INVALIDO", HttpStatus.BAD_REQUEST,
				"El período no es válido: dos fechas AAAA-MM-DD, la primera no posterior a la segunda, hasta hoy y de 366 días como máximo.");
	}
}
