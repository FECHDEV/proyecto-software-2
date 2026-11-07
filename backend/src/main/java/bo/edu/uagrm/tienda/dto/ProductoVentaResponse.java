package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Un resultado de la búsqueda de «Nueva venta» (HU-23 RF-1, RF-3): el precio de hoy y el disponible, que el personal
// ya ve en el inventario. imagen: la primera foto, o nula
public record ProductoVentaResponse(Long idProducto, String nombre, String imagen, BigDecimal precio,
		UnidadVenta unidad, BigDecimal disponible) {

	public static ProductoVentaResponse from(Producto producto) {
		return new ProductoVentaResponse(producto.getIdProducto(), producto.getNombre(),
				producto.getImagenes().isEmpty() ? null : producto.getImagenes().getFirst(), producto.getPrecio(),
				producto.getUnidad(), producto.getStock());
	}
}
