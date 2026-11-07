package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.entity.MedioPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Registrar una venta del mostrador (HU-23 RF-5 a RF-7, RF-13, RF-14). idCliente: nulo para el consumidor final.
// recibido: solo en efectivo (que no sea menor que el total lo revisa VentaTiendaService). El total esperado es el que
// vio la pantalla: solo se compara, la venta se arma con los precios del servidor. Quien vende sale del token
@JsonIgnoreProperties(ignoreUnknown = true)
public record VentaRequest(

		@NotEmpty(message = "lleva al menos un producto")
		@Size(max = 50, message = "admite hasta 50 productos")
		List<@Valid @NotNull(message = "es obligatoria") LineaVentaRequest> lineas,

		Long idCliente,

		@NotNull(message = "es obligatorio")
		MedioPago medio,

		@DecimalMin(value = "0", message = "no puede ser negativo")
		@DecimalMax(value = "999999.99", message = "admite hasta 999 999,99")
		@Digits(integer = 6, fraction = 2, message = "admite hasta dos decimales")
		BigDecimal recibido,

		@NotNull(message = "es obligatorio")
		BigDecimal totalEsperado) {
}
