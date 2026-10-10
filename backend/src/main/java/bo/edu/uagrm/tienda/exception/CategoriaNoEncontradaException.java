package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class CategoriaNoEncontradaException extends ErrorNegocioException {

	public CategoriaNoEncontradaException() {
		super("CATEGORIA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "No existe la categoría indicada.");
	}
}
