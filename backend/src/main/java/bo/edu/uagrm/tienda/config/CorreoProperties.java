package bo.edu.uagrm.tienda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

// Marca que firma los correos, remitente y dirección pública del sitio, para armar los enlaces (decisiones.md →
// 'Notificaciones'). Sin la URL, los enlaces quedarían sin sitio: la aplicación no arranca
@Validated
@ConfigurationProperties("tienda.correo")
public record CorreoProperties(@NotBlank String marca, @NotBlank String remitente, @NotBlank String urlDelSitio) {

	public CorreoProperties {
		// Sin la barra final, para que los enlaces no queden con dos
		urlDelSitio = urlDelSitio == null ? null : urlDelSitio.replaceAll("/+$", "");
	}
}
