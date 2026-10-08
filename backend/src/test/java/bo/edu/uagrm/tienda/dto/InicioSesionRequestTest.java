package bo.edu.uagrm.tienda.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class InicioSesionRequestTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void crearValidador() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void cerrarValidador() {
		factory.close();
	}

	private static Set<String> camposInvalidos(InicioSesionRequest solicitud) {
		return validator.validate(solicitud).stream()
				.map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void faltanCorreoYContrasenaYSeIndicanAmbos() {
		assertThat(camposInvalidos(new InicioSesionRequest(null, null)))
				.containsExactlyInAnyOrder("correo", "contrasena");
		assertThat(camposInvalidos(new InicioSesionRequest("   ", "")))
				.containsExactlyInAnyOrder("correo", "contrasena");
	}

	@Test
	void correoSeNormalizaYLaContrasenaQuedaTalCual() {
		var solicitud = new InicioSesionRequest(" Ana@Mail.COM ", " Secreta12 ");

		assertThat(solicitud.correo()).isEqualTo("ana@mail.com");
		assertThat(solicitud.contrasena()).isEqualTo(" Secreta12 ");
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}
}
