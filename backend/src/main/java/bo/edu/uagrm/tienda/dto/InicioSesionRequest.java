package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InicioSesionRequest(

		@NotBlank(message = "es obligatorio")
		String correo,

		@NotBlank(message = "es obligatoria")
		String contrasena) {

	public InicioSesionRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
