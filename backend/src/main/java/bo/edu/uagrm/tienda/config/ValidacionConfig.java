package bo.edu.uagrm.tienda.config;

import java.time.Clock;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidacionConfig {

	@Bean
	ValidationConfigurationCustomizer relojDeValidacion(Clock clock) {
		return configuracion -> configuracion.clockProvider(() -> clock);
	}
}
