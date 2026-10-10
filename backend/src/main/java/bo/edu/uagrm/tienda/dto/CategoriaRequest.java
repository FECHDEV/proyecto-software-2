package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Los espacios alrededor se recortan antes de validar: un nombre de 60 caracteres con espacios de más entra
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoriaRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 60, message = "admite hasta 60 caracteres")
		String nombre,

		@Size(max = 300, message = "admite hasta 300 caracteres")
		String descripcion) {

	public CategoriaRequest {
		nombre = nombre == null ? null : nombre.strip();
		descripcion = descripcion == null ? null : descripcion.strip();
	}
}
