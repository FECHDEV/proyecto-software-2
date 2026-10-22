package bo.edu.uagrm.tienda.exception;

import org.springframework.http.HttpStatus;

// Solo un pedido pagado se entrega (HU-17 RF-7): uno ya entregado, pendiente, cerrado o con pago a devolver no
public class PedidoNoEntregableException extends ErrorNegocioException {

	public PedidoNoEntregableException() {
		super("PEDIDO_NO_ENTREGABLE", HttpStatus.CONFLICT, "El pedido no está pagado: no se puede entregar.");
	}
}
