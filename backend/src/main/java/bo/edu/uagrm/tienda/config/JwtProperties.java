package bo.edu.uagrm.tienda.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tienda.jwt")
public record JwtProperties(String secreto, Duration duracion) {
}
