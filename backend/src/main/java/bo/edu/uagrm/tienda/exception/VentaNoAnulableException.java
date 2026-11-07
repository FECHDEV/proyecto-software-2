package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// Solo se anula una venta del mostrador en efectivo, ya entregada y del mismo día en Bolivia (HU-23 RF-20)
public class VentaNoAnulableException extends ErrorNegocioException {

	public VentaNoAnulableException() {
		super("VENTA_NO_ANULABLE", HttpStatus.CONFLICT,
				"Esta venta no se puede anular: solo una en efectivo, entregada y de hoy.");
	}
}
