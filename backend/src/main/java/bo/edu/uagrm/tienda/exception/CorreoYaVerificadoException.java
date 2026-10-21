package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class CorreoYaVerificadoException extends ErrorNegocioException {

	public CorreoYaVerificadoException() {
		super("CORREO_YA_VERIFICADO", HttpStatus.CONFLICT, "Su correo ya está verificado.");
	}
}
