package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class ReenviosExcedidosException extends ErrorNegocioException {

	public ReenviosExcedidosException() {
		super("REENVIOS_EXCEDIDOS", HttpStatus.TOO_MANY_REQUESTS, "Ya le enviamos varios correos. Espere unos minutos antes de pedir otro.");
	}
}
