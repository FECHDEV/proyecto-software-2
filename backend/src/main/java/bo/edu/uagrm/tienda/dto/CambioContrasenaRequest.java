package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.dto.validacion.MaximoBytesUtf8;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioContrasenaRequest(

		@NotBlank(message = "es obligatoria")
		String contrasenaActual,

		@NotBlank(message = "es obligatoria")
		@Size(min = 8, message = "debe tener al menos 8 caracteres")
		@MaximoBytesUtf8(value = 72, message = "admite hasta 72 bytes")
		String contrasenaNueva) {
}
