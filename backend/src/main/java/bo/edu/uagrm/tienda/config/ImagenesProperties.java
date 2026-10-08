package bo.edu.uagrm.tienda.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Carpeta donde se guardan las imágenes subidas (decisiones.md → Imágenes subidas)
@ConfigurationProperties("tienda.imagenes")
public record ImagenesProperties(Path directorio) {
}
