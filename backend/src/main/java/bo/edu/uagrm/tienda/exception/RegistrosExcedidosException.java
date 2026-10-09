package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class RegistrosExcedidosException extends ErrorNegocioException {

	public RegistrosExcedidosException() {
		super("REGISTROS_EXCEDIDOS", HttpStatus.TOO_MANY_REQUESTS,
				"Se crearon demasiadas cuentas desde esta conexión. Espere una hora antes de volver a intentar.");
	}
}
