package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

// Lo que tenía un producto en los pedidos perdidos del período (HU-18 RF-18)
public record PerdidoFila(Long idProducto, BigDecimal cantidad, BigDecimal importe) {
}
