package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class UltimoAdministradorActivoException extends ErrorNegocioException {

	public UltimoAdministradorActivoException() {
		super("ULTIMO_ADMINISTRADOR_ACTIVO", HttpStatus.CONFLICT,
				"La operación dejaría al sistema sin ningún administrador activo.");
	}
}
