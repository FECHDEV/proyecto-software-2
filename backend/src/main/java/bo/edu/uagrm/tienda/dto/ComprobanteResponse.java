package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Usuario;

// El comprobante de una compra del Cliente (HU-14 RF-10), en línea o en el mostrador. fecha: cuándo se pagó.
// vendidoPor: solo en el mostrador. medio: solo en el mostrador; en línea es nulo, porque la tienda no sabe con qué se
// pagó en el sistema de pago (la pantalla dice «En línea»). recibido y cambio: solo en efectivo
public record ComprobanteResponse(Long idPedido, LocalDateTime fecha, String cliente, String vendidoPor,
		List<LineaPedidoResponse> lineas, BigDecimal total, MedioPago medio, BigDecimal recibido, BigDecimal cambio) {

	// Lee las líneas y las cuentas: dentro de la transacción
	public static ComprobanteResponse from(Pedido pedido) {
		return new ComprobanteResponse(pedido.getIdPedido(),
				pedido.getPagado() != null ? pedido.getPagado() : pedido.getConfirmado(), nombre(pedido.getUsuario()),
				pedido.getVendidoPor() == null ? null : nombre(pedido.getVendidoPor()),
				pedido.getLineas().stream().map(LineaPedidoResponse::from).toList(), pedido.getTotal(),
				pedido.getMedioPago(), pedido.getRecibido(), pedido.cambio());
	}

	private static String nombre(Usuario usuario) {
		return usuario.getNombre() + " " + usuario.getApellido();
	}
}
