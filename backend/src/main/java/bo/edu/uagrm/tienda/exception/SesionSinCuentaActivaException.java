package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// La cuenta se desactivó con la sesión abierta, entre el filtro del token y el servicio: la misma respuesta que da el
// filtro a una cuenta inactiva (401), para que la app cierre la sesión (revisión total)
public class SesionSinCuentaActivaException extends ErrorNegocioException {

	// Los mismos que ManejadorErrores da a una petición sin sesión
	static final String CODIGO = "NO_AUTENTICADO";
	static final String MENSAJE = "Debe iniciar sesión para acceder a este recurso.";

	public SesionSinCuentaActivaException() {
		super(CODIGO, HttpStatus.UNAUTHORIZED, MENSAJE);
	}
}
