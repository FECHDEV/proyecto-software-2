package bo.edu.uagrm.tienda.dto;

import java.time.LocalDate;

import bo.edu.uagrm.tienda.entity.Descuento;
import bo.edu.uagrm.tienda.entity.EstadoDescuento;

// Un descuento en la gestión (HU-24 RF-3): a qué se aplica, el porcentaje, las fechas y cómo está hoy. Sin quién lo
// creó: no hace falta en la pantalla
public record DescuentoResponse(Long idDescuento, Long idProducto, String producto, Long idCategoria,
		String categoria, int porcentaje, LocalDate inicio, LocalDate fin, EstadoDescuento estado) {

	// Lee el producto o la categoría: dentro de la transacción
	public static DescuentoResponse from(Descuento descuento, LocalDate hoy) {
		boolean deProducto = descuento.getProducto() != null;
		return new DescuentoResponse(descuento.getIdDescuento(),
				deProducto ? descuento.getProducto().getIdProducto() : null,
				deProducto ? descuento.getProducto().getNombre() : null,
				deProducto ? null : descuento.getCategoria().getIdCategoria(),
				deProducto ? null : descuento.getCategoria().getNombre(), descuento.getPorcentaje(),
				descuento.getInicio(), descuento.getFin(), descuento.estado(hoy));
	}
}
