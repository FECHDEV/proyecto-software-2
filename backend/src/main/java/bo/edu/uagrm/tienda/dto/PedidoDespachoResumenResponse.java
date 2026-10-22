package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.EstadoPedido;

// Un pedido en la lista del personal (HU-17 RF-1); el detalle está en su ficha. cantidadProductos son las líneas, como en
// «Mis pedidos»
public record PedidoDespachoResumenResponse(Long idPedido, EstadoPedido estado, LocalDateTime confirmado,
		LocalDateTime pagado, String nombreRetira, int cantidadProductos, BigDecimal total) {
}
