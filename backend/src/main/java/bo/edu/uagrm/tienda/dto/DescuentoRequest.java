package bo.edu.uagrm.tienda.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Un descuento nuevo (HU-24 RF-1): un producto o una categoría (uno solo, lo valida DescuentoService), un porcentaje
// entero y las fechas. 12.5 no llega como 12: accept-float-as-int está apagado (application.properties). Para editar
// (RF-4), DescuentoCambioRequest
@JsonIgnoreProperties(ignoreUnknown = true)
public record DescuentoRequest(

		Long idProducto,

		Long idCategoria,

		@NotNull(message = "es obligatorio")
		@Min(value = 1, message = "tiene que estar entre 1 y 90")
		@Max(value = 90, message = "tiene que estar entre 1 y 90")
		Integer porcentaje,

		@NotNull(message = "es obligatorio")
		LocalDate inicio,

		@NotNull(message = "es obligatorio")
		LocalDate fin) {
}
