package bo.edu.uagrm.tienda.dto;

import java.util.Objects;

import bo.edu.uagrm.tienda.entity.Producto;

// El modelo 3D de un producto (HU-15): el archivo que generó AlmacenImagenes y su peso en bytes, o null sin modelo.
// Un peso que falta (la fila editada a mano) no rompe el producto: se muestra 0 (/code-review)
public record ModeloResponse(String archivo, long peso) {

	public static ModeloResponse from(Producto producto) {
		return producto.getModelo() == null ? null
				: new ModeloResponse(producto.getModelo(), Objects.requireNonNullElse(producto.getPesoModelo(), 0L));
	}
}
