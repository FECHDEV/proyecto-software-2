package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

public class StockFueraDeRangoException extends ErrorNegocioException {

	public StockFueraDeRangoException() {
		super("STOCK_FUERA_DE_RANGO", HttpStatus.CONFLICT, "Con esa entrada, lo que hay en el estante pasaría de 9 999 999,99.");
	}
}
