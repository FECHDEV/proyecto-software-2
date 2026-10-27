package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.dto.validacion.MaximoBytesUtf8;
import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Con los mismos topes que el registro: un correo de varios MB quedaría 15 minutos como clave del límite de intentos
// (revisión total, 26/10/2026), y ninguna contraseña válida pasa de 72 bytes
@JsonIgnoreProperties(ignoreUnknown = true)
public record InicioSesionRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 120, message = "admite hasta 120 caracteres")
		String correo,

		@NotBlank(message = "es obligatoria")
		@MaximoBytesUtf8(value = 72, message = "admite hasta 72 bytes")
		String contrasena) {

	public InicioSesionRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
