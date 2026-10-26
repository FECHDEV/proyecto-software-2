package bo.edu.uagrm.tienda.service;

// Los correos del sistema (CLAUDE.md → 'Correos'). Cada correo nuevo es un método más, con su versión en el simulador
// y en el adaptador SMTP
public interface NotificadorCorreo {

	void enviarRecuperacion(String correo, String nombre, String token);

	void enviarVerificacion(String correo, String nombre, String token);

	// HU-21: cuando se confirma el pago, también un pago tardío que revive el pedido
	void enviarPedidoPagado(CorreoDelPedido pedido);

	// HU-21: el pago llegó y el pedido ya no se pudo reservar (HU-13 RF-9)
	void enviarPagoADevolver(CorreoDelPedido pedido);
}
