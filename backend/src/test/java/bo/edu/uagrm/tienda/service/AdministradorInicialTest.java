package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import bo.edu.uagrm.tienda.config.AdministradorInicialProperties;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class AdministradorInicialTest {

	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-14T15:00:00Z"), ZoneId.of("America/La_Paz"));

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void crearValidador() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void cerrarValidador() {
		factory.close();
	}

	@Mock
	private UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private void arrancar(String correo, String contrasena) {
		new AdministradorInicial(usuarioRepository, passwordEncoder, validator, RELOJ,
				new AdministradorInicialProperties(correo, contrasena))
				.run(new DefaultApplicationArguments());
	}

	@Test
	void sinAdministradorCreaUnoActivoConLasVariables() {
		given(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).willReturn(false);
		given(usuarioRepository.existsByCorreo("admin@tienda.com")).willReturn(false);

		arrancar(" Admin@Tienda.com ", "clave-admin-1");

		ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
		then(usuarioRepository).should().save(guardado.capture());
		Usuario admin = guardado.getValue();
		assertThat(admin.getRol()).isEqualTo(Rol.ADMINISTRADOR);
		assertThat(admin.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(admin.getCorreo()).isEqualTo("admin@tienda.com");
		assertThat(admin.getNombre()).isEqualTo("Administrador");
		assertThat(admin.getApellido()).isEqualTo("Inicial");
		assertThat(admin.getFechaRegistro()).isEqualTo(LocalDateTime.of(2026, 9, 14, 11, 0));
		assertThat(passwordEncoder.matches("clave-admin-1", admin.getContrasena())).isTrue();
	}

	@Test
	void conAdministradorExistenteNoCreaOtroAunqueFaltenLasVariables() {
		given(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).willReturn(true);

		arrancar("", "");

		then(usuarioRepository).should(never()).save(any(Usuario.class));
	}

	@Test
	void sinAdministradorYSinVariablesNoArrancaIndicandoQueConfigurar() {
		given(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).willReturn(false);

		assertThatThrownBy(() -> arrancar("", ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TIENDA_ADMIN_CORREO")
				.hasMessageContaining("TIENDA_ADMIN_PASSWORD");
		then(usuarioRepository).should(never()).save(any(Usuario.class));
	}

	@Test
	void variablesQueNoCumplenLasReglasDelRegistroNoArrancan() {
		given(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).willReturn(false);

		assertThatThrownBy(() -> arrancar("admin-sin-arroba", "clave-admin-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("formato de correo");
		assertThatThrownBy(() -> arrancar("admin@tienda.com", "1234567"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("al menos 8")
				.hasMessageNotContaining("1234567");
		assertThatThrownBy(() -> arrancar("admin@tienda.com", "a".repeat(73)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("72 bytes");
		then(usuarioRepository).should(never()).save(any(Usuario.class));
	}

	@Test
	void correoDeOtraCuentaNoArrancaNiLeCambiaElRol() {
		given(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).willReturn(false);
		given(usuarioRepository.existsByCorreo("ana@mail.com")).willReturn(true);

		assertThatThrownBy(() -> arrancar("Ana@Mail.com", "clave-admin-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ya pertenece a otra cuenta");
		then(usuarioRepository).should(never()).save(any(Usuario.class));
	}
}
