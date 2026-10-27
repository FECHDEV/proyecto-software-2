package bo.edu.uagrm.tienda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

// La cuenta de Gmail del adaptador SMTP (decisiones.md → Notificaciones). Sin el usuario o la contraseña de aplicación
// la API no arranca con smtp, en vez de fallar en cada envío sin que nadie lo note (revisión total, 26/10/2026). La
// registra NotificadorCorreoSmtp, así que solo se exige con ese proveedor
@Validated
@ConfigurationProperties("spring.mail")
public record CredencialesSmtp(
		@NotBlank(message = "Falta TIENDA_CORREO_USUARIO en .env (o TIENDA_CORREO_PROVEEDOR=simulador para probar)")
		String username,

		@NotBlank(message = "Falta TIENDA_CORREO_CONTRASENA en .env (o TIENDA_CORREO_PROVEEDOR=simulador para probar)")
		String password) {

	// La contraseña nunca sale en el registro ni en un mensaje de error
	@Override
	public String toString() {
		return "CredencialesSmtp[username=" + username + "]";
	}
}
