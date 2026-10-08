package bo.edu.uagrm.tienda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import bo.edu.uagrm.tienda.config.SecurityConfig;
import bo.edu.uagrm.tienda.config.TokenEmitido;
import bo.edu.uagrm.tienda.config.TokenJwt;
import bo.edu.uagrm.tienda.dto.InicioSesionRequest;
import bo.edu.uagrm.tienda.dto.RegistroClienteRequest;
import bo.edu.uagrm.tienda.dto.RestablecimientoContrasenaRequest;
import bo.edu.uagrm.tienda.dto.SolicitudRecuperacionRequest;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CredencialesIncorrectasException;
import bo.edu.uagrm.tienda.exception.CuentaDesactivadaException;
import bo.edu.uagrm.tienda.exception.CuentaExistenteException;
import bo.edu.uagrm.tienda.exception.IntentosExcedidosException;
import bo.edu.uagrm.tienda.exception.SolicitudesExcedidasException;
import bo.edu.uagrm.tienda.exception.TokenRecuperacionInvalidoException;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import bo.edu.uagrm.tienda.service.RecuperacionContrasenaService;
import bo.edu.uagrm.tienda.service.SesionIniciada;
import bo.edu.uagrm.tienda.service.UsuarioService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	private static final String URL = "/api/auth/registro";

	private static final String CREDENCIALES = "{\"correo\":\"ana@mail.com\",\"contrasena\":\"secreta12\"}";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UsuarioService usuarioService;

	@MockitoBean
	private AutenticacionService autenticacionService;

	@MockitoBean
	private TokenJwt tokenJwt;

	@MockitoBean
	private RecuperacionContrasenaService recuperacionContrasenaService;

	private static Map<String, String> datosValidos() {
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("nombre", "Ana");
		datos.put("apellido", "Rojas");
		datos.put("correo", "ana@mail.com");
		datos.put("contrasena", "secreta12");
		return datos;
	}

	private static String json(Map<String, String> datos) {
		return datos.entrySet().stream()
				.map(campo -> "\"" + campo.getKey() + "\":\"" + campo.getValue() + "\"")
				.collect(Collectors.joining(",", "{", "}"));
	}

	private MvcTestResult registrar(Map<String, String> datos) {
		return mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON).content(json(datos)).exchange();
	}

	private static Usuario clienteRegistrado() {
		return Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null, LocalDateTime.of(2026, 9, 12, 11, 0));
	}

	@Test
	void registroValidoSinSesionDevuelve201SinContrasena() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class))).willReturn(clienteRegistrado());

		MvcTestResult resultado = registrar(datosValidos());

		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().extractingPath("$.rol").isEqualTo("CLIENTE");
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVA");
		assertThat(resultado).bodyJson().doesNotHavePath("$.contrasena");
	}

	@Test
	void rolYEstadoEnviadosEnLaSolicitudSeIgnoran() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class))).willReturn(clienteRegistrado());
		Map<String, String> datos = datosValidos();
		datos.put("rol", "ADMINISTRADOR");
		datos.put("estado", "DESACTIVADA");

		assertThat(registrar(datos)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void correoLlegaNormalizadoAlServicio() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class))).willReturn(clienteRegistrado());
		Map<String, String> datos = datosValidos();
		datos.put("correo", "  Ana@Mail.com ");

		assertThat(registrar(datos)).hasStatus(HttpStatus.CREATED);

		ArgumentCaptor<RegistroClienteRequest> solicitud = ArgumentCaptor.forClass(RegistroClienteRequest.class);
		then(usuarioService).should().registrarCliente(solicitud.capture());
		assertThat(solicitud.getValue().correo()).isEqualTo("ana@mail.com");
	}

	@Test
	void datosInvalidosDevuelven400ConTodosLosCamposSinLlamarAlServicio() {
		Map<String, String> datos = datosValidos();
		datos.remove("nombre");
		datos.put("correo", "ana-sin-arroba");
		datos.put("contrasena", "corta");

		MvcTestResult resultado = registrar(datos);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("nombre", "correo", "contrasena");
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void contrasenaDeMasDe72BytesDevuelve400() {
		Map<String, String> datos = datosValidos();
		datos.put("contrasena", "a".repeat(73));

		assertThat(registrar(datos)).hasStatus(HttpStatus.BAD_REQUEST);
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void jsonMalFormadoDevuelveDatosInvalidos() {
		MvcTestResult resultado = mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":").exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void cuentaExistenteDevuelve409() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class)))
				.willThrow(new CuentaExistenteException());

		MvcTestResult resultado = registrar(datosValidos());

		assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_EXISTENTE");
	}

	@Test
	void preflightDesdeElFrontendEstaPermitido() {
		MvcTestResult resultado = mvc.options().uri(URL)
				.header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST")
				.exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).headers().hasValue("Access-Control-Allow-Origin", "http://localhost:4200");
	}

	private MvcTestResult iniciarSesion(String cuerpo) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.exchange();
	}

	private static SesionIniciada sesionDeAna() {
		return new SesionIniciada(clienteRegistrado(),
				new TokenEmitido("token.de.prueba", Instant.parse("2026-09-14T23:00:00Z")));
	}

	@Test
	void inicioDeSesionValidoSinSesionDevuelveTokenYDatosSinContrasena() throws Exception {
		given(autenticacionService.iniciarSesion(any(InicioSesionRequest.class), anyString())).willReturn(sesionDeAna());

		MvcTestResult resultado = iniciarSesion(CREDENCIALES);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.token").isEqualTo("token.de.prueba");
		assertThat(resultado).bodyJson().extractingPath("$.tipo").isEqualTo("Bearer");
		assertThat(resultado).bodyJson().extractingPath("$.expiracion").isEqualTo("2026-09-14T23:00:00Z");
		assertThat(resultado).bodyJson().extractingPath("$.usuario.correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().extractingPath("$.usuario.rol").isEqualTo("CLIENTE");
		assertThat(resultado).bodyJson().doesNotHavePath("$.usuario.contrasena");
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("$2a$10$hash");
	}

	@Test
	void correoLlegaNormalizadoAlServicioDeAutenticacion() {
		given(autenticacionService.iniciarSesion(any(InicioSesionRequest.class), anyString())).willReturn(sesionDeAna());

		assertThat(iniciarSesion("{\"correo\":\" Ana@Mail.COM \",\"contrasena\":\"secreta12\"}"))
				.hasStatus(HttpStatus.OK);

		ArgumentCaptor<InicioSesionRequest> solicitud = ArgumentCaptor.forClass(InicioSesionRequest.class);
		then(autenticacionService).should().iniciarSesion(solicitud.capture(), anyString());
		assertThat(solicitud.getValue().correo()).isEqualTo("ana@mail.com");
	}

	@Test
	void direccionIpDelClienteLlegaAlServicioDeAutenticacion() {
		given(autenticacionService.iniciarSesion(any(InicioSesionRequest.class), anyString())).willReturn(sesionDeAna());

		assertThat(mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr("10.0.0.9");
					return peticion;
				})
				.content(CREDENCIALES).exchange()).hasStatus(HttpStatus.OK);

		then(autenticacionService).should().iniciarSesion(any(InicioSesionRequest.class), eq("10.0.0.9"));
	}

	@Test
	void intentosExcedidosDevuelven429() {
		given(autenticacionService.iniciarSesion(any(InicioSesionRequest.class), anyString()))
				.willThrow(new IntentosExcedidosException());

		MvcTestResult resultado = iniciarSesion(CREDENCIALES);

		assertThat(resultado).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("INTENTOS_EXCEDIDOS");
	}

	@Test
	void inicioDeSesionSinCorreoNiContrasenaDevuelve400SinLlamarAlServicio() {
		MvcTestResult resultado = iniciarSesion("{}");

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("correo", "contrasena");
		then(autenticacionService).shouldHaveNoInteractions();
	}

	@Test
	void credencialesIncorrectasDevuelven401() {
		given(autenticacionService.iniciarSesion(any(InicioSesionRequest.class), anyString()))
				.willThrow(new CredencialesIncorrectasException());

		MvcTestResult resultado = iniciarSesion(CREDENCIALES);

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CREDENCIALES_INCORRECTAS");
		assertThat(resultado).bodyJson().extractingPath("$.detail").isEqualTo("Correo o contraseña incorrectos.");
	}

	@Test
	void cuentaDesactivadaDevuelve403() {
		given(autenticacionService.iniciarSesion(any(InicioSesionRequest.class), anyString()))
				.willThrow(new CuentaDesactivadaException());

		MvcTestResult resultado = iniciarSesion(CREDENCIALES);

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_DESACTIVADA");
	}

	private MvcTestResult solicitarRecuperacion(String cuerpo) {
		return mvc.post().uri("/api/auth/recuperacion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr("10.0.0.9");
					return peticion;
				})
				.content(cuerpo).exchange();
	}

	private MvcTestResult restablecerContrasena(String cuerpo) {
		return mvc.post().uri("/api/auth/recuperacion/restablecimiento").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo).exchange();
	}

	@Test
	void solicitudDeRecuperacionSinSesionDevuelve202ConElMensajeDeConfirmacion() {
		MvcTestResult resultado = solicitarRecuperacion("{\"correo\":\" Ana@Mail.COM \"}");

		assertThat(resultado).hasStatus(HttpStatus.ACCEPTED);
		assertThat(resultado).bodyJson().extractingPath("$.mensaje")
				.isEqualTo("Si el correo está registrado, le enviamos las instrucciones para recuperar la contraseña.");
		ArgumentCaptor<SolicitudRecuperacionRequest> solicitud = ArgumentCaptor
				.forClass(SolicitudRecuperacionRequest.class);
		then(recuperacionContrasenaService).should().solicitar(solicitud.capture(), eq("10.0.0.9"));
		assertThat(solicitud.getValue().correo()).isEqualTo("ana@mail.com");
	}

	@Test
	void solicitudDeRecuperacionConCorreoInvalidoDevuelve400SinLlamarAlServicio() {
		MvcTestResult resultado = solicitarRecuperacion("{\"correo\":\"ana-sin-arroba\"}");

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactly("correo");
		then(recuperacionContrasenaService).shouldHaveNoInteractions();
	}

	@Test
	void solicitudesDeRecuperacionExcedidasDevuelven429() {
		willThrow(new SolicitudesExcedidasException()).given(recuperacionContrasenaService)
				.solicitar(any(SolicitudRecuperacionRequest.class), anyString());

		MvcTestResult resultado = solicitarRecuperacion("{\"correo\":\"ana@mail.com\"}");

		assertThat(resultado).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("SOLICITUDES_EXCEDIDAS");
	}

	@Test
	void restablecimientoValidoSinSesionDevuelve200ConLaConfirmacion() {
		MvcTestResult resultado = restablecerContrasena(
				"{\"token\":\"token-del-correo\",\"contrasena\":\"nueva-clave-1\"}");

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.mensaje")
				.isEqualTo("La contraseña se actualizó. Ya puede iniciar sesión con la nueva contraseña.");
		ArgumentCaptor<RestablecimientoContrasenaRequest> solicitud = ArgumentCaptor
				.forClass(RestablecimientoContrasenaRequest.class);
		then(recuperacionContrasenaService).should().restablecer(solicitud.capture());
		assertThat(solicitud.getValue().token()).isEqualTo("token-del-correo");
		assertThat(solicitud.getValue().contrasena()).isEqualTo("nueva-clave-1");
	}

	@Test
	void restablecimientoSinTokenYConContrasenaCortaDevuelve400ConAmbosCampos() {
		MvcTestResult resultado = restablecerContrasena("{\"contrasena\":\"corta\"}");

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("token", "contrasena");
		then(recuperacionContrasenaService).shouldHaveNoInteractions();
	}

	@Test
	void restablecimientoConContrasenaDeMasDe72BytesDevuelve400() {
		assertThat(restablecerContrasena("{\"token\":\"t\",\"contrasena\":\"%s\"}".formatted("a".repeat(73))))
				.hasStatus(HttpStatus.BAD_REQUEST);
		then(recuperacionContrasenaService).shouldHaveNoInteractions();
	}

	@Test
	void tokenDeRecuperacionInvalidoDevuelve400ConSuCodigo() {
		willThrow(new TokenRecuperacionInvalidoException()).given(recuperacionContrasenaService)
				.restablecer(any(RestablecimientoContrasenaRequest.class));

		MvcTestResult resultado = restablecerContrasena("{\"token\":\"vencido\",\"contrasena\":\"nueva-clave-1\"}");

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("TOKEN_RECUPERACION_INVALIDO");
	}
}
