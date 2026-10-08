package bo.edu.uagrm.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.service.AutenticacionService;

@WebMvcTest(controllers = SeguridadJwtTest.RecursoDePrueba.class)
@ActiveProfiles("test")
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, SeguridadJwtTest.RecursoDePrueba.class })
class SeguridadJwtTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TokenJwt tokenJwt;

	@Autowired
	private JwtProperties propiedades;

	@MockitoBean
	private AutenticacionService autenticacionService;

	@RestController
	public static class RecursoDePrueba {

		@GetMapping("/api/prueba/sesion")
		public Map<String, Object> sesion(Authentication autenticacion) {
			return Map.of("idUsuario", autenticacion.getPrincipal(), "roles",
					autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
		}

		@GetMapping("/api/prueba/prohibido")
		public void prohibido() {
			throw new AccessDeniedException("Reservado para otro rol");
		}
	}

	private static Usuario conId(Usuario usuario, long id) {
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	private static Usuario cliente(long id) {
		return conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null, LocalDateTime.of(2026, 9, 12, 11, 0)), id);
	}

	private String bearer(Usuario usuario) {
		return "Bearer " + tokenJwt.emitir(usuario).token();
	}

	private MvcTestResult pedir(String uri, String authorization) {
		return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization).exchange();
	}

	@Test
	void peticionSinTokenDevuelve401NoAutenticado() {
		MvcTestResult resultado = mvc.get().uri("/api/prueba/sesion").exchange();

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).headers().hasValue(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
		then(autenticacionService).shouldHaveNoInteractions();
	}

	@Test
	void tokenValidoDeCuentaActivaAtiendeLaPeticionComoEseUsuario() {
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(cliente(7)));

		MvcTestResult resultado = pedir("/api/prueba/sesion", bearer(cliente(7)));

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.idUsuario").isEqualTo(7);
		assertThat(resultado).bodyJson().extractingPath("$.roles").asArray().containsExactly("ROLE_CLIENTE");
	}

	@Test
	void rolSeTomaDeLaCuentaYNoDelToken() {
		Usuario ahoraAdministrador = conId(Usuario.crearAdministrador("Ana", "Rojas", "ana@mail.com", "$2a$10$hash",
				LocalDateTime.of(2026, 9, 12, 11, 0)), 7);
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(ahoraAdministrador));

		MvcTestResult resultado = pedir("/api/prueba/sesion", bearer(cliente(7)));

		assertThat(resultado).bodyJson().extractingPath("$.roles").asArray().containsExactly("ROLE_ADMINISTRADOR");
	}

	@Test
	void cuentaDesactivadaOInexistenteDevuelve401AunqueElTokenSigaVigente() {
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.empty());

		MvcTestResult resultado = pedir("/api/prueba/sesion", bearer(cliente(7)));

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
		then(autenticacionService).should().usuarioActivo(7L);
	}

	@Test
	void contrasenaCambiadaDespuesDeEmitirElTokenDevuelve401() {
		Usuario conContrasenaNueva = cliente(7);
		ReflectionTestUtils.setField(conContrasenaNueva, "contrasena", "$2a$10$hashDeOtraContrasena");
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(conContrasenaNueva));

		MvcTestResult resultado = pedir("/api/prueba/sesion", bearer(cliente(7)));

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
	}

	@Test
	void tokenVencidoOInvalidoDevuelve401() {
		String vencido = new TokenJwt(propiedades,
				Clock.fixed(Instant.now().minus(Duration.ofHours(9)), ZoneId.of("America/La_Paz")))
				.emitir(cliente(7)).token();

		assertThat(pedir("/api/prueba/sesion", "Bearer " + vencido)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(pedir("/api/prueba/sesion", "Bearer basura")).hasStatus(HttpStatus.UNAUTHORIZED);
		then(autenticacionService).shouldHaveNoInteractions();
	}

	@Test
	void tokenSinPrefijoBearerSeIgnora() {
		MvcTestResult resultado = pedir("/api/prueba/sesion", tokenJwt.emitir(cliente(7)).token());

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		then(autenticacionService).shouldHaveNoInteractions();
	}

	@Test
	void rutaInexistenteConSesionDevuelve404() {
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(cliente(7)));

		assertThat(pedir("/api/no-existe", bearer(cliente(7)))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void accesoDenegadoDevuelve403ConCodigo() {
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(cliente(7)));

		MvcTestResult resultado = pedir("/api/prueba/prohibido", bearer(cliente(7)));

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
	}
}
