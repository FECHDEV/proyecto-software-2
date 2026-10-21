package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// Un solo error para token inexistente, vencido, ya usado o de una cuenta desactivada: no se distingue el motivo
public class TokenVerificacionInvalidoException extends ErrorNegocioException {

	public TokenVerificacionInvalidoException() {
		super("TOKEN_VERIFICACION_INVALIDO", HttpStatus.BAD_REQUEST, "El enlace no sirve: venció o ya se usó. Puede pedir otro desde su carrito o Mis datos.");
	}
}
