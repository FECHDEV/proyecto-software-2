package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroClienteIntegracionTest {

	private static final String CUERPO = """
			{"nombre":"Ana","apellido":"Rojas","correo":"%s","contrasena":"secreta12"}
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@AfterEach
	void limpiar() {
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
	}

	private MvcTestResult registrar(String correo) {
		return mvc.post().uri("/api/auth/registro").with(ipNueva()).contentType(MediaType.APPLICATION_JSON)
				.content(CUERPO.formatted(correo)).exchange();
	}

	// HU-01 RF-1, RF-7, RF-11
	@Test
	void registroGuardaClienteActivoConCorreoNormalizadoYHashBcrypt() {
		assertThat(registrar(" Ana@Mail.com ")).hasStatus(HttpStatus.CREATED);

		Usuario guardado = usuarioRepository.findByCorreo("ana@mail.com").orElseThrow();
		assertThat(guardado.getCorreo()).isEqualTo("ana@mail.com");
		assertThat(guardado.getRol()).isEqualTo(Rol.CLIENTE);
		assertThat(guardado.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(guardado.getFechaRegistro()).isNotNull();
		assertThat(guardado.getContrasena()).startsWith("$2");
		assertThat(passwordEncoder.matches("secreta12", guardado.getContrasena())).isTrue();
	}

	// HU-01 RF-8
	@Test
	void segundoRegistroConElMismoCorreoEnOtrasMayusculasDevuelve409() {
		assertThat(registrar("ana@mail.com")).hasStatus(HttpStatus.CREATED);
		assertThat(registrar("ANA@MAIL.COM")).hasStatus(HttpStatus.CONFLICT);
		assertThat(usuarioRepository.findAll()).extracting(Usuario::getCorreo).containsOnlyOnce("ana@mail.com");
	}

	// HU-01 RF-2: la sesión que devuelve el registro sirve para usar la cuenta
	@Test
	void tokenDelRegistroAtiendeLaPeticionComoElCliente() throws Exception {
		MvcTestResult registro = registrar("ana@mail.com");
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		String token = JsonPath.read(registro.getResponse().getContentAsString(), "$.token");

		MvcTestResult cuenta = mvc.get().uri("/api/cuenta/datos-personales").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange();

		assertThat(cuenta).hasStatus(HttpStatus.OK);
		assertThat(cuenta).bodyJson().extractingPath("$.correo").isEqualTo("ana@mail.com");
	}
}
