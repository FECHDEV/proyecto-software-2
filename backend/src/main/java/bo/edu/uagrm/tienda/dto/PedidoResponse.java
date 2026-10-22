package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;

// La página del pedido (HU-12 RF-8). segundosParaPagar lo cuenta el servidor, así el contador de la página no depende
// del reloj ni de la zona del dispositivo; es 0 si el pedido ya no espera el pago. cobro: el QR pendiente, o nulo; pagado:
// cuándo se pagó (HU-13); entregado: cuándo se lo llevaron (HU-17 RF-10)
public record PedidoResponse(Long idPedido, EstadoPedido estado, List<LineaPedidoResponse> lineas, BigDecimal total,
		String nombreRetira, String telefonoRetira, String nota, LocalDateTime confirmado, LocalDateTime vence,
		long segundosParaPagar, RetiroResponse retiro, LocalDateTime pagado, CobroResponse cobro,
		LocalDateTime entregado) {

	// Lee las líneas: dentro de la transacción
	public static PedidoResponse from(Pedido pedido, RetiroResponse retiro, LocalDateTime ahora, CobroResponse cobro) {
		long segundos = pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO
				? Math.max(0, Duration.between(ahora, pedido.getVence()).toSeconds())
				: 0;
		return new PedidoResponse(pedido.getIdPedido(), pedido.getEstado(),
				pedido.getLineas().stream().map(LineaPedidoResponse::from).toList(), pedido.getTotal(),
				pedido.getNombreRetira(), pedido.getTelefonoRetira(), pedido.getNota(), pedido.getConfirmado(),
				pedido.getVence(), segundos, retiro, pedido.getPagado(), cobro, pedido.getEntregado());
	}
}
