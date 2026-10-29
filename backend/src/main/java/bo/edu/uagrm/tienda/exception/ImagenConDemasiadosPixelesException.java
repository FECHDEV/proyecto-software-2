package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// HU-22 RF-5: una foto liviana puede declarar medidas que abiertas no entran en la memoria de la API
public class ImagenConDemasiadosPixelesException extends ErrorNegocioException {

	public ImagenConDemasiadosPixelesException() {
		super("IMAGEN_CON_DEMASIADOS_PIXELES", HttpStatus.BAD_REQUEST,
				"La foto admite hasta 8000 px de lado y 40 megapíxeles.");
	}
}
