package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class AjusteSinCambioException extends ErrorNegocioException {

	public AjusteSinCambioException() {
		super("AJUSTE_SIN_CAMBIO", HttpStatus.CONFLICT, "La cantidad contada es igual a lo que hay en el estante: no hay nada que ajustar.");
	}
}
