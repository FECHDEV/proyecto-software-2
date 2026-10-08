package bo.edu.uagrm.tienda.service.pago;

// No se pudo hablar con el sistema de pago (caído, respuesta inválida). No es un error de negocio: quien llama decide
// qué hacer (al generar el cobro, avisar al cliente; al consultar, reintentar en la próxima verificación)
public class SistemaDePagoNoDisponibleException extends RuntimeException {

	public SistemaDePagoNoDisponibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
