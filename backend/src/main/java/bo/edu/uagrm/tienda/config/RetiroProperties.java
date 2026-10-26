package bo.edu.uagrm.tienda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Size;

// Dónde y cuándo se retiran los pedidos (HU-12 RF-17), de TIENDA_RETIRO_DIRECCION y TIENDA_RETIRO_HORARIO. Opcionales:
// sin valor, la confirmación y el pedido no muestran esa parte
@Validated
@ConfigurationProperties("tienda.retiro")
public record RetiroProperties(

		@Size(max = 200, message = "TIENDA_RETIRO_DIRECCION admite hasta 200 caracteres")
		String direccion,

		@Size(max = 200, message = "TIENDA_RETIRO_HORARIO admite hasta 200 caracteres")
		String horario) {

	// Una sola vez para la página y el correo del pedido (HU-21): sin espacios de más, y vacío es como no tenerlo
	public RetiroProperties {
		direccion = textoONulo(direccion);
		horario = textoONulo(horario);
	}

	private static String textoONulo(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}
}
