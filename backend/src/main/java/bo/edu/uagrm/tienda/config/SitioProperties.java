package bo.edu.uagrm.tienda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

// La dirección pública de la tienda, para las direcciones completas del mapa del sitio (decisiones.md → «Buscadores
// y enlaces compartidos»). La misma TIENDA_SITIO_URL que los correos y el pago; nunca el Host de la petición, que lo
// manda el cliente
@Validated
@ConfigurationProperties("tienda.sitio")
public record SitioProperties(@NotBlank String url) {

	public SitioProperties {
		// Sin la barra final, para que las direcciones no queden con dos
		url = url == null ? null : url.replaceAll("/+$", "");
	}
}
