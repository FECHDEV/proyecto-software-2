package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class UnidadIncompatibleConStockException extends ErrorNegocioException {

	public UnidadIncompatibleConStockException() {
		super("UNIDAD_INCOMPATIBLE_CON_STOCK", HttpStatus.CONFLICT, "El stock, el mínimo o un pedido abierto tienen decimales y esa unidad no los admite.");
	}
}
