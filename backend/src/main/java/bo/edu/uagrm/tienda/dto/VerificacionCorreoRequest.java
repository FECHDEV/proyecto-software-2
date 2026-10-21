package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificacionCorreoRequest(

		@NotBlank(message = "es obligatorio")
		String token) {
}
