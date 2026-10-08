package bo.edu.uagrm.tienda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import bo.edu.uagrm.tienda.dto.validacion.MaximoBytesUtf8;
import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Las reglas se validan en AdministradorInicial y solo cuando no hay administrador: por eso no lleva @Validated
@ConfigurationProperties("tienda.admin")
public record AdministradorInicialProperties(

		@NotBlank(message = "TIENDA_ADMIN_CORREO es obligatorio")
		@Email(message = "TIENDA_ADMIN_CORREO no tiene formato de correo")
		@Size(max = 120, message = "TIENDA_ADMIN_CORREO admite hasta 120 caracteres")
		String correo,

		@NotBlank(message = "TIENDA_ADMIN_PASSWORD es obligatoria")
		@Size(min = 8, message = "TIENDA_ADMIN_PASSWORD debe tener al menos 8 caracteres")
		@MaximoBytesUtf8(value = 72, message = "TIENDA_ADMIN_PASSWORD admite hasta 72 bytes")
		String contrasena) {

	public AdministradorInicialProperties {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
