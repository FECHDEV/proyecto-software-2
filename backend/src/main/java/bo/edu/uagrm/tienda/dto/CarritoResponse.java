package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.util.List;

// El carrito entero: cada escritura lo devuelve, así la página y el encabezado quedan al día (HU-11). El total suma
// solo las líneas que se pueden comprar (RF-8, RF-9). idPedidoPendiente es el pedido que espera el pago, o null
// (HU-12 RF-5). Sin el correo verificado no se confirma (HU-20 RF-7)
public record CarritoResponse(List<LineaCarritoResponse> lineas, BigDecimal total, int cantidadLineas,
		Long idPedidoPendiente, boolean correoVerificado) {
}
