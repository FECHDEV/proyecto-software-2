package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class ImagenDemasiadoGrandeException extends ErrorNegocioException {

	public ImagenDemasiadoGrandeException() {
		super("IMAGEN_DEMASIADO_GRANDE", HttpStatus.BAD_REQUEST, "La imagen admite hasta 2 MB.");
	}
}
