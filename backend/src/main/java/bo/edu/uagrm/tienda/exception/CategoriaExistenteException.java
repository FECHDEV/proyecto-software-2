package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class CategoriaExistenteException extends ErrorNegocioException {

	public CategoriaExistenteException() {
		super("CATEGORIA_EXISTENTE", HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre.");
	}
}
