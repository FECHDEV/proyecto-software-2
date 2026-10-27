package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una fila del inventario y la cabecera del stock de un producto (HU-08). El stock es el disponible; lo comprometido
// sigue en el estante por pedidos abiertos (RF-15, RF-16)
public record InventarioResponse(Long idProducto, String codigo, String nombre, String categoria, UnidadVenta unidad,
		boolean activo, BigDecimal stock, BigDecimal stockMinimo, boolean stockBajo, BigDecimal comprometido) {

	// Con dos decimales, como el stock: sin pedidos abiertos la suma es un 0 sin escala
	public InventarioResponse {
		comprometido = comprometido.setScale(2, RoundingMode.UNNECESSARY);
	}

	// Lee la categoría: dentro de la transacción
	public static InventarioResponse from(Producto producto, BigDecimal comprometido) {
		return new InventarioResponse(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
				producto.getCategoria().getNombre(), producto.getUnidad(), producto.isActivo(), producto.getStock(),
				producto.getStockMinimo(), producto.isStockBajo(), comprometido);
	}
}
