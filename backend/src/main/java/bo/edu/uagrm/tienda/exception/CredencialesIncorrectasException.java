package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class CredencialesIncorrectasException extends ErrorNegocioException {

	public CredencialesIncorrectasException() {
		super("CREDENCIALES_INCORRECTAS", HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
	}
}
