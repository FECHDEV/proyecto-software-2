package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class ModeloDemasiadoGrandeException extends ErrorNegocioException {

	public ModeloDemasiadoGrandeException() {
		super("MODELO_DEMASIADO_GRANDE", HttpStatus.BAD_REQUEST, "El modelo admite hasta 3 MB.");
	}
}
