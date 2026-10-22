package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class TipoSoloClienteException extends ErrorNegocioException {

	public TipoSoloClienteException() {
		super("TIPO_SOLO_CLIENTE", HttpStatus.CONFLICT, "Solo una cuenta de cliente tiene tipo de cliente.");
	}
}
