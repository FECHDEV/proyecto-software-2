package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.util.List;

import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// El detalle público de un producto (HU-10 RF-2, RF-4, RF-7). Como la tarjeta, solo si hay stock: nunca cuánto,
// ni el mínimo, ni otro dato de la gestión. Las imágenes van por nombre de archivo, la principal primero; el modelo
// 3D también, o null sin modelo (HU-16 RF-1), sin su peso
public record ProductoDetalleResponse(Long idProducto, String codigo, String nombre, String marca, Long idCategoria,
		String categoria, BigDecimal precio, UnidadVenta unidad, String descripcion,
		List<CaracteristicaResponse> ficha, List<String> imagenes, boolean disponible, String modelo,
		List<ProductoTarjetaResponse> relacionados) {

	// Lee la categoría y las colecciones: dentro de la transacción
	public static ProductoDetalleResponse from(Producto producto, List<ProductoTarjetaResponse> relacionados) {
		return new ProductoDetalleResponse(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
				producto.getMarca(), producto.getCategoria().getIdCategoria(), producto.getCategoria().getNombre(),
				producto.getPrecio(), producto.getUnidad(), producto.getDescripcion(),
				producto.getFicha().stream().map(CaracteristicaResponse::from).toList(),
				List.copyOf(producto.getImagenes()), producto.getStock().signum() > 0, producto.getModelo(),
				relacionados);
	}
}
