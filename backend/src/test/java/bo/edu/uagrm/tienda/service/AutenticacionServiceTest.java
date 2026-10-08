package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.spy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.config.JwtProperties;
import bo.edu.uagrm.tienda.config.TokenJwt;
import bo.edu.uagrm.tienda.config.TokenLeido;
import bo.edu.uagrm.tienda.dto.CambioContrasenaRequest;
import bo.edu.uagrm.tienda.dto.InicioSesionRequest;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.ContrasenaActualIncorrectaException;
import bo.edu.uagrm.tienda.exception.CredencialesIncorrectasException;
import bo.edu.uagrm.tienda.exception.CuentaDesactivadaException;
import bo.edu.uagrm.tienda.exception.IntentosExcedidosException;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

	private static final Instant AHORA = Instant.parse("2026-09-14T15:00:00Z");
	private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/La_Paz"));
	private static final LocalDateTime FECHA_REGISTRO = LocalDateTime.of(2026, 9, 12, 11, 0);
	private static final String HASH_SECRETA12 = new BCryptPasswordEncoder().encode("secreta12");
	private static final String IP = "10.0.0.1";

	@Mock
	private UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder());

	private final TokenJwt tokenJwt = new TokenJwt(
			new JwtProperties("Y2xhdmUtZGUtcHJ1ZWJhcy1wYXJhLXRva2Vucy1qd3QtZXZlbnRvcw==", Duration.ofHours(8)), RELOJ);

	private AutenticacionService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new AutenticacionService(usuarioRepository, passwordEncoder, tokenJwt, new LimiteIntentos(RELOJ));
	}

	private static Usuario cliente(long id, String hash) {
		Usuario usuario = Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", hash, null, FECHA_REGISTRO);
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	private static Usuario desactivada(Usuario usuario) {
		usuario.desactivar();
		return usuario;
	}

	private SesionIniciada iniciarSesion(String correo, String contrasena) {
		return servicio.iniciarSesion(new InicioSesionRequest(correo, contrasena), IP);
	}

	private void fallarVeces(int veces, String correo) {
		for (int i = 0; i < veces; i++) {
			assertThatThrownBy(() -> iniciarSesion(correo, "otra-clave"))
					.isInstanceOf(CredencialesIncorrectasException.class);
		}
	}

	@Test
	void credencialesCorrectasDeUnaCuentaActivaEntreganUnTokenDelUsuario() {
		Usuario ana = cliente(7, HASH_SECRETA12);
		given(usuarioRepository.findByCorreo("ana@mail.com")).willReturn(Optional.of(ana));

		SesionIniciada sesion = iniciarSesion("ana@mail.com", "secreta12");

		assertThat(sesion.usuario()).isSameAs(ana);
		assertThat(sesion.token().expiracion()).isEqualTo(Instant.parse("2026-09-14T23:00:00Z"));
		assertThat(tokenJwt.leer(sesion.token().token()))
				.hasValueSatisfying(leido -> assertThat(leido.idUsuario()).isEqualTo(7L));
	}

	@Test
	void administradorIniciaSesionIgualQueUnCliente() {
		Usuario admin = Usuario.crearAdministrador("Administrador", "Inicial", "admin@tienda.com", HASH_SECRETA12,
				FECHA_REGISTRO);
		ReflectionTestUtils.setField(admin, "idUsuario", 1L);
		given(usuarioRepository.findByCorreo("admin@tienda.com")).willReturn(Optional.of(admin));

		assertThat(iniciarSesion("admin@tienda.com", "secreta12").usuario().getRol()).isEqualTo(Rol.ADMINISTRADOR);
	}

	@Test
	void contrasenaIncorrectaSeInformaComoCredencialesIncorrectas() {
		given(usuarioRepository.findByCorreo("ana@mail.com")).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));

		assertThatThrownBy(() -> iniciarSesion("ana@mail.com", "Secreta12"))
				.isInstanceOf(CredencialesIncorrectasException.class)
				.hasMessage("Correo o contraseña incorrectos.");
	}

	@Test
	void correoInexistenteSeInformaIgualYTambienComparaUnaContrasena() {
		given(usuarioRepository.findByCorreo("nadie@mail.com")).willReturn(Optional.empty());

		assertThatThrownBy(() -> iniciarSesion("nadie@mail.com", "secreta12"))
				.isInstanceOf(CredencialesIncorrectasException.class)
				.hasMessage("Correo o contraseña incorrectos.");
		then(passwordEncoder).should().matches(eq("secreta12"), startsWith("$2"));
	}

	@Test
	void contrasenaDeMasDe72BytesNoEntraAunqueBcryptLaTruncaria() {
		String hash72 = new BCryptPasswordEncoder().encode("a".repeat(72));
		String contrasena73 = "a".repeat(72) + "b";
		// Precondición: BCrypt compara solo los primeros 72 bytes
		assertThat(new BCryptPasswordEncoder().matches(contrasena73, hash72)).isTrue();
		given(usuarioRepository.findByCorreo("ana@mail.com")).willReturn(Optional.of(cliente(7, hash72)));

		assertThatThrownBy(() -> iniciarSesion("ana@mail.com", contrasena73))
				.isInstanceOf(CredencialesIncorrectasException.class);
	}

	@Test
	void cuentaDesactivadaConContrasenaCorrectaSeInformaComoDesactivada() {
		given(usuarioRepository.findByCorreo("ana@mail.com"))
				.willReturn(Optional.of(desactivada(cliente(7, HASH_SECRETA12))));

		assertThatThrownBy(() -> iniciarSesion("ana@mail.com", "secreta12"))
				.isInstanceOf(CuentaDesactivadaException.class);
	}

	@Test
	void cuentaDesactivadaConContrasenaIncorrectaSeInformaComoCredencialesIncorrectas() {
		given(usuarioRepository.findByCorreo("ana@mail.com"))
				.willReturn(Optional.of(desactivada(cliente(7, HASH_SECRETA12))));

		assertThatThrownBy(() -> iniciarSesion("ana@mail.com", "otra-clave"))
				.isInstanceOf(CredencialesIncorrectasException.class);
	}

	@Test
	void cincoIntentosFallidosBloqueanAunqueElSextoTengaLaContrasenaCorrecta() {
		given(usuarioRepository.findByCorreo("ana@mail.com")).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));
		fallarVeces(5, "ana@mail.com");

		assertThatThrownBy(() -> iniciarSesion("ana@mail.com", "secreta12"))
				.isInstanceOf(IntentosExcedidosException.class);
	}

	@Test
	void correoInexistenteTambienSumaIntentos() {
		given(usuarioRepository.findByCorreo("nadie@mail.com")).willReturn(Optional.empty());
		fallarVeces(5, "nadie@mail.com");

		assertThatThrownBy(() -> iniciarSesion("nadie@mail.com", "secreta12"))
				.isInstanceOf(IntentosExcedidosException.class);
	}

	@Test
	void inicioDeSesionExitosoReiniciaElConteo() {
		given(usuarioRepository.findByCorreo("ana@mail.com")).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));
		fallarVeces(4, "ana@mail.com");
		iniciarSesion("ana@mail.com", "secreta12");
		fallarVeces(4, "ana@mail.com");

		assertThat(iniciarSesion("ana@mail.com", "secreta12").usuario().getIdUsuario()).isEqualTo(7L);
	}

	@Test
	void cuentaDesactivadaConContrasenaCorrectaNoSumaIntentos() {
		given(usuarioRepository.findByCorreo("ana@mail.com"))
				.willReturn(Optional.of(desactivada(cliente(7, HASH_SECRETA12))));

		for (int i = 0; i < 6; i++) {
			assertThatThrownBy(() -> iniciarSesion("ana@mail.com", "secreta12"))
					.isInstanceOf(CuentaDesactivadaException.class);
		}
	}

	@Test
	void peticionesSimultaneasNoComparanMasDeCincoContrasenas() throws Exception {
		int hilos = 10;
		CountDownLatch llegadas = new CountDownLatch(hilos);
		AtomicInteger comparaciones = new AtomicInteger();
		// Comparación lenta: retiene a cada hilo hasta que lleguen todos (o pasen 2 s), como una ráfaga real
		PasswordEncoder comparacionLenta = new PasswordEncoder() {

			@Override
			public String encode(CharSequence contrasena) {
				return HASH_SECRETA12;
			}

			@Override
			public boolean matches(CharSequence contrasena, String hash) {
				comparaciones.incrementAndGet();
				llegadas.countDown();
				try {
					llegadas.await(2, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return false;
			}
		};
		AutenticacionService servicioLento = new AutenticacionService(usuarioRepository, comparacionLenta, tokenJwt,
				new LimiteIntentos(RELOJ));
		given(usuarioRepository.findByCorreo("ana@mail.com")).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));

		ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
		try {
			List<Future<?>> rafaga = IntStream.range(0, hilos)
					.<Future<?>>mapToObj(i -> ejecutor.submit(() -> {
						try {
							servicioLento.iniciarSesion(new InicioSesionRequest("ana@mail.com", "otra-clave"), IP);
						} catch (CredencialesIncorrectasException | IntentosExcedidosException esperada) {
							// cada intento termina rechazado de una de las dos formas
						}
					}))
					.toList();
			for (Future<?> intento : rafaga) {
				intento.get(10, TimeUnit.SECONDS);
			}
		} finally {
			ejecutor.shutdownNow();
		}

		assertThat(comparaciones.get()).isEqualTo(5);
	}

	@Test
	void usuarioActivoDevuelveSoloCuentasActivasExistentes() {
		given(usuarioRepository.findById(7L)).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));
		given(usuarioRepository.findById(8L)).willReturn(Optional.of(desactivada(cliente(8, HASH_SECRETA12))));
		given(usuarioRepository.findById(9L)).willReturn(Optional.empty());

		assertThat(servicio.usuarioActivo(7L)).isPresent();
		assertThat(servicio.usuarioActivo(8L)).isEmpty();
		assertThat(servicio.usuarioActivo(9L)).isEmpty();
	}

	private SesionIniciada cambiarContrasena(String actual, String nueva) {
		return servicio.cambiarContrasena(7L, new CambioContrasenaRequest(actual, nueva), IP);
	}

	private void fallarCambioVeces(int veces) {
		for (int i = 0; i < veces; i++) {
			assertThatThrownBy(() -> cambiarContrasena("otra-clave", "nueva-clave-1"))
					.isInstanceOf(ContrasenaActualIncorrectaException.class);
		}
	}

	@Test
	void contrasenaActualCorrectaGuardaLaNuevaYEntregaUnTokenDeLaNueva() {
		Usuario ana = cliente(7, HASH_SECRETA12);
		given(usuarioRepository.findConBloqueoByIdUsuario(7L)).willReturn(Optional.of(ana));
		TokenLeido tokenAnterior = tokenJwt.leer(tokenJwt.emitir(ana).token()).orElseThrow();

		SesionIniciada sesion = cambiarContrasena("secreta12", "nueva-clave-1");

		assertThat(sesion.usuario()).isSameAs(ana);
		assertThat(passwordEncoder.matches("nueva-clave-1", ana.getContrasena())).isTrue();
		assertThat(tokenJwt.correspondeA(tokenJwt.leer(sesion.token().token()).orElseThrow(), ana)).isTrue();
		assertThat(tokenJwt.correspondeA(tokenAnterior, ana)).isFalse();
	}

	@Test
	void contrasenaActualIncorrectaNoCambiaLaContrasena() {
		Usuario ana = cliente(7, HASH_SECRETA12);
		given(usuarioRepository.findConBloqueoByIdUsuario(7L)).willReturn(Optional.of(ana));

		assertThatThrownBy(() -> cambiarContrasena("Secreta12", "nueva-clave-1"))
				.isInstanceOfSatisfying(ContrasenaActualIncorrectaException.class, ex -> {
					assertThat(ex.getCodigo()).isEqualTo("CONTRASENA_ACTUAL_INCORRECTA");
					assertThat(ex.getEstadoHttp()).isEqualTo(HttpStatus.BAD_REQUEST);
				});
		assertThat(ana.getContrasena()).isEqualTo(HASH_SECRETA12);
	}

	@Test
	void contrasenaActualDeMasDe72BytesEsIncorrectaAunqueBcryptLaTruncaria() {
		String hash72 = new BCryptPasswordEncoder().encode("a".repeat(72));
		Usuario ana = cliente(7, hash72);
		given(usuarioRepository.findConBloqueoByIdUsuario(7L)).willReturn(Optional.of(ana));

		assertThatThrownBy(() -> cambiarContrasena("a".repeat(72) + "b", "nueva-clave-1"))
				.isInstanceOf(ContrasenaActualIncorrectaException.class);
		assertThat(ana.getContrasena()).isEqualTo(hash72);
	}

	@Test
	void cincoContrasenasActualesIncorrectasBloqueanElCambioAunqueLaSiguienteSeaCorrecta() {
		Usuario ana = cliente(7, HASH_SECRETA12);
		given(usuarioRepository.findConBloqueoByIdUsuario(7L)).willReturn(Optional.of(ana));
		fallarCambioVeces(5);

		assertThatThrownBy(() -> cambiarContrasena("secreta12", "nueva-clave-1"))
				.isInstanceOf(IntentosExcedidosException.class);
		assertThat(ana.getContrasena()).isEqualTo(HASH_SECRETA12);
	}

	@Test
	void contrasenasActualesIncorrectasSumanAlLimiteDelInicioDeSesion() {
		given(usuarioRepository.findConBloqueoByIdUsuario(7L)).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));
		fallarCambioVeces(5);

		// El límite rechaza antes de buscar la cuenta: no hace falta stub de findByCorreo
		assertThatThrownBy(() -> iniciarSesion("ana@mail.com", "secreta12"))
				.isInstanceOf(IntentosExcedidosException.class);
	}

	@Test
	void cambioExitosoReiniciaElConteoDeIntentos() {
		given(usuarioRepository.findConBloqueoByIdUsuario(7L)).willReturn(Optional.of(cliente(7, HASH_SECRETA12)));
		fallarCambioVeces(4);
		cambiarContrasena("secreta12", "nueva-clave-1");
		fallarCambioVeces(4);

		assertThatCode(() -> cambiarContrasena("nueva-clave-1", "nueva-clave-2")).doesNotThrowAnyException();
	}
}
