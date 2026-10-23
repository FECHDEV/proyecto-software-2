package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una fila de la lista de la gestión (HU-07 RF-10); imagenPrincipal es null si no tiene fotos, y conModelo marca
// los que tienen modelo 3D (HU-15 RF-12)
public record ProductoResumenResponse(Long idProducto, String codigo, String nombre, String marca, String categoria,
		BigDecimal precio, UnidadVenta unidad, boolean activo, String imagenPrincipal, boolean conModelo) {

	// Lee la categoría y las imágenes: dentro de la transacción
	public static ProductoResumenResponse from(Producto producto) {
		return new ProductoResumenResponse(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
				producto.getMarca(), producto.getCategoria().getNombre(), producto.getPrecio(), producto.getUnidad(),
				producto.isActivo(), producto.getImagenes().isEmpty() ? null : producto.getImagenes().getFirst(),
				producto.getModelo() != null);
	}
}
