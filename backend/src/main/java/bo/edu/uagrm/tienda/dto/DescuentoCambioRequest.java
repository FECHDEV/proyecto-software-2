package bo.edu.uagrm.tienda.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Editar un descuento programado (HU-24 RF-4): solo el porcentaje y las fechas. A qué se aplica no cambia: si la
// solicitud trae un producto o una categoría, no se leen
@JsonIgnoreProperties(ignoreUnknown = true)
public record DescuentoCambioRequest(

		@NotNull(message = "es obligatorio")
		@Min(value = 1, message = "tiene que estar entre 1 y 90")
		@Max(value = 90, message = "tiene que estar entre 1 y 90")
		Integer porcentaje,

		@NotNull(message = "es obligatorio")
		LocalDate inicio,

		@NotNull(message = "es obligatorio")
		LocalDate fin) {
}
