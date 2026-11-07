package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Anular una venta del mostrador (HU-23 RF-18): el motivo, obligatorio y de hasta 200 caracteres sin los espacios de
// alrededor. Quien anula sale del token
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnulacionRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 200, message = "admite hasta 200 caracteres")
		String motivo) {

	public AnulacionRequest {
		motivo = motivo == null ? null : motivo.strip();
	}
}
