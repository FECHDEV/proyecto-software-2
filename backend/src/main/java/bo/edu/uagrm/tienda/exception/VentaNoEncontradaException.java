package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// Una venta del mostrador que no existe, o el id de un pedido en línea (HU-23, casos límite)
public class VentaNoEncontradaException extends ErrorNegocioException {

	public VentaNoEncontradaException() {
		super("VENTA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "No encontramos esa venta.");
	}
}
