package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class IntentosExcedidosException extends ErrorNegocioException {

	public IntentosExcedidosException() {
		super("INTENTOS_EXCEDIDOS", HttpStatus.TOO_MANY_REQUESTS,
				"Demasiados intentos fallidos. Espere unos minutos antes de volver a intentar.");
	}
}
