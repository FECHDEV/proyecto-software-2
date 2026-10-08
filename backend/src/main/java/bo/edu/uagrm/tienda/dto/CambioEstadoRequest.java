package bo.edu.uagrm.tienda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import jakarta.validation.constraints.NotNull;

// El administrador que opera sale del token: si la petición trae otros campos, se ignoran
@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioEstadoRequest(@NotNull(message = "es obligatorio") EstadoCuenta estado) {
}
