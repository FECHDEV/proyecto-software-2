package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// HU-20 RF-6: la cuenta está identificada, pero le falta abrir el enlace del correo
public class CorreoSinVerificarException extends ErrorNegocioException {

	public CorreoSinVerificarException() {
		super("CORREO_SIN_VERIFICAR", HttpStatus.FORBIDDEN, "Verifique su correo para confirmar el pedido.");
	}
}
