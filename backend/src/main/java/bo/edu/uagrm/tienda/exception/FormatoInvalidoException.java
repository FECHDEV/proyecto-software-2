package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class FormatoInvalidoException extends ErrorNegocioException {

	public FormatoInvalidoException() {
		super("FORMATO_INVALIDO", HttpStatus.BAD_REQUEST, "El formato tiene que ser xlsx o pdf.");
	}
}
