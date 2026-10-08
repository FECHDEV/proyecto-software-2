package bo.edu.uagrm.tienda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InicioSesionIntegracionTest {

	private static final String REGISTRO_DE_ANA = """
			{"nombre":"Ana","apellido":"Rojas","correo":"ana@mail.com","contrasena":"secreta12"}
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationContext contexto;

	@BeforeEach
	void registrarAna() {
		assertThat(mvc.post().uri("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(REGISTRO_DE_ANA).exchange()).hasStatus(HttpStatus.CREATED);
	}

	@AfterEach
	void limpiar() {
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
	}

	private MvcTestResult iniciarSesion(String correo, String contrasena) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
	}

	private static String token(MvcTestResult sesion) throws Exception {
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	private MvcTestResult pedirRutaProtegida(String token) {
		return mvc.get().uri("/api/no-existe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	@Test
	void clienteRegistradoIniciaSesionYUsaElTokenEnLasPeticionesSiguientes() throws Exception {
		MvcTestResult sesion = iniciarSesion(" Ana@Mail.COM ", "secreta12");

		assertThat(sesion).hasStatus(HttpStatus.OK);
		assertThat(sesion).bodyJson().extractingPath("$.usuario.rol").isEqualTo("CLIENTE");
		assertThat(mvc.get().uri("/api/no-existe").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(pedirRutaProtegida(token(sesion))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void contrasenaIncorrectaDevuelveCredencialesIncorrectas() {
		MvcTestResult resultado = iniciarSesion("ana@mail.com", "Secreta12");

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CREDENCIALES_INCORRECTAS");
	}

	@Test
	void administradorInicialIniciaSesionConLasVariablesDelPerfil() {
		// UsuarioRepositoryTest comparte la H2 en memoria y recrea el esquema al arrancar su contexto, lo que
		// puede borrar el administrador creado al arrancar este. Repetir el arranque es idempotente.
		administradorInicial.run(new DefaultApplicationArguments());

		MvcTestResult resultado = iniciarSesion("admin@tienda.test", "clave-admin-pruebas");

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.usuario.rol").isEqualTo("ADMINISTRADOR");
		assertThat(resultado).bodyJson().extractingPath("$.usuario.nombre").isEqualTo("Administrador");
	}

	@Test
	void cuentaDesactivadaNoIniciaSesionYSuTokenAnteriorDejaDeValer() throws Exception {
		String tokenAnterior = token(iniciarSesion("ana@mail.com", "secreta12"));
		// Se desactiva directo en la base, sin pasar por la gestión de usuarios, que se prueba aparte
		jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA' WHERE correo = ?", "ana@mail.com");

		MvcTestResult resultado = iniciarSesion("ana@mail.com", "secreta12");

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_DESACTIVADA");
		assertThat(pedirRutaProtegida(tokenAnterior)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void cambioDeContrasenaInvalidaElTokenAnterior() throws Exception {
		String tokenAnterior = token(iniciarSesion("ana@mail.com", "secreta12"));
		// La recuperación y Mis datos agregan el cambio de contraseña; mientras tanto se reemplaza el hash como en la prueba manual
		String hashNuevo = contexto.getBean(PasswordEncoder.class).encode("secreta12");
		jdbc.update("UPDATE usuario SET contrasena = ? WHERE correo = ?", hashNuevo, "ana@mail.com");

		assertThat(pedirRutaProtegida(tokenAnterior)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(pedirRutaProtegida(token(iniciarSesion("ana@mail.com", "secreta12"))))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void cincoIntentosFallidosBloqueanElCorreoSoloDesdeEsaIp() {
		// Correo propio de este test: los contadores del límite viven en el contexto compartido
		String correo = "bloqueo@mail.com";
		for (int i = 0; i < 5; i++) {
			assertThat(iniciarSesion(correo, "otra-clave")).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		MvcTestResult bloqueado = iniciarSesion(correo, "otra-clave");
		MvcTestResult desdeOtraIp = mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr("10.0.0.2");
					return peticion;
				})
				.content("{\"correo\":\"%s\",\"contrasena\":\"otra-clave\"}".formatted(correo)).exchange();

		assertThat(bloqueado).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(bloqueado).bodyJson().extractingPath("$.codigo").isEqualTo("INTENTOS_EXCEDIDOS");
		assertThat(desdeOtraIp).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokenInvalidoEnElHeaderNoImpideIniciarSesion() {
		MvcTestResult resultado = mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer token-viejo-o-invalido")
				.content("{\"correo\":\"ana@mail.com\",\"contrasena\":\"secreta12\"}").exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
	}

	@Test
	void noSeCreaElUsuarioEnMemoriaConContrasenaGenerada() {
		assertThat(contexto.getBeansOfType(UserDetailsService.class)).isEmpty();
	}
}
