package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.dto.SolicitudRecuperacionRequest;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.NotificadorCorreo;
import bo.edu.uagrm.tienda.service.RecuperacionContrasenaService;
import bo.edu.uagrm.tienda.service.TokenRecuperacion;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperacionContrasenaIntegracionTest {

	private static final String REGISTRO = """
			{"nombre":"Ana","apellido":"Rojas","correo":"%s","contrasena":"secreta12"}
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RecuperacionContrasenaService recuperacionContrasenaService;

	// Deja pasar la llamada al simulador y permite leer el token, que la base no guarda
	@MockitoSpyBean
	private NotificadorCorreo notificadorCorreo;

	@BeforeEach
	void registrarAna() {
		registrar("ana@mail.com");
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM recuperacion_contrasena");
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
		usuarioRepository.findByCorreo("beto@mail.com").ifPresent(usuarioRepository::delete);
	}

	private void registrar(String correo) {
		assertThat(mvc.post().uri("/api/auth/registro").with(ipNueva()).contentType(MediaType.APPLICATION_JSON)
				.content(REGISTRO.formatted(correo)).exchange()).hasStatus(HttpStatus.CREATED);
	}

	// Cada test usa su propia IP: el límite de solicitudes vive en el contexto compartido y cuenta por correo e IP
	private MvcTestResult solicitar(String correo, String ip) {
		return mvc.post().uri("/api/auth/recuperacion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr(ip);
					return peticion;
				})
				.content("{\"correo\":\"%s\"}".formatted(correo)).exchange();
	}

	private String ultimoTokenEnviadoA(String correo) {
		ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
		then(notificadorCorreo).should(atLeastOnce()).enviarRecuperacion(eq(correo), anyString(), token.capture());
		return token.getValue();
	}

	private MvcTestResult restablecer(String token, String contrasena) {
		return mvc.post().uri("/api/auth/recuperacion/restablecimiento").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"contrasena\":\"%s\"}".formatted(token, contrasena)).exchange();
	}

	private MvcTestResult iniciarSesion(String correo, String contrasena) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
	}

	private MvcTestResult pedirRutaProtegida(String tokenDeSesion) {
		return mvc.get().uri("/api/no-existe").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeSesion).exchange();
	}

	@Test
	void flujoCompletoCambiaLaContrasenaYElTokenNoSePuedeReutilizar() {
		assertThat(solicitar(" Ana@Mail.COM ", "10.1.0.1")).hasStatus(HttpStatus.ACCEPTED);
		String token = ultimoTokenEnviadoA("ana@mail.com");

		assertThat(restablecer(token, "nueva-clave-1")).hasStatus(HttpStatus.OK);

		assertThat(iniciarSesion("ana@mail.com", "nueva-clave-1")).hasStatus(HttpStatus.OK);
		assertThat(iniciarSesion("ana@mail.com", "secreta12")).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult reutilizado = restablecer(token, "otra-clave-2");
		assertThat(reutilizado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reutilizado).bodyJson().extractingPath("$.codigo").isEqualTo("TOKEN_RECUPERACION_INVALIDO");
	}

	@Test
	void correoNoRegistradoRecibeLaMismaRespuestaSinQueSeEnvieCorreo() throws Exception {
		MvcTestResult inexistente = solicitar("nadie@mail.com", "10.1.0.2");
		MvcTestResult registrado = solicitar("ana@mail.com", "10.1.0.2");

		assertThat(inexistente).hasStatus(HttpStatus.ACCEPTED);
		assertThat(registrado).hasStatus(HttpStatus.ACCEPTED);
		assertThat(inexistente.getResponse().getContentAsString())
				.isEqualTo(registrado.getResponse().getContentAsString());
		then(notificadorCorreo).should(never()).enviarRecuperacion(eq("nadie@mail.com"), anyString(), anyString());
	}

	@Test
	void laBaseGuardaElHashDelTokenYEseValorNoSirveComoToken() {
		solicitar("ana@mail.com", "10.1.0.3");
		String token = ultimoTokenEnviadoA("ana@mail.com");

		String guardado = jdbc.queryForObject("SELECT token FROM recuperacion_contrasena", String.class);

		assertThat(guardado).hasSize(64).isEqualTo(TokenRecuperacion.hash(token)).isNotEqualTo(token);
		assertThat(restablecer(guardado, "nueva-clave-1")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void segundaSolicitudInvalidaElTokenDeLaPrimera() {
		solicitar("ana@mail.com", "10.1.0.4");
		String primero = ultimoTokenEnviadoA("ana@mail.com");
		solicitar("ana@mail.com", "10.1.0.4");
		String segundo = ultimoTokenEnviadoA("ana@mail.com");

		assertThat(segundo).isNotEqualTo(primero);
		assertThat(restablecer(primero, "nueva-clave-1")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(restablecer(segundo, "nueva-clave-1")).hasStatus(HttpStatus.OK);
	}

	@Test
	void tokenVencidoNoCambiaLaContrasena() {
		solicitar("ana@mail.com", "10.1.0.5");
		String token = ultimoTokenEnviadoA("ana@mail.com");
		jdbc.update("UPDATE recuperacion_contrasena SET fecha_expiracion = ?", Timestamp.valueOf("2000-01-01 00:00:00"));

		assertThat(restablecer(token, "nueva-clave-1")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(iniciarSesion("ana@mail.com", "secreta12")).hasStatus(HttpStatus.OK);
	}

	@Test
	void cuartaSolicitudDelMismoCorreoDesdeLaMismaIpDevuelve429() {
		String correo = "limite-recuperacion@mail.com";
		for (int i = 0; i < 3; i++) {
			assertThat(solicitar(correo, "10.1.0.6")).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult bloqueada = solicitar(correo, "10.1.0.6");

		assertThat(bloqueada).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(bloqueada).bodyJson().extractingPath("$.codigo").isEqualTo("SOLICITUDES_EXCEDIDAS");
		assertThat(solicitar(correo, "10.1.0.60")).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void restablecerLaContrasenaInvalidaLosTokensDeSesionAnteriores() throws Exception {
		String tokenDeSesion = JsonPath.read(
				iniciarSesion("ana@mail.com", "secreta12").getResponse().getContentAsString(), "$.token");
		assertThat(pedirRutaProtegida(tokenDeSesion)).hasStatus(HttpStatus.NOT_FOUND);
		solicitar("ana@mail.com", "10.1.0.7");

		assertThat(restablecer(ultimoTokenEnviadoA("ana@mail.com"), "nueva-clave-1")).hasStatus(HttpStatus.OK);

		assertThat(pedirRutaProtegida(tokenDeSesion)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void cuentaDesactivadaRecuperaLaContrasenaSinReactivarse() {
		// Se desactiva directo en la base, sin pasar por la gestión de usuarios, que se prueba aparte
		jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA' WHERE correo = ?", "ana@mail.com");
		assertThat(solicitar("ana@mail.com", "10.1.0.8")).hasStatus(HttpStatus.ACCEPTED);

		assertThat(restablecer(ultimoTokenEnviadoA("ana@mail.com"), "nueva-clave-1")).hasStatus(HttpStatus.OK);

		// 403 y no 401: la contraseña nueva es la correcta, pero la cuenta sigue desactivada
		MvcTestResult sesion = iniciarSesion("ana@mail.com", "nueva-clave-1");
		assertThat(sesion).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(sesion).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_DESACTIVADA");
	}

	@Test
	void elTokenDeUnaCuentaSoloCambiaLaContrasenaDeEsaCuenta() {
		registrar("beto@mail.com");
		solicitar("ana@mail.com", "10.1.0.9");

		assertThat(restablecer(ultimoTokenEnviadoA("ana@mail.com"), "nueva-clave-1")).hasStatus(HttpStatus.OK);

		assertThat(iniciarSesion("beto@mail.com", "secreta12")).hasStatus(HttpStatus.OK);
		assertThat(iniciarSesion("beto@mail.com", "nueva-clave-1")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult iniciarSesionDesde(String ip, String contrasena) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr(ip);
					return peticion;
				})
				.content("{\"correo\":\"ana@mail.com\",\"contrasena\":\"%s\"}".formatted(contrasena)).exchange();
	}

	@Test
	void despuesDeRestablecerSePuedeIniciarSesionAunqueElCorreoEstuvieraBloqueado() {
		// IP propia: los intentos fallidos de ana en otros tests de esta clase se cuentan desde 127.0.0.1
		String ip = "10.1.0.10";
		for (int i = 0; i < 5; i++) {
			assertThat(iniciarSesionDesde(ip, "otra-clave")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(iniciarSesionDesde(ip, "secreta12")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		solicitar("ana@mail.com", ip);

		assertThat(restablecer(ultimoTokenEnviadoA("ana@mail.com"), "nueva-clave-1")).hasStatus(HttpStatus.OK);

		assertThat(iniciarSesionDesde(ip, "nueva-clave-1")).hasStatus(HttpStatus.OK);
	}

	@Test
	void dosSolicitudesSimultaneasDejanUnSoloTokenVigente() throws Exception {
		// El envío tarda: sin bloqueo, la segunda solicitud no ve el token que la primera todavía no confirmó
		willAnswer(invocacion -> {
			Thread.sleep(300);
			return invocacion.callRealMethod();
		}).given(notificadorCorreo).enviarRecuperacion(anyString(), anyString(), anyString());
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<Object>> solicitudes = List.of("10.1.0.11", "10.1.0.12").stream()
					.map(ip -> hilos.submit(() -> {
						largada.await();
						recuperacionContrasenaService.solicitar(new SolicitudRecuperacionRequest("ana@mail.com"), ip);
						return null;
					}))
					.toList();
			largada.countDown();
			for (Future<Object> solicitud : solicitudes) {
				solicitud.get(10, TimeUnit.SECONDS);
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recuperacion_contrasena WHERE usado = FALSE",
				Integer.class)).isEqualTo(1);
	}
}
