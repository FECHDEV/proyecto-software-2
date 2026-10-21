package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;

// Un pedido en «Mis pedidos» (HU-14 RF-1, RF-2): lo que se ve en la lista; el detalle está en su página.
// cantidadProductos son las líneas, no la suma de cantidades (mezclaría unidades y metros)
public record PedidoResumenResponse(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, int cantidadProductos,
		BigDecimal total, long segundosParaPagar) {

	// Lo que lee la consulta de la lista, sin cargar el pedido
	public static PedidoResumenResponse de(Long idPedido, EstadoPedido estado, LocalDateTime confirmado,
			int cantidadProductos, BigDecimal total, LocalDateTime vence, LocalDateTime ahora) {
		return new PedidoResumenResponse(idPedido, estado, confirmado, cantidadProductos, total,
				segundosParaPagar(estado, vence, ahora));
	}

	// Lee las líneas: dentro de la transacción
	public static PedidoResumenResponse from(Pedido pedido, LocalDateTime ahora) {
		return new PedidoResumenResponse(pedido.getIdPedido(), pedido.getEstado(), pedido.getConfirmado(),
				pedido.getLineas().size(), pedido.getTotal(),
				segundosParaPagar(pedido.getEstado(), pedido.getVence(), ahora));
	}

	// 0 si ya no espera el pago o si ya pasó la hora. Redondeado hacia arriba: con medio segundo por delante dice 1, así
	// un 0 en un pedido pendiente siempre significa que la hora pasó y espera la consulta de su cobro (HU-13 RF-7)
	private static long segundosParaPagar(EstadoPedido estado, LocalDateTime vence, LocalDateTime ahora) {
		if (estado != EstadoPedido.PENDIENTE_PAGO) {
			return 0;
		}
		long milisegundos = Duration.between(ahora, vence).toMillis();
		return milisegundos <= 0 ? 0 : Math.ceilDiv(milisegundos, 1000);
	}
}
