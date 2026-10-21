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

	// Como en PedidoResponse: 0 si ya no espera el pago o se le acabó el tiempo
	private static long segundosParaPagar(EstadoPedido estado, LocalDateTime vence, LocalDateTime ahora) {
		return estado == EstadoPedido.PENDIENTE_PAGO ? Math.max(0, Duration.between(ahora, vence).toSeconds()) : 0;
	}
}
