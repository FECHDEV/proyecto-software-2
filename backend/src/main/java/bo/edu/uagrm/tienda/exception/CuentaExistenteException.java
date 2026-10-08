package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class CuentaExistenteException extends ErrorNegocioException {

	public CuentaExistenteException() {
		super("CUENTA_EXISTENTE", HttpStatus.CONFLICT, "Ya existe una cuenta registrada con ese correo.");
	}
}
