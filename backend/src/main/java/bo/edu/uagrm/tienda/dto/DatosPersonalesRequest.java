package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Correo, rol, estado y contraseña no están acá: si llegan en la petición se ignoran
// (decisiones.md → Datos personales)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosPersonalesRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String nombre,

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String apellido,

		@Size(max = 20, message = "admite hasta 20 caracteres")
		String telefono) {
}
