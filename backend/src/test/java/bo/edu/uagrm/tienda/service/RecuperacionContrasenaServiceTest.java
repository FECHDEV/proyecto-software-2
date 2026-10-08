package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import bo.edu.uagrm.tienda.dto.RestablecimientoContrasenaRequest;
import bo.edu.uagrm.tienda.dto.SolicitudRecuperacionRequest;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.RecuperacionContrasena;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.SolicitudesExcedidasException;
import bo.edu.uagrm.tienda.exception.TokenRecuperacionInvalidoException;
import bo.edu.uagrm.tienda.repository.RecuperacionContrasenaRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaServiceTest {

	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-15T14:00:00Z"), ZoneId.of("America/La_Paz"));
	private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);
	private static final String HASH_ANTERIOR = "$2a$10$hashAnterior";
	private static final String IP = "10.0.0.1";

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private RecuperacionContrasenaRepository recuperacionRepository;

	@Mock
	private NotificadorCorreo notificadorCorreo;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private final LimiteIntentos limiteIntentos = new LimiteIntentos(RELOJ);

	private RecuperacionContrasenaService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new RecuperacionContrasenaService(usuarioRepository, recuperacionRepository, passwordEncoder,
				notificadorCorreo, new LimiteSolicitudesRecuperacion(RELOJ), limiteIntentos, RELOJ);
	}

	private static Usuario ana() {
		return Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", HASH_ANTERIOR, null,
				AHORA.minusDays(3));
	}

	private static Usuario desactivada(Usuario usuario) {
		usuario.desactivar();
		return usuario;
	}

	private void solicitar(String correo) {
		servicio.solicitar(new SolicitudRecuperacionRequest(correo), IP);
	}

	private void restablecer(String token, String contrasena) {
		servicio.restablecer(new RestablecimientoContrasenaRequest(token, contrasena));
	}

	private String tokenEnviadoAAna() {
		ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
		then(notificadorCorreo).should().enviarRecuperacion(eq("ana@mail.com"), eq("Ana"), token.capture());
		return token.getValue();
	}

	private RecuperacionContrasena recuperacionGuardada() {
		ArgumentCaptor<RecuperacionContrasena> guardada = ArgumentCaptor.forClass(RecuperacionContrasena.class);
		then(recuperacionRepository).should().save(guardada.capture());
		return guardada.getValue();
	}

	// Registro existente para ese token, tal como lo devolvería la base
	private RecuperacionContrasena registrada(Usuario usuario, String token, LocalDateTime creada) {
		RecuperacionContrasena recuperacion = RecuperacionContrasena.crear(usuario, TokenRecuperacion.hash(token), creada);
		given(recuperacionRepository.findByHashToken(TokenRecuperacion.hash(token))).willReturn(Optional.of(recuperacion));
		return recuperacion;
	}

	@Test
	void correoRegistradoGuardaElHashDelTokenConVigenciaDeUnaHoraYLeEnviaElToken() {
		Usuario ana = ana();
		given(usuarioRepository.findConBloqueoByCorreo("ana@mail.com")).willReturn(Optional.of(ana));

		solicitar(" Ana@Mail.COM ");

		String token = tokenEnviadoAAna();
		RecuperacionContrasena guardada = recuperacionGuardada();
		assertThat(guardada.getUsuario()).isSameAs(ana);
		assertThat(guardada.getHashToken()).isEqualTo(TokenRecuperacion.hash(token)).isNotEqualTo(token);
		assertThat(guardada.getFechaCreacion()).isEqualTo(AHORA);
		assertThat(guardada.getFechaExpiracion()).isEqualTo(AHORA.plusHours(1));
		assertThat(guardada.isUsado()).isFalse();
	}

	@Test
	void correoNoRegistradoTerminaIgualSinGuardarNiEnviarNada() {
		assertThatCode(() -> solicitar("nadie@mail.com")).doesNotThrowAnyException();

		then(recuperacionRepository).shouldHaveNoInteractions();
		then(notificadorCorreo).shouldHaveNoInteractions();
	}

	@Test
	void nuevaSolicitudInvalidaLasRecuperacionesPendientesAnteriores() {
		Usuario ana = ana();
		RecuperacionContrasena anterior = RecuperacionContrasena.crear(ana, "b".repeat(64), AHORA.minusMinutes(10));
		given(usuarioRepository.findConBloqueoByCorreo("ana@mail.com")).willReturn(Optional.of(ana));
		given(recuperacionRepository.findByUsuarioAndUsadoFalse(ana)).willReturn(List.of(anterior));

		solicitar("ana@mail.com");

		assertThat(anterior.isUsado()).isTrue();
		assertThat(recuperacionGuardada().isUsado()).isFalse();
	}

	@Test
	void cuentaDesactivadaTambienRecibeElTokenYSigueDesactivada() {
		Usuario ana = desactivada(ana());
		given(usuarioRepository.findConBloqueoByCorreo("ana@mail.com")).willReturn(Optional.of(ana));

		solicitar("ana@mail.com");

		assertThat(tokenEnviadoAAna()).isNotBlank();
		assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
	}

	@Test
	void cuartaSolicitudDelMismoCorreoDesdeLaMismaIpSeRechazaAunqueElCorreoNoExista() {
		for (int i = 0; i < 3; i++) {
			solicitar("nadie@mail.com");
		}

		assertThatThrownBy(() -> solicitar("nadie@mail.com")).isInstanceOf(SolicitudesExcedidasException.class);
		then(usuarioRepository).should(times(3)).findConBloqueoByCorreo("nadie@mail.com");
	}

	@Test
	void tokenVigenteCambiaLaContrasenaEInvalidaLasRecuperacionesPendientesDeLaCuenta() {
		Usuario ana = ana();
		String token = TokenRecuperacion.generar();
		RecuperacionContrasena recuperacion = registrada(ana, token, AHORA.minusMinutes(30));
		RecuperacionContrasena otra = RecuperacionContrasena.crear(ana, "c".repeat(64), AHORA.minusMinutes(20));
		given(recuperacionRepository.findByUsuarioAndUsadoFalse(ana)).willReturn(List.of(recuperacion, otra));

		restablecer(token, "nueva-clave-1");

		assertThat(passwordEncoder.matches("nueva-clave-1", ana.getContrasena())).isTrue();
		assertThat(recuperacion.isUsado()).isTrue();
		assertThat(otra.isUsado()).isTrue();
	}

	@Test
	void tokenVencidoNoCambiaLaContrasena() {
		Usuario ana = ana();
		String token = TokenRecuperacion.generar();
		registrada(ana, token, AHORA.minusHours(1));

		assertThatThrownBy(() -> restablecer(token, "nueva-clave-1"))
				.isInstanceOfSatisfying(TokenRecuperacionInvalidoException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("TOKEN_RECUPERACION_INVALIDO"));
		assertThat(ana.getContrasena()).isEqualTo(HASH_ANTERIOR);
	}

	@Test
	void tokenYaUsadoNoCambiaLaContrasena() {
		Usuario ana = ana();
		String token = TokenRecuperacion.generar();
		registrada(ana, token, AHORA.minusMinutes(5)).marcarUsada();

		assertThatThrownBy(() -> restablecer(token, "nueva-clave-1"))
				.isInstanceOf(TokenRecuperacionInvalidoException.class);
		assertThat(ana.getContrasena()).isEqualTo(HASH_ANTERIOR);
	}

	@Test
	void tokenDesconocidoSeRechazaConElMismoError() {
		assertThatThrownBy(() -> restablecer(TokenRecuperacion.generar(), "nueva-clave-1"))
				.isInstanceOf(TokenRecuperacionInvalidoException.class);
	}

	@Test
	void restablecerEnUnaCuentaDesactivadaNoLaReactiva() {
		Usuario ana = desactivada(ana());
		String token = TokenRecuperacion.generar();
		registrada(ana, token, AHORA.minusMinutes(5));

		restablecer(token, "nueva-clave-1");

		assertThat(passwordEncoder.matches("nueva-clave-1", ana.getContrasena())).isTrue();
		assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
	}

	@Test
	void restablecerLaContrasenaQuitaElBloqueoDeInicioDeSesionDeLaCuenta() {
		for (int i = 0; i < 5; i++) {
			limiteIntentos.registrarIntento("ana@mail.com", IP);
		}
		Usuario ana = ana();
		String token = TokenRecuperacion.generar();
		registrada(ana, token, AHORA.minusMinutes(5));

		restablecer(token, "nueva-clave-1");

		assertThatCode(() -> limiteIntentos.registrarIntento("ana@mail.com", IP)).doesNotThrowAnyException();
	}
}
