package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Usuario;

// La ficha de un pedido para el personal (HU-17 RF-4): lo de la página del Cliente, más la cuenta que lo pidió y quién
// lo entregó (nombre y apellido; nulo si todavía no se entregó)
public record PedidoDespachoResponse(Long idPedido, EstadoPedido estado, List<LineaPedidoResponse> lineas,
		BigDecimal total, String nombreRetira, String telefonoRetira, String nota, LocalDateTime confirmado,
		LocalDateTime pagado, LocalDateTime entregado, String entregadoPor, String clienteNombre,
		String clienteCorreo) {

	// Lee las líneas, el cliente y quien entregó: dentro de la transacción
	public static PedidoDespachoResponse from(Pedido pedido) {
		Usuario cliente = pedido.getUsuario();
		Usuario entrego = pedido.getEntregadoPor();
		return new PedidoDespachoResponse(pedido.getIdPedido(), pedido.getEstado(),
				pedido.getLineas().stream().map(LineaPedidoResponse::from).toList(), pedido.getTotal(),
				pedido.getNombreRetira(), pedido.getTelefonoRetira(), pedido.getNota(), pedido.getConfirmado(),
				pedido.getPagado(), pedido.getEntregado(), entrego == null ? null : nombreCompleto(entrego),
				nombreCompleto(cliente), cliente.getCorreo());
	}

	private static String nombreCompleto(Usuario usuario) {
		return usuario.getNombre() + " " + usuario.getApellido();
	}
}
