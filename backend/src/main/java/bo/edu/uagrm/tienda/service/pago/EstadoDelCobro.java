package bo.edu.uagrm.tienda.service.pago;

// Lo que puede informar el sistema de pago. Un vencimiento, si lo hay, es nuestro: lo controla el
// sistema, no el proveedor
public enum EstadoDelCobro {
	PENDIENTE, PAGADO, ERROR
}
