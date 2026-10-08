package bo.edu.uagrm.tienda.exception;

import java.util.List;

import lombok.Getter;

// Reglas de campo que no se pueden expresar como anotación, porque dependen del reloj o de un dato que ya existe. Sale
// con el mismo formato que las validaciones de @Valid (ManejadorErrores). No extiende ErrorNegocioException: ese
// formato no lleva la lista de errores
@Getter
public class CamposInvalidosException extends RuntimeException {

	private final List<CampoInvalido> errores;

	public CamposInvalidosException(List<CampoInvalido> errores) {
		super("Hay campos a corregir.");
		this.errores = List.copyOf(errores);
	}

	public record CampoInvalido(String campo, String mensaje) {
	}
}
