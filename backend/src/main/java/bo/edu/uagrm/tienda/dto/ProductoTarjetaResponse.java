package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una tarjeta del catálogo público (HU-09 RF-6). Solo si hay stock, nunca cuánto ni otro dato de la gestión
// (RF-15); imagenPrincipal es null si no tiene fotos. conModelo, para la marca «3D» (HU-16 RF-2). precio es el que se
// cobra hoy; con descuento, también el normal y el porcentaje, nulos sin descuento (HU-24 RF-12)
public record ProductoTarjetaResponse(Long idProducto, String nombre, String marca, BigDecimal precio,
		BigDecimal precioNormal, Integer descuento, UnidadVenta unidad, String imagenPrincipal, boolean disponible,
		boolean conModelo) {

	// Lee las imágenes: dentro de la transacción
	public static ProductoTarjetaResponse from(Producto producto, PrecioDeVenta precio) {
		return new ProductoTarjetaResponse(producto.getIdProducto(), producto.getNombre(), producto.getMarca(),
				precio.cobrado(), precio.normalTachado(), precio.descuento(),
				producto.getUnidad(),
				producto.getImagenes().isEmpty() ? null : producto.getImagenes().getFirst(),
				producto.getStock().signum() > 0, producto.getModelo() != null);
	}
}
