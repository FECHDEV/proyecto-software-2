package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

// Lo vendido de una categoría en el período, por la categoría actual de cada producto (HU-18 RF-14)
public record CategoriaFila(String categoria, BigDecimal importe, Long pedidos, BigDecimal margen,
		BigDecimal sinCosto) {
}
