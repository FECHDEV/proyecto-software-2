package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.entity.UnidadVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Alta y edición de un producto (HU-07 RF-3, RF-6). Los textos se recortan y los pares vacíos de la ficha se
// descartan antes de validar
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 120, message = "admite hasta 120 caracteres")
		String nombre,

		@Size(max = 60, message = "admite hasta 60 caracteres")
		String marca,

		@Size(max = 1000, message = "admite hasta 1000 caracteres")
		String descripcion,

		@NotNull(message = "es obligatoria")
		Long idCategoria,

		@NotNull(message = "es obligatoria")
		UnidadVenta unidad,

		@NotNull(message = "es obligatorio")
		@DecimalMin(value = "0", inclusive = false, message = "tiene que ser mayor que 0")
		@DecimalMax(value = "99999.99", message = "admite hasta 99 999,99")
		@Digits(integer = 5, fraction = 2, message = "admite hasta dos decimales")
		BigDecimal precio,

		// Opcional: sin costo, el producto no entra en el margen (HU-18 RF-19)
		@DecimalMin(value = "0", inclusive = false, message = "tiene que ser mayor que 0")
		@DecimalMax(value = "99999.99", message = "admite hasta 99 999,99")
		@Digits(integer = 5, fraction = 2, message = "admite hasta dos decimales")
		BigDecimal costo,

		@Size(max = 15, message = "admite hasta 15 características")
		List<@Valid CaracteristicaRequest> ficha,

		// Sale en «Destacados» de la portada (HU-09 RF-4); sin decirlo, no
		Boolean destacado) {

	public ProductoRequest {
		nombre = nombre == null ? null : nombre.strip();
		marca = marca == null ? null : marca.strip();
		descripcion = descripcion == null ? null : descripcion.strip();
		destacado = Boolean.TRUE.equals(destacado);
		ficha = ficha == null ? List.of()
				: ficha.stream().filter(Objects::nonNull).filter(par -> !par.estaVacia()).toList();
	}
}
