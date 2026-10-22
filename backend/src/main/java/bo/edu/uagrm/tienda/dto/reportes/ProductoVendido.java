package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una fila de «Más vendidos» o «Menos vendidos» (HU-18 RF-11, RF-12). La cantidad va en la unidad del producto; el
// margen es nulo si nada vendido tenía costo
public record ProductoVendido(Long idProducto, String codigo, String nombre, UnidadVenta unidad, boolean activo,
		BigDecimal cantidad, BigDecimal importe, BigDecimal margen, BigDecimal sinCosto) {
}
