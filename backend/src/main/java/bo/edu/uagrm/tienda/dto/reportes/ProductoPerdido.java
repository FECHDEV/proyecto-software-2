package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una fila de los productos de los pedidos perdidos (HU-18 RF-18)
public record ProductoPerdido(Long idProducto, String codigo, String nombre, UnidadVenta unidad, BigDecimal cantidad,
		BigDecimal importe) {
}
