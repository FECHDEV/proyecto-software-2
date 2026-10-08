package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class ContrasenaActualIncorrectaException extends ErrorNegocioException {

	public ContrasenaActualIncorrectaException() {
		super("CONTRASENA_ACTUAL_INCORRECTA", HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
	}
}
