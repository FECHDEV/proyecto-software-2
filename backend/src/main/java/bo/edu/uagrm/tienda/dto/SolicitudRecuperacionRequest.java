package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SolicitudRecuperacionRequest(

		@NotBlank(message = "es obligatorio")
		@Email(message = "no tiene formato de correo")
		String correo) {

	public SolicitudRecuperacionRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
