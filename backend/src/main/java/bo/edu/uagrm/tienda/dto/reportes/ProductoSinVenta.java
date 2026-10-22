package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una fila de «Sin venderse» (HU-18 RF-13): ultimaVenta es nula si nunca se vendió, y entonces los días cuentan desde
// el alta. El inmovilizado es stock × costo, o stock × precio si no tiene costo (inmovilizadoAPrecio)
public record ProductoSinVenta(Long idProducto, String codigo, String nombre, UnidadVenta unidad, BigDecimal stock,
		BigDecimal precio, BigDecimal costo, LocalDateTime ultimaVenta, long diasSinVenta, BigDecimal inmovilizado,
		boolean inmovilizadoAPrecio) {
}
