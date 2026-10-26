package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;

// Lo que llevan los correos del pedido (HU-21), copiado dentro de la transacción del pago: el envío corre en segundo
// plano, sin sesión de Hibernate, y no puede leer las líneas del pedido
public record CorreoDelPedido(String correo, String nombre, Long numero, LocalDateTime fechaPago, List<Linea> lineas,
		BigDecimal total, String nombreRetira, String telefonoRetira) {

	public record Linea(String nombre, UnidadVenta unidad, BigDecimal cantidad, BigDecimal precio,
			BigDecimal subtotal) {
	}

	public CorreoDelPedido {
		lineas = List.copyOf(lineas);
	}

	// El correo de la cuenta al momento del pago, esté activa o no (HU-21 RF-1)
	static CorreoDelPedido de(Pedido pedido, Usuario usuario) {
		List<Linea> lineas = pedido.getLineas().stream()
				.map(linea -> new Linea(linea.getNombre(), linea.getUnidad(), linea.getCantidad(), linea.getPrecio(),
						linea.getSubtotal()))
				.toList();
		return new CorreoDelPedido(usuario.getCorreo(), usuario.getNombre(), pedido.getIdPedido(), pedido.getPagado(),
				lineas, pedido.getTotal(), pedido.getNombreRetira(), pedido.getTelefonoRetira());
	}
}
