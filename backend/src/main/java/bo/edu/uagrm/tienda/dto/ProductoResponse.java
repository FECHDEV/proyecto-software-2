package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Un producto completo, para el formulario de la gestión. Las imágenes van por nombre de archivo, en orden: la
// primera es la principal. Lleva el costo: solo lo lee el Administrador (HU-18 RF-19)
public record ProductoResponse(Long idProducto, String codigo, String nombre, String marca, String descripcion,
		BigDecimal precio, BigDecimal costo, UnidadVenta unidad, Long idCategoria, String categoria, boolean activo,
		LocalDateTime fechaAlta, List<CaracteristicaResponse> ficha, List<String> imagenes, boolean destacado) {

	// Lee la categoría y las colecciones: dentro de la transacción
	public static ProductoResponse from(Producto producto) {
		return new ProductoResponse(producto.getIdProducto(), producto.getCodigo(), producto.getNombre(),
				producto.getMarca(), producto.getDescripcion(), producto.getPrecio(), producto.getCosto(), producto.getUnidad(),
				producto.getCategoria().getIdCategoria(), producto.getCategoria().getNombre(), producto.isActivo(),
				producto.getFechaAlta(), producto.getFicha().stream().map(CaracteristicaResponse::from).toList(),
				List.copyOf(producto.getImagenes()), producto.isDestacado());
	}
}
