package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.dto.validacion.CantidadDeCarrito;
import jakarta.validation.constraints.NotNull;

// Una línea de la venta del mostrador (HU-23 RF-2, RF-13): como en el carrito, mayor que 0, hasta 999 y con dos
// decimales como máximo. Los decimales según la unidad los revisa VentaTiendaService
public record LineaVentaRequest(

		@NotNull(message = "es obligatorio")
		Long idProducto,

		@CantidadDeCarrito
		BigDecimal cantidad) {
}
