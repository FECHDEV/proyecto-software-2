package bo.edu.uagrm.tienda.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class ErrorNegocioException extends RuntimeException {

	private final String codigo;
	private final HttpStatus estadoHttp;

	protected ErrorNegocioException(String codigo, HttpStatus estadoHttp, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
		this.estadoHttp = estadoHttp;
	}

	// Datos que la pantalla necesita para explicar el error además del código, como un límite o una
	// cantidad disponible. Salen como propiedades del ProblemDetail (ManejadorErrores)
	public Map<String, Object> propiedades() {
		return Map.of();
	}
}
