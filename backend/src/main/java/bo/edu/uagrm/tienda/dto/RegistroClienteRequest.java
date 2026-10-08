package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.dto.validacion.MaximoBytesUtf8;
import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistroClienteRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String nombre,

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String apellido,

		@NotBlank(message = "es obligatorio")
		@Email(message = "no tiene formato de correo")
		@Size(max = 120, message = "admite hasta 120 caracteres")
		String correo,

		@NotBlank(message = "es obligatoria")
		@Size(min = 8, message = "debe tener al menos 8 caracteres")
		@MaximoBytesUtf8(value = 72, message = "admite hasta 72 bytes")
		String contrasena,

		@Size(max = 20, message = "admite hasta 20 caracteres")
		String telefono) {

	public RegistroClienteRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
