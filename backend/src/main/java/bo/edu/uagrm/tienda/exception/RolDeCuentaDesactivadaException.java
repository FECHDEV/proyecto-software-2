package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class RolDeCuentaDesactivadaException extends ErrorNegocioException {

	public RolDeCuentaDesactivadaException() {
		super("ROL_DE_CUENTA_DESACTIVADA", HttpStatus.CONFLICT,
				"No se puede modificar el rol de una cuenta desactivada; primero hay que reactivarla.");
	}
}
