package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class UsuarioNoEncontradoException extends ErrorNegocioException {

	public UsuarioNoEncontradoException() {
		super("USUARIO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "No existe el usuario indicado.");
	}
}
