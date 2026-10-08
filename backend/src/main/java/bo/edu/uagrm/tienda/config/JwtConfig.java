package bo.edu.uagrm.tienda.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	@Bean
	TokenJwt tokenJwt(JwtProperties propiedades, Clock clock) {
		return new TokenJwt(propiedades, clock);
	}
}
