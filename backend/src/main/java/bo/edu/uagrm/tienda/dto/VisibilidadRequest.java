package bo.edu.uagrm.tienda.dto;

import jakarta.validation.constraints.NotNull;

public record VisibilidadRequest(@NotNull(message = "es obligatorio") Boolean visible) {
}
