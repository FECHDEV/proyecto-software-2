package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class ModeloInvalidoException extends ErrorNegocioException {

	public ModeloInvalidoException() {
		super("MODELO_INVALIDO", HttpStatus.BAD_REQUEST, "El modelo debe ser un archivo .glb (glTF binario 2.0).");
	}
}
