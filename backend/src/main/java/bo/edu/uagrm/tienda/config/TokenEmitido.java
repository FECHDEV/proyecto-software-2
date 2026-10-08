package bo.edu.uagrm.tienda.config;

import java.time.Instant;

public record TokenEmitido(String token, Instant expiracion) {
}
