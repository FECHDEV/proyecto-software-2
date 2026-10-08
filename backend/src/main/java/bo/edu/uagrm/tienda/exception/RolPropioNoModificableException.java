package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class RolPropioNoModificableException extends ErrorNegocioException {

	public RolPropioNoModificableException() {
		super("ROL_PROPIO_NO_MODIFICABLE", HttpStatus.CONFLICT, "No puede modificar su propio rol.");
	}
}
