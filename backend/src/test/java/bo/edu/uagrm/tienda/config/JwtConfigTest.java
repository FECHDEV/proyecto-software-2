package bo.edu.uagrm.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withUserConfiguration(JwtConfig.class)
			.withBean(Clock.class, Clock::systemUTC)
			.withPropertyValues("tienda.jwt.duracion=8h");

	@Test
	void sinClaveLaAplicacionNoArranca() {
		contexto.run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void sinLaVariableDeEntornoElErrorIndicaQueHayQueConfigurarla() {
		// Sin la variable, Spring deja el texto "${TIENDA_JWT_SECRETO}" sin resolver en la propiedad
		contexto.withPropertyValues("tienda.jwt.secreto=${TIENDA_JWT_SECRETO_AUSENTE_EN_PRUEBAS}")
				.run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("TIENDA_JWT_SECRETO"));
	}

	@Test
	void conClaveCortaLaAplicacionNoArranca() {
		contexto.withPropertyValues("tienda.jwt.secreto=Y2xhdmUtY29ydGEtZGUtMzEtYnl0ZXMtZXhhY3Rvcw==")
				.run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("TIENDA_JWT_SECRETO"));
	}

	@Test
	void conClaveValidaQuedaDisponibleElEmisorDeTokens() {
		contexto.withPropertyValues("tienda.jwt.secreto=Y2xhdmUtZGUtcHJ1ZWJhcy1wYXJhLXRva2Vucy1qd3QtZXZlbnRvcw==")
				.run(ctx -> assertThat(ctx).hasSingleBean(TokenJwt.class));
	}
}
