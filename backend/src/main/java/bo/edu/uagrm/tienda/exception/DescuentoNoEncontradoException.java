package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class DescuentoNoEncontradoException extends ErrorNegocioException {

	public DescuentoNoEncontradoException() {
		super("DESCUENTO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "No existe el descuento indicado.");
	}
}
