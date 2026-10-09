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

class RegistroClienteRequestTest {


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

	private static Set<String> camposInvalidos(RegistroClienteRequest solicitud) {
		return validator.validate(solicitud).stream()
				.map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	private static RegistroClienteRequest conContrasena(String contrasena) {
		return new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", contrasena, null);
	}

	// 4 + 60 + 1 + 4 = 69 caracteres fijos; ninguna etiqueta supera los 63 que admite @Email
	private static String correoDeLongitud(int longitud) {
		return "ana@" + "a".repeat(60) + "." + "b".repeat(longitud - 69) + ".com";
	}

	// HU-01 RF-1
	@Test
	void solicitudCompletaEsValida() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", "secreta12", "70000000");
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}

	// HU-01 RF-3, RF-9
	@Test
	void faltanTodosLosObligatoriosYSeIndicanTodos() {
		var solicitud = new RegistroClienteRequest(null, null, null, null, null);
		assertThat(camposInvalidos(solicitud))
				.containsExactlyInAnyOrder("nombre", "apellido", "correo", "contrasena");
	}

	// HU-01 RF-3
	@Test
	void camposConSoloEspaciosSonIncompletos() {
		var solicitud = new RegistroClienteRequest("   ", "   ", "   ", "secreta12", null);
		assertThat(camposInvalidos(solicitud)).containsExactlyInAnyOrder("nombre", "apellido", "correo");
	}

	// HU-01 RF-5
	@Test
	void correoSinFormatoEsInvalido() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "ana-sin-arroba", "secreta12", null);
		assertThat(camposInvalidos(solicitud)).containsExactly("correo");
	}

	// HU-01 RF-7
	@Test
	void correoSeNormalizaAntesDeValidar() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "  Ana@Mail.com ", "secreta12", null);
		assertThat(solicitud.correo()).isEqualTo("ana@mail.com");
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}

	// HU-01 RF-4, RF-7
	@Test
	void limiteDelCorreoSeAplicaAlValorNormalizado() {
		// "İ" (U+0130) ocupa 2 caracteres en minúsculas: 100 caracteres de entrada quedan en 140
		var correo = "İ".repeat(40) + "@" + "a".repeat(55) + ".com";
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", correo, "secreta12", null);
		assertThat(camposInvalidos(solicitud)).containsExactly("correo");
	}

	// HU-01 RF-6
	@Test
	void contrasenaDe7CaracteresEsInvalidaYDe8Valida() {
		assertThat(camposInvalidos(conContrasena("1234567"))).containsExactly("contrasena");
		assertThat(camposInvalidos(conContrasena("12345678"))).isEmpty();
	}

	// HU-01 RF-6
	@Test
	void contrasenaDe72BytesEsValidaYDe73Invalida() {
		assertThat(camposInvalidos(conContrasena("a".repeat(72)))).isEmpty();
		assertThat(camposInvalidos(conContrasena("a".repeat(73)))).containsExactly("contrasena");
	}

	// HU-01 RF-6
	@Test
	void caracteresMultibyteCuentanPorBytesParaElMaximo() {
		// "ñ" ocupa 2 bytes en UTF-8: 36 = 72 bytes, 37 = 74 bytes
		assertThat(camposInvalidos(conContrasena("ñ".repeat(36)))).isEmpty();
		assertThat(camposInvalidos(conContrasena("ñ".repeat(37)))).containsExactly("contrasena");
	}

	// HU-01 RF-4
	@Test
	void longitudesMaximasDeLaTablaUsuario() {
		var enElLimite = new RegistroClienteRequest("a".repeat(80), "a".repeat(80), correoDeLongitud(120),
				"secreta12", "1".repeat(20));
		var excedida = new RegistroClienteRequest("a".repeat(81), "a".repeat(81), correoDeLongitud(121),
				"secreta12", "1".repeat(21));

		assertThat(camposInvalidos(enElLimite)).isEmpty();
		assertThat(camposInvalidos(excedida)).containsExactlyInAnyOrder("nombre", "apellido", "correo", "telefono");
	}

	// HU-01 RF-10
	@Test
	void telefonoEsOpcional() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", "secreta12", null);
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}
}
