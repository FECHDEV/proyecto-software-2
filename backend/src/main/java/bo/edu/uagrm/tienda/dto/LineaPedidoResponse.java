package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.LineaPedido;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// Una línea del pedido como se confirmó (HU-12 RF-2): no cambia si el producto cambia después. precio es lo cobrado;
// con descuento (HU-24 RF-15), también el precio normal, el porcentaje y lo ahorrado, nulos sin descuento
public record LineaPedidoResponse(Long idProducto, String nombre, UnidadVenta unidad, BigDecimal precio,
		BigDecimal cantidad, BigDecimal subtotal, BigDecimal precioNormal, Integer descuento, BigDecimal descontado) {

	public static LineaPedidoResponse from(LineaPedido linea) {
		return new LineaPedidoResponse(linea.getIdProducto(), linea.getNombre(), linea.getUnidad(), linea.getPrecio(),
				linea.getCantidad(), linea.getSubtotal(), linea.getPrecioNormal(), linea.getDescuento(),
				linea.getDescontado());
	}
}
