package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Un resultado de la búsqueda de «Nueva venta» (HU-23 RF-1, RF-3): el precio de hoy y el disponible, que el personal
// ya ve en el inventario. imagen: la primera foto, o nula. precio es el que se cobra hoy, con el descuento (HU-24
// RF-12): con él calcula el total la pantalla, que el servidor compara (RF-14); el normal y el porcentaje, nulos sin
// descuento
public record ProductoVentaResponse(Long idProducto, String nombre, String imagen, BigDecimal precio,
		BigDecimal precioNormal, Integer descuento, UnidadVenta unidad, BigDecimal disponible) {

	public static ProductoVentaResponse from(Producto producto, PrecioDeVenta precio) {
		return new ProductoVentaResponse(producto.getIdProducto(), producto.getNombre(),
				producto.getImagenes().isEmpty() ? null : producto.getImagenes().getFirst(), precio.cobrado(),
				precio.normalTachado(), precio.descuento(), producto.getUnidad(),
				producto.getStock());
	}
}
