package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// HU-24 RF-4, RF-5: solo un programado se edita o se borra, y solo un vigente se termina; un vencido no se toca
public class DescuentoNoEditableException extends ErrorNegocioException {

	public DescuentoNoEditableException(String detalle) {
		super("DESCUENTO_NO_EDITABLE", HttpStatus.CONFLICT, detalle);
	}
}
