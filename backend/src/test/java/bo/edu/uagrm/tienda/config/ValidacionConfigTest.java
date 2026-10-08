package bo.edu.uagrm.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.Validator;

class ValidacionConfigTest {

	// 2000-01-01 02:00 UTC todavía es 1999-12-31 22:00 en La Paz
	private static final Clock RELOJ_LA_PAZ = Clock.fixed(Instant.parse("2000-01-01T02:00:00Z"), ZoneId.of("America/La_Paz"));

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(ValidacionConfig.class)
			.withBean(Clock.class, () -> RELOJ_LA_PAZ);

	// Cualquier @PastOrPresent sirve: lo que se prueba es el reloj con el que se evalúa
	record ConFecha(@PastOrPresent LocalDate fecha) {
	}

	private static ConFecha nacidoEl(LocalDate fecha) {
		return new ConFecha(fecha);
	}

	@Test
	void unaFechaFuturaSeEvaluaConElRelojDeLaPaz() {
		contexto.run(ctx -> {
			Validator validator = ctx.getBean(Validator.class);
			assertThat(validator.validateProperty(nacidoEl(LocalDate.of(2000, 1, 1)), "fecha")).isNotEmpty();
			assertThat(validator.validateProperty(nacidoEl(LocalDate.of(1999, 12, 31)), "fecha")).isEmpty();
		});
	}
}
