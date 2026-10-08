package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// Un solo error para token inexistente, vencido o ya usado: no se distingue el motivo
public class TokenRecuperacionInvalidoException extends ErrorNegocioException {

	public TokenRecuperacionInvalidoException() {
		super("TOKEN_RECUPERACION_INVALIDO", HttpStatus.BAD_REQUEST,
				"El enlace de recuperación no es válido o ya venció. Solicite la recuperación de la contraseña nuevamente.");
	}
}
