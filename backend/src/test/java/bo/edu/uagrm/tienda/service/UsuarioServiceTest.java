package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.dto.DatosPersonalesRequest;
import bo.edu.uagrm.tienda.dto.RegistroClienteRequest;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CuentaExistenteException;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-12T15:00:00Z"), ZoneId.of("America/La_Paz"));

	@Mock
	private UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private UsuarioService usuarioService;

	@BeforeEach
	void crearServicio() {
		usuarioService = new UsuarioService(usuarioRepository, passwordEncoder, RELOJ);
	}

	private static RegistroClienteRequest solicitud(String correo, String telefono) {
		return new RegistroClienteRequest("Ana", "Rojas", correo, "secreta12", telefono);
	}

	@Test
	void registraClienteActivoConCorreoNormalizadoYContrasenaHasheada() {
		given(usuarioRepository.existsByCorreo("ana@mail.com")).willReturn(false);
		given(usuarioRepository.saveAndFlush(any(Usuario.class))).willAnswer(invocacion -> invocacion.getArgument(0));

		Usuario registrado = usuarioService.registrarCliente(solicitud("Ana@Mail.COM", "70000000"));

		ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
		then(usuarioRepository).should().saveAndFlush(guardado.capture());
		Usuario usuario = guardado.getValue();
		assertThat(registrado).isSameAs(usuario);
		assertThat(usuario.getRol()).isEqualTo(Rol.CLIENTE);
		assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(usuario.getCorreo()).isEqualTo("ana@mail.com");
		assertThat(usuario.getNombre()).isEqualTo("Ana");
		assertThat(usuario.getApellido()).isEqualTo("Rojas");
		assertThat(usuario.getTelefono()).isEqualTo("70000000");
		assertThat(usuario.getFechaRegistro()).isEqualTo(LocalDateTime.of(2026, 9, 12, 11, 0));
		assertThat(usuario.getContrasena()).isNotEqualTo("secreta12").startsWith("$2");
		assertThat(passwordEncoder.matches("secreta12", usuario.getContrasena())).isTrue();
	}

	@Test
	void telefonoEnBlancoSeGuardaComoNulo() {
		given(usuarioRepository.existsByCorreo("ana@mail.com")).willReturn(false);
		given(usuarioRepository.saveAndFlush(any(Usuario.class))).willAnswer(invocacion -> invocacion.getArgument(0));

		Usuario usuario = usuarioService.registrarCliente(solicitud("ana@mail.com", "  "));

		assertThat(usuario.getTelefono()).isNull();
	}

	@Test
	void rechazaCorreoYaRegistradoAunqueCambienLasMayusculas() {
		given(usuarioRepository.existsByCorreo("ana@mail.com")).willReturn(true);

		assertThatThrownBy(() -> usuarioService.registrarCliente(solicitud("ANA@mail.com", null)))
				.isInstanceOf(CuentaExistenteException.class);
		then(usuarioRepository).should(never()).saveAndFlush(any(Usuario.class));
	}

	@Test
	void registroSimultaneoConElMismoCorreoTerminaComoCuentaExistente() {
		given(usuarioRepository.existsByCorreo("ana@mail.com")).willReturn(false);
		given(usuarioRepository.saveAndFlush(any(Usuario.class))).willThrow(new DataIntegrityViolationException("duplicado",
				new ConstraintViolationException("duplicado", new SQLException("Duplicate entry", "23000", 1062),
						ConstraintViolationException.ConstraintKind.UNIQUE, "uk_usuario_correo")));

		assertThatThrownBy(() -> usuarioService.registrarCliente(solicitud("ana@mail.com", null)))
				.isInstanceOf(CuentaExistenteException.class);
	}

	@Test
	void otroErrorDeIntegridadNoSeInformaComoCuentaExistente() {
		DataIntegrityViolationException datoDemasiadoLargo = new DataIntegrityViolationException("Data too long",
				new SQLException("Data too long for column 'correo'", "22001", 1406));
		given(usuarioRepository.existsByCorreo("ana@mail.com")).willReturn(false);
		given(usuarioRepository.saveAndFlush(any(Usuario.class))).willThrow(datoDemasiadoLargo);

		assertThatThrownBy(() -> usuarioService.registrarCliente(solicitud("ana@mail.com", null)))
				.isSameAs(datoDemasiadoLargo);
	}

	private static Usuario ana() {
		Usuario ana = Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", "70000000",
				LocalDateTime.of(2026, 9, 12, 11, 0));
		ReflectionTestUtils.setField(ana, "idUsuario", 7L);
		return ana;
	}

	@Test
	void datosPersonalesDevuelveLaCuentaDeLaSesion() {
		Usuario ana = ana();
		given(usuarioRepository.findById(7L)).willReturn(Optional.of(ana));

		assertThat(usuarioService.datosPersonales(7L)).isSameAs(ana);
	}

	@Test
	void actualizarDatosCambiaSoloNombreApellidoYTelefono() {
		Usuario ana = ana();
		given(usuarioRepository.findById(7L)).willReturn(Optional.of(ana));

		Usuario actualizado = usuarioService.actualizarDatos(7L,
				new DatosPersonalesRequest("Ana María", "Rojas Paz", "71111111"));

		assertThat(actualizado).isSameAs(ana);
		assertThat(ana.getNombre()).isEqualTo("Ana María");
		assertThat(ana.getApellido()).isEqualTo("Rojas Paz");
		assertThat(ana.getTelefono()).isEqualTo("71111111");
		assertThat(ana.getCorreo()).isEqualTo("ana@mail.com");
		assertThat(ana.getContrasena()).isEqualTo("$2a$10$hash");
		assertThat(ana.getRol()).isEqualTo(Rol.CLIENTE);
		assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
	}

	@Test
	void telefonoEnBlancoAlActualizarDejaLaCuentaSinTelefono() {
		given(usuarioRepository.findById(7L)).willReturn(Optional.of(ana()));

		assertThat(usuarioService.actualizarDatos(7L, new DatosPersonalesRequest("Ana", "Rojas", "  ")).getTelefono())
				.isNull();
	}

	@Test
	void actualizarConLosMismosDatosNoAlteraLaCuenta() {
		Usuario ana = ana();
		given(usuarioRepository.findById(7L)).willReturn(Optional.of(ana));

		usuarioService.actualizarDatos(7L, new DatosPersonalesRequest("Ana", "Rojas", "70000000"));

		assertThat(ana.getNombre()).isEqualTo("Ana");
		assertThat(ana.getApellido()).isEqualTo("Rojas");
		assertThat(ana.getTelefono()).isEqualTo("70000000");
		then(usuarioRepository).should(never()).saveAndFlush(any(Usuario.class));
	}
}
