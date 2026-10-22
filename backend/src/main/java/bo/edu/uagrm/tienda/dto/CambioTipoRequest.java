package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.entity.TipoCliente;
import jakarta.validation.constraints.NotNull;

// El administrador que opera sale del token: si la petición trae otros campos, se ignoran (HU-18 RF-21)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioTipoRequest(@NotNull(message = "es obligatorio") TipoCliente tipo) {
}
