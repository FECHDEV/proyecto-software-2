package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class ImagenInvalidaException extends ErrorNegocioException {

	public ImagenInvalidaException() {
		super("IMAGEN_INVALIDA", HttpStatus.BAD_REQUEST, "La imagen debe ser JPG, PNG o WebP.");
	}
}
