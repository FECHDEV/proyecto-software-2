package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.Canal;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Usuario;

// La ficha de un pedido para el personal (HU-17 RF-4): lo de la página del Cliente, más la cuenta que lo pidió y quién
// lo entregó (nombre y apellido; nulo si todavía no se entregó). En una venta del mostrador (HU-23 RF-16), también el
// medio de pago, quién vendió y la anulación; la cuenta es nula en el consumidor final y no hay datos de retiro
public record PedidoDespachoResponse(Long idPedido, EstadoPedido estado, List<LineaPedidoResponse> lineas,
		BigDecimal total, String nombreRetira, String telefonoRetira, String nota, LocalDateTime confirmado,
		LocalDateTime pagado, LocalDateTime entregado, String entregadoPor, String clienteNombre,
		String clienteCorreo, Canal canal, MedioPago medioPago, String vendidoPor, LocalDateTime anulado,
		String anuladoPor, String motivoAnulacion) {

	// Lee las líneas, el cliente y quien entregó: dentro de la transacción
	public static PedidoDespachoResponse from(Pedido pedido) {
		Usuario cliente = pedido.getUsuario();
		Usuario entrego = pedido.getEntregadoPor();
		return new PedidoDespachoResponse(pedido.getIdPedido(), pedido.getEstado(),
				pedido.getLineas().stream().map(LineaPedidoResponse::from).toList(), pedido.getTotal(),
				pedido.getNombreRetira(), pedido.getTelefonoRetira(), pedido.getNota(), pedido.getConfirmado(),
				pedido.getPagado(), pedido.getEntregado(), entrego == null ? null : nombreCompleto(entrego),
				cliente == null ? null : nombreCompleto(cliente), cliente == null ? null : cliente.getCorreo(),
				pedido.getCanal(), pedido.getMedioPago(),
				pedido.getVendidoPor() == null ? null : nombreCompleto(pedido.getVendidoPor()), pedido.getAnulado(),
				pedido.getAnuladoPor() == null ? null : nombreCompleto(pedido.getAnuladoPor()),
				pedido.getMotivoAnulacion());
	}

	private static String nombreCompleto(Usuario usuario) {
		return usuario.getNombre() + " " + usuario.getApellido();
	}
}
