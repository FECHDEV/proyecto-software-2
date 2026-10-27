package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;
import java.time.Duration;
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
import org.springframework.boot.DefaultApplicationArguments;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.exception.SesionSinCuentaActivaException;
import bo.edu.uagrm.tienda.exception.TokenVerificacionInvalidoException;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.GestionUsuariosService;
import bo.edu.uagrm.tienda.service.LimiteReenviosVerificacion;
import bo.edu.uagrm.tienda.service.NotificadorCorreo;
import bo.edu.uagrm.tienda.service.TokenRecuperacion;
import bo.edu.uagrm.tienda.service.VerificacionCorreoService;

// HU-20: verificar el correo para poder confirmar pedidos
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacionCorreoIntegracionTest {

	private static final String REGISTRO = """
			{"nombre":"Ana","apellido":"Rojas","correo":"%s","contrasena":"secreta12"}
			""";
	private static final String VERIFICACION = "/api/auth/verificacion";
	private static final String REENVIO = "/api/cuenta/verificacion";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private GestionUsuariosService gestionUsuarios;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private VerificacionCorreoService verificacionCorreoService;

	@Autowired
	private TransactionTemplate transaccion;

	// Vive en el contexto compartido y cuenta por cuenta: sin reiniciarlo, los reenvíos de un test frenan al siguiente
	@Autowired
	private LimiteReenviosVerificacion limiteReenvios;

	// Deja pasar la llamada al simulador y permite leer el token, que la base no guarda
	@MockitoSpyBean
	private NotificadorCorreo notificadorCorreo;

	private String sesionDeAna;

	@BeforeEach
	void registrarAna() throws Exception {
		sesionDeAna = registrar("ana@mail.com");
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM verificacion_correo");
		limiteReenvios.reiniciarCorreo("ana@mail.com");
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
		usuarioRepository.findByCorreo("beto@mail.com").ifPresent(usuarioRepository::delete);
	}

	private String registrar(String correo) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON).content(REGISTRO.formatted(correo)).exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private String ultimoEnlaceEnviadoA(String correo) {
		ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
		then(notificadorCorreo).should(atLeastOnce()).enviarVerificacion(eq(correo), anyString(), token.capture());
		return token.getValue();
	}

	private MvcTestResult verificar(String token) {
		return mvc.post().uri(VERIFICACION).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(token)).exchange();
	}

	private MvcTestResult reenviar(String sesion) {
		return mvc.post().uri(REENVIO).header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion).exchange();
	}

	private boolean verificado(String correo) {
		return usuarioRepository.findByCorreo(correo).orElseThrow().isCorreoVerificado();
	}

	private Long idDe(String correo) {
		return usuarioRepository.findByCorreo(correo).orElseThrow().getIdUsuario();
	}

	// HU-20 RF-1
	@Test
	void alRegistrarseQuedaSinVerificarConLaSesionIniciadaYRecibeElEnlace() {
		assertThat(sesionDeAna).isNotBlank();
		assertThat(verificado("ana@mail.com")).isFalse();
		then(notificadorCorreo).should(times(1)).enviarVerificacion(eq("ana@mail.com"), eq("Ana"), anyString());
	}

	// HU-20 RF-13 (24/10/2026): el correo sale después del commit. Si la transacción se deshace, no llega un enlace que
	// no sirve
	@Test
	void siElReenvioSeDeshaceNoSeEnviaElEnlace() {
		clearInvocations(notificadorCorreo);
		transaccion.executeWithoutResult(estado -> {
			verificacionCorreoService.reenviar(idDe("ana@mail.com"));
			estado.setRollbackOnly();
		});

		then(notificadorCorreo).should(never()).enviarVerificacion(anyString(), anyString(), anyString());
		// Solo el enlace del registro, todavía vigente
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_correo WHERE usado = FALSE",
				Integer.class)).isEqualTo(1);
	}

	// HU-20 RF-3, RF-4, RF-17
	@Test
	void elEnlaceVerificaLaCuentaYSirveUnaSolaVez() {
		String token = ultimoEnlaceEnviadoA("ana@mail.com");

		MvcTestResult verificacion = verificar(token);
		assertThat(verificacion).hasStatus(HttpStatus.OK);
		assertThat(verificacion).bodyJson().extractingPath("$.mensaje").isEqualTo("Su correo quedó verificado.");
		assertThat(verificado("ana@mail.com")).isTrue();

		// RF-17 (revisión total): la respuesta se perdió y «Reintentar» lo abre otra vez; el correo ya está
		// verificado, así que lo dice, sin cambiar nada
		MvcTestResult otraVez = verificar(token);
		assertThat(otraVez).hasStatus(HttpStatus.OK);
		assertThat(otraVez).bodyJson().extractingPath("$.mensaje").isEqualTo("Su correo quedó verificado.");

		// Vencido, ya no
		jdbc.update("UPDATE verificacion_correo SET fecha_expiracion = ?", Timestamp.valueOf("2000-01-01 00:00:00"));
		MvcTestResult vencido = verificar(token);
		assertThat(vencido).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(vencido).bodyJson().extractingPath("$.codigo").isEqualTo("TOKEN_VERIFICACION_INVALIDO");
	}

	// HU-20 RF-3
	@Test
	void laBaseGuardaElHashDelTokenConVigenciaDe24HorasYEseValorNoSirveComoToken() {
		String token = ultimoEnlaceEnviadoA("ana@mail.com");
		String guardado = jdbc.queryForObject("SELECT token FROM verificacion_correo", String.class);
		Timestamp creacion = jdbc.queryForObject("SELECT fecha_creacion FROM verificacion_correo", Timestamp.class);
		Timestamp expiracion = jdbc.queryForObject("SELECT fecha_expiracion FROM verificacion_correo",
				Timestamp.class);

		assertThat(guardado).isEqualTo(TokenRecuperacion.hash(token)).isNotEqualTo(token);
		assertThat(Duration.between(creacion.toInstant(), expiracion.toInstant())).isEqualTo(Duration.ofHours(24));
		assertThat(verificar(guardado)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verificado("ana@mail.com")).isFalse();
	}

	// HU-20 RF-4
	@Test
	void elTokenDeUnaCuentaSoloVerificaEsaCuenta() throws Exception {
		registrar("beto@mail.com");

		assertThat(verificar(ultimoEnlaceEnviadoA("ana@mail.com"))).hasStatus(HttpStatus.OK);

		assertThat(verificado("ana@mail.com")).isTrue();
		assertThat(verificado("beto@mail.com")).isFalse();
	}

	// HU-20 RF-4: el enlace funciona sin sesión y no la inicia
	@Test
	void verificaSinSesionYNoDevuelveUnTokenDeSesion() throws Exception {
		MvcTestResult verificacion = verificar(ultimoEnlaceEnviadoA("ana@mail.com"));

		assertThat(verificacion).hasStatus(HttpStatus.OK);
		assertThat(verificacion.getResponse().getContentAsString()).doesNotContain("token");
	}

	// HU-20 RF-5
	@Test
	void tokenVencidoInexistenteVacioODeCuentaDesactivadaDanElMismoError() {
		String token = ultimoEnlaceEnviadoA("ana@mail.com");
		jdbc.update("UPDATE verificacion_correo SET fecha_expiracion = ?", Timestamp.valueOf("2000-01-01 00:00:00"));
		MvcTestResult vencido = verificar(token);
		MvcTestResult inexistente = verificar("no-existe");

		assertThat(vencido).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(vencido).bodyJson().extractingPath("$.codigo").isEqualTo("TOKEN_VERIFICACION_INVALIDO");
		assertThat(inexistente).bodyJson().extractingPath("$.codigo").isEqualTo("TOKEN_VERIFICACION_INVALIDO");
		assertThat(verificar("  ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verificado("ana@mail.com")).isFalse();
	}

	// HU-20 RF-5, RF-12
	@Test
	void cuentaDesactivadaNoRecibeElEnlaceYSuEnlaceAnteriorNoSirveNiAlReactivarla() {
		String anterior = ultimoEnlaceEnviadoA("ana@mail.com");
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		Long idAdmin = usuarioRepository.findAll().stream().filter(cuenta -> cuenta.getRol() == Rol.ADMINISTRADOR)
				.findFirst().orElseThrow().getIdUsuario();
		gestionUsuarios.cambiarEstado(idAdmin, idDe("ana@mail.com"), EstadoCuenta.DESACTIVADA);

		MvcTestResult verificacion = verificar(anterior);
		assertThat(verificacion).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verificacion).bodyJson().extractingPath("$.codigo").isEqualTo("TOKEN_VERIFICACION_INVALIDO");
		assertThat(verificado("ana@mail.com")).isFalse();

		verificacionCorreoService.iniciar(usuarioRepository.findByCorreo("ana@mail.com").orElseThrow());
		assertThatThrownBy(() -> verificacionCorreoService.reenviar(idDe("ana@mail.com")))
				.isInstanceOfSatisfying(SesionSinCuentaActivaException.class,
						ex -> assertThat(ex.getEstadoHttp()).isEqualTo(HttpStatus.UNAUTHORIZED));
		then(notificadorCorreo).should(times(1)).enviarVerificacion(eq("ana@mail.com"), anyString(), anyString());

		// Como la recuperación (HU-03 RF-9): reactivar la cuenta no revive el enlace
		gestionUsuarios.cambiarEstado(idAdmin, idDe("ana@mail.com"), EstadoCuenta.ACTIVA);
		assertThat(verificar(anterior)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verificado("ana@mail.com")).isFalse();
	}

	// HU-20 RF-9
	@Test
	void reenviarMandaUnEnlaceNuevoEInvalidaElAnterior() {
		String anterior = ultimoEnlaceEnviadoA("ana@mail.com");

		MvcTestResult reenvio = reenviar(sesionDeAna);
		assertThat(reenvio).hasStatus(HttpStatus.ACCEPTED);
		assertThat(reenvio).bodyJson().extractingPath("$.mensaje")
				.isEqualTo("Le enviamos el correo a ana@mail.com.");
		String nuevo = ultimoEnlaceEnviadoA("ana@mail.com");

		assertThat(nuevo).isNotEqualTo(anterior);
		assertThat(verificar(anterior)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verificar(nuevo)).hasStatus(HttpStatus.OK);
	}

	// HU-20 RF-10
	@Test
	void elCuartoReenvioEn15MinutosResponde429SinEnviar() {
		for (int i = 0; i < 3; i++) {
			assertThat(reenviar(sesionDeAna)).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult cuarto = reenviar(sesionDeAna);
		assertThat(cuarto).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(cuarto).bodyJson().extractingPath("$.codigo").isEqualTo("REENVIOS_EXCEDIDOS");
		// El del registro y los tres reenvíos
		then(notificadorCorreo).should(times(4)).enviarVerificacion(eq("ana@mail.com"), anyString(), anyString());
	}

	// HU-20 RF-11
	@Test
	void reenviarConElCorreoVerificadoResponde409SinEnviar() {
		verificar(ultimoEnlaceEnviadoA("ana@mail.com"));

		MvcTestResult reenvio = reenviar(sesionDeAna);
		assertThat(reenvio).hasStatus(HttpStatus.CONFLICT);
		assertThat(reenvio).bodyJson().extractingPath("$.codigo").isEqualTo("CORREO_YA_VERIFICADO");
		then(notificadorCorreo).should(times(1)).enviarVerificacion(eq("ana@mail.com"), anyString(), anyString());
	}

	// HU-20 RF-9: sin sesión no se reenvía
	@Test
	void reenviarSinSesionResponde401() {
		assertThat(mvc.post().uri(REENVIO).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// HU-20 RF-8
	@Test
	void losDatosPersonalesDicenSiElCorreoEstaVerificado() {
		MvcTestResult antes = mvc.get().uri("/api/cuenta/datos-personales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + sesionDeAna).exchange();
		assertThat(antes).bodyJson().extractingPath("$.correoVerificado").isEqualTo(false);

		verificar(ultimoEnlaceEnviadoA("ana@mail.com"));

		MvcTestResult despues = mvc.get().uri("/api/cuenta/datos-personales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + sesionDeAna).exchange();
		assertThat(despues).bodyJson().extractingPath("$.correoVerificado").isEqualTo(true);
	}

	// HU-20 RF-9
	@Test
	void dosReenviosALaVezDejanUnSoloEnlaceVigente() throws Exception {
		// El envío tarda: sin bloqueo, el segundo reenvío no ve el enlace que el primero todavía no confirmó
		willAnswer(invocacion -> {
			Thread.sleep(300);
			return invocacion.callRealMethod();
		}).given(notificadorCorreo).enviarVerificacion(anyString(), anyString(), anyString());
		Long idAna = idDe("ana@mail.com");
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<Object>> reenvios = List.of(1, 2).stream()
					.map(numero -> hilos.submit(() -> {
						largada.await();
						verificacionCorreoService.reenviar(idAna);
						return null;
					}))
					.toList();
			largada.countDown();
			for (Future<Object> reenvio : reenvios) {
				reenvio.get(10, TimeUnit.SECONDS);
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_correo WHERE usado = FALSE",
				Integer.class)).isEqualTo(1);
	}

	// HU-20 RF-4, RF-9: verificar bloquea la cuenta antes que el enlace, como reenviar. En el orden contrario, el
	// reenvío (con la cuenta bloqueada, marcando el enlace usado) y la verificación (con el enlace bloqueado,
	// esperando la cuenta) se trababan
	@Test
	void verificarYReenviarALaVezNoSeTraban() throws Exception {
		String token = ultimoEnlaceEnviadoA("ana@mail.com");
		willAnswer(invocacion -> {
			Thread.sleep(400);
			return invocacion.callRealMethod();
		}).given(notificadorCorreo).enviarVerificacion(anyString(), anyString(), anyString());
		Long idAna = idDe("ana@mail.com");
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			Future<Object> reenvio = hilos.submit(() -> {
				verificacionCorreoService.reenviar(idAna);
				return null;
			});
			Thread.sleep(100);
			Future<Object> verificacion = hilos.submit(() -> {
				verificacionCorreoService.verificar(token);
				return null;
			});

			reenvio.get(10, TimeUnit.SECONDS);
			// El reenvío terminó primero e invalidó el enlace que se estaba verificando
			assertThatThrownBy(() -> verificacion.get(10, TimeUnit.SECONDS))
					.hasCauseInstanceOf(TokenVerificacionInvalidoException.class);
		} finally {
			hilos.shutdownNow();
		}
		assertThat(verificado("ana@mail.com")).isFalse();
	}

	// HU-20 RF-1: el registro no envía nada si falla
	@Test
	void unRegistroRechazadoNoEnviaElCorreo() {
		assertThat(mvc.post().uri("/api/auth/registro").with(ipNueva()).contentType(MediaType.APPLICATION_JSON)
				.content(REGISTRO.formatted("ana@mail.com")).exchange()).hasStatus(HttpStatus.CONFLICT);

		then(notificadorCorreo).should(times(1)).enviarVerificacion(eq("ana@mail.com"), anyString(), anyString());
		then(notificadorCorreo).should(never()).enviarRecuperacion(anyString(), anyString(), anyString());
	}
}
