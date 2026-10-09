package bo.edu.uagrm.tienda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import bo.edu.uagrm.tienda.config.ClockConfig;
import bo.edu.uagrm.tienda.config.JwtConfig;
import bo.edu.uagrm.tienda.config.SecurityConfig;
import bo.edu.uagrm.tienda.config.TokenEmitido;
import bo.edu.uagrm.tienda.config.TokenJwt;
import bo.edu.uagrm.tienda.dto.CambioContrasenaRequest;
import bo.edu.uagrm.tienda.dto.DatosPersonalesRequest;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.ContrasenaActualIncorrectaException;
import bo.edu.uagrm.tienda.exception.IntentosExcedidosException;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import bo.edu.uagrm.tienda.service.SesionIniciada;
import bo.edu.uagrm.tienda.service.UsuarioService;

// Autentica con un token real, como SeguridadJwtTest: el id de la cuenta sale del filtro, nunca de la petición
@WebMvcTest(CuentaController.class)
@ActiveProfiles("test")
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class })
class CuentaControllerTest {

	private static final String DATOS = "/api/cuenta/datos-personales";
	private static final String CONTRASENA = "/api/cuenta/contrasena";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TokenJwt tokenJwt;

	@MockitoBean
	private AutenticacionService autenticacionService;

	@MockitoBean
	private UsuarioService usuarioService;

	private final Usuario ana = conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash",
			"70000000", LocalDateTime.of(2026, 9, 12, 11, 0)), 7L);

	private static Usuario conId(Usuario usuario, long id) {
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	@BeforeEach
	void sesionDeAna() {
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(ana));
	}

	private String bearer() {
		return "Bearer " + tokenJwt.emitir(ana).token();
	}

	private MvcTestResult cambiarContrasenaConSesion() {
		return mvc.put().uri(CONTRASENA).header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasenaActual\":\"otra-clave\",\"contrasenaNueva\":\"nueva-clave-1\"}").exchange();
	}

	// HU-04 RF-11
	@Test
	void sinSesionNoSePuedeConsultarActualizarNiCambiarLaContrasena() {
		assertThat(mvc.get().uri(DATOS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put().uri(DATOS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellido\":\"Rojas\"}").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult cambio = mvc.put().uri(CONTRASENA).contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasenaActual\":\"secreta12\",\"contrasenaNueva\":\"nueva-clave-1\"}").exchange();

		assertThat(cambio).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(cambio).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
		then(usuarioService).shouldHaveNoInteractions();
		then(autenticacionService).shouldHaveNoInteractions();
	}

	// HU-04 RF-1
	@Test
	void consultaDevuelveLosDatosDeLaCuentaDeLaSesionSinContrasena() throws Exception {
		given(usuarioService.datosPersonales(7L)).willReturn(ana);

		MvcTestResult resultado = mvc.get().uri(DATOS).header(HttpHeaders.AUTHORIZATION, bearer()).exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().extractingPath("$.telefono").isEqualTo("70000000");
		assertThat(resultado).bodyJson().extractingPath("$.rol").isEqualTo("CLIENTE");
		assertThat(resultado).bodyJson().doesNotHavePath("$.contrasena");
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("$2a$10$hash");
	}

	// HU-04 RF-2, RF-4
	@Test
	void actualizacionValidaUsaLaCuentaDeLaSesionYSoloNombreApellidoYTelefono() {
		given(usuarioService.actualizarDatos(eq(7L), any(DatosPersonalesRequest.class))).willReturn(ana);

		MvcTestResult resultado = mvc.put().uri(DATOS).header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Ana María","apellido":"Rojas Paz","telefono":"71111111","idUsuario":99,"correo":"otra@mail.com","rol":"ADMINISTRADOR"}
						""")
				.exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		ArgumentCaptor<DatosPersonalesRequest> solicitud = ArgumentCaptor.forClass(DatosPersonalesRequest.class);
		then(usuarioService).should().actualizarDatos(eq(7L), solicitud.capture());
		assertThat(solicitud.getValue()).isEqualTo(new DatosPersonalesRequest("Ana María", "Rojas Paz", "71111111"));
	}

	@Test
	void datosInvalidosDevuelven400ConLosCamposSinLlamarAlServicio() {
		MvcTestResult resultado = mvc.put().uri(DATOS).header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"\",\"apellido\":\"%s\",\"telefono\":\"%s\"}".formatted("a".repeat(81),
						"7".repeat(21)))
				.exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("nombre", "apellido", "telefono");
		then(usuarioService).shouldHaveNoInteractions();
	}

	// HU-04 RF-5
	@Test
	void cambioDeContrasenaValidoDevuelveElTokenNuevo() {
		given(autenticacionService.cambiarContrasena(eq(7L), any(CambioContrasenaRequest.class), anyString()))
				.willReturn(new SesionIniciada(ana,
						new TokenEmitido("token.nuevo", Instant.parse("2026-09-15T23:00:00Z"))));

		MvcTestResult resultado = mvc.put().uri(CONTRASENA).header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr("10.0.0.9");
					return peticion;
				})
				.content("{\"contrasenaActual\":\"secreta12\",\"contrasenaNueva\":\"nueva-clave-1\"}").exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.token").isEqualTo("token.nuevo");
		assertThat(resultado).bodyJson().extractingPath("$.tipo").isEqualTo("Bearer");
		assertThat(resultado).bodyJson().extractingPath("$.usuario.correo").isEqualTo("ana@mail.com");
		then(autenticacionService).should().cambiarContrasena(7L,
				new CambioContrasenaRequest("secreta12", "nueva-clave-1"), "10.0.0.9");
	}

	@Test
	void cambioDeContrasenaSinActualYConNuevaCortaDevuelve400ConAmbosCampos() {
		MvcTestResult resultado = mvc.put().uri(CONTRASENA).header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"contrasenaNueva\":\"corta\"}").exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("contrasenaActual", "contrasenaNueva");
		then(autenticacionService).should(never()).cambiarContrasena(any(), any(), any());
	}

	@Test
	void contrasenaActualIncorrectaDevuelve400ConSuCodigo() {
		given(autenticacionService.cambiarContrasena(eq(7L), any(CambioContrasenaRequest.class), anyString()))
				.willThrow(new ContrasenaActualIncorrectaException());

		MvcTestResult resultado = cambiarContrasenaConSesion();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CONTRASENA_ACTUAL_INCORRECTA");
	}

	@Test
	void intentosExcedidosAlCambiarLaContrasenaDevuelven429() {
		given(autenticacionService.cambiarContrasena(eq(7L), any(CambioContrasenaRequest.class), anyString()))
				.willThrow(new IntentosExcedidosException());

		MvcTestResult resultado = cambiarContrasenaConSesion();

		assertThat(resultado).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("INTENTOS_EXCEDIDOS");
	}
}
