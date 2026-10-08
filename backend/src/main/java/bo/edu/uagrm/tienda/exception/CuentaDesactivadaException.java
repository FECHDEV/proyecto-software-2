package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class CuentaDesactivadaException extends ErrorNegocioException {

	public CuentaDesactivadaException() {
		super("CUENTA_DESACTIVADA", HttpStatus.FORBIDDEN, "La cuenta está desactivada y no puede acceder al sistema.");
	}
}
