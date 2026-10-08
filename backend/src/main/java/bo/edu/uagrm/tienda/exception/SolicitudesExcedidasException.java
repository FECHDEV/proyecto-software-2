package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class SolicitudesExcedidasException extends ErrorNegocioException {

	public SolicitudesExcedidasException() {
		super("SOLICITUDES_EXCEDIDAS", HttpStatus.TOO_MANY_REQUESTS,
				"Demasiadas solicitudes de recuperación. Espere unos minutos antes de volver a intentar.");
	}
}
