package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.RecuperacionContrasena;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CuentaPropiaNoDesactivableException;
import bo.edu.uagrm.tienda.exception.RolDeCuentaDesactivadaException;
import bo.edu.uagrm.tienda.exception.RolPropioNoModificableException;
import bo.edu.uagrm.tienda.exception.UltimoAdministradorActivoException;
import bo.edu.uagrm.tienda.exception.UsuarioNoEncontradoException;
import bo.edu.uagrm.tienda.repository.RecuperacionContrasenaRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class GestionUsuariosServiceTest {

	private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaRegistro", "idUsuario");
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 12, 11, 0);

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private RecuperacionContrasenaRepository recuperacionRepository;

	private GestionUsuariosService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new GestionUsuariosService(usuarioRepository, recuperacionRepository);
	}

	private static Usuario conId(Usuario usuario, long id) {
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	private void listadoVacio() {
		given(usuarioRepository.buscar(any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));
	}

	// HU-05 RF-2
	@Test
	void listarPideLaPaginaIndicadaDelRegistroMasRecienteAlMasAntiguo() {
		Page<Usuario> pagina = new PageImpl<>(List.of(conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com",
				"$2a$10$hash", null, REGISTRO), 7L)));
		given(usuarioRepository.buscar(any(), any(), any(), any())).willReturn(pagina);

		assertThat(servicio.listar(Rol.CLIENTE, EstadoCuenta.ACTIVA, null, 2, 30)).isSameAs(pagina);

		then(usuarioRepository).should().buscar(Rol.CLIENTE, EstadoCuenta.ACTIVA, null, PageRequest.of(2, 30, ORDEN));
	}

	@Test
	void tamanoMayorA100UsaCienYPaginaOTamanoInvalidosUsanLosPorDefecto() {
		listadoVacio();

		servicio.listar(null, null, null, -1, 500);
		servicio.listar(null, null, null, 0, 0);

		then(usuarioRepository).should().buscar(null, null, null, PageRequest.of(0, 100, ORDEN));
		then(usuarioRepository).should().buscar(null, null, null, PageRequest.of(0, 20, ORDEN));
	}

	// HU-05 RF-3
	@Test
	void textoSeBuscaRecortadoEnMinusculasYConLosComodinesEscapados() {
		listadoVacio();

		servicio.listar(null, null, "  Ana_50%! ", 0, 20);

		then(usuarioRepository).should().buscar(null, null, "%ana!_50!%!!%", PageRequest.of(0, 20, ORDEN));
	}

	// HU-05 RF-3
	@Test
	void textoVacioOEnBlancoNoFiltra() {
		listadoVacio();

		servicio.listar(null, null, "", 0, 20);
		servicio.listar(null, null, "   ", 0, 20);

		then(usuarioRepository).should(times(2)).buscar(null, null, null, PageRequest.of(0, 20, ORDEN));
	}

	private static final long ID_ADMIN = 1L;
	private static final long ID_OTRO_ADMIN = 2L;
	private static final long ID_ANA = 7L;

	private static Usuario administrador(long id) {
		return conId(Usuario.crearAdministrador("Administrador", "Inicial", "admin" + id + "@tienda.com", "$2a$10$hash",
				REGISTRO), id);
	}

	private static Usuario ana() {
		return conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null, REGISTRO),
				ID_ANA);
	}

	private void administradoresActivos(Usuario... administradores) {
		given(usuarioRepository.findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA))
				.willReturn(List.of(administradores));
	}

	private void cuenta(Usuario usuario) {
		given(usuarioRepository.findConBloqueoByIdUsuario(usuario.getIdUsuario())).willReturn(Optional.of(usuario));
	}

	// --- cambiarRol ---

	// HU-05 RF-4, RF-9
	@Test
	void asignaElRolBloqueandoPrimeroLosAdministradoresYConservaLaFecha() {
		Usuario ana = ana();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThat(servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.EMPLEADO)).isSameAs(ana);

		assertThat(ana.getRol()).isEqualTo(Rol.EMPLEADO);
		InOrder orden = inOrder(usuarioRepository);
		orden.verify(usuarioRepository).findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA);
		orden.verify(usuarioRepository).findConBloqueoByIdUsuario(ID_ANA);
	}

	// HU-05 RF-4
	@Test
	void cuentaConFechaVuelveAClienteDesdeOtroRol() {
		Usuario ana = ana();
		ana.asignarRol(Rol.EMPLEADO);
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThat(servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.CLIENTE).getRol()).isEqualTo(Rol.CLIENTE);
	}

	// HU-05 RF-7
	@Test
	void propioRolSeRechazaAunqueSeaElMismoSinTocarLaBase() {
		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_ADMIN, Rol.ADMINISTRADOR))
				.isInstanceOf(RolPropioNoModificableException.class);
		then(usuarioRepository).shouldHaveNoInteractions();
	}

	// HU-05 RF-11
	@Test
	void usuarioInexistenteAlCambiarElRol() {
		administradoresActivos(administrador(ID_ADMIN));
		given(usuarioRepository.findConBloqueoByIdUsuario(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, 99L, Rol.EMPLEADO))
				.isInstanceOf(UsuarioNoEncontradoException.class);
	}

	// HU-05 RF-9
	@Test
	void quitarElRolAlUltimoAdministradorActivoSeRechaza() {
		// Solo se alcanza con operaciones simultáneas: quien opera ya dejó de ser administrador al llegar el bloqueo
		Usuario ultimo = administrador(ID_OTRO_ADMIN);
		administradoresActivos(ultimo);
		cuenta(ultimo);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.EMPLEADO))
				.isInstanceOf(UltimoAdministradorActivoException.class);
		assertThat(ultimo.getRol()).isEqualTo(Rol.ADMINISTRADOR);
	}

	// HU-05 RF-9
	@Test
	void quitarElRolAUnAdministradorConOtroActivoSePermite() {
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(administrador(ID_ADMIN), otro);
		cuenta(otro);

		assertThat(servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.EMPLEADO).getRol()).isEqualTo(Rol.EMPLEADO);
	}

	// HU-05 RF-9
	@Test
	void filaQueYaNoEsAdministradorActivoNoCuentaComoOtroAdministrador() {
		Usuario yaDegradado = administrador(ID_ADMIN);
		yaDegradado.asignarRol(Rol.EMPLEADO);
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(yaDegradado, otro);
		cuenta(otro);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.EMPLEADO))
				.isInstanceOf(UltimoAdministradorActivoException.class);
	}

	// HU-05 RF-10
	@Test
	void rolDeCuentaDesactivadaSeRechazaAunqueSeaElMismo() {
		Usuario ana = ana();
		ana.desactivar();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.CLIENTE))
				.isInstanceOf(RolDeCuentaDesactivadaException.class);
	}

	// HU-05 RF-9
	@Test
	void ultimoAdministradorSinFechaRespondePrimeroPorElUltimoAdministrador() {
		Usuario ultimo = administrador(ID_OTRO_ADMIN);
		administradoresActivos(ultimo);
		cuenta(ultimo);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.CLIENTE))
				.isInstanceOf(UltimoAdministradorActivoException.class);
	}

	// HU-05 RF-10
	@Test
	void cuentaDesactivadaSinFechaRespondePrimeroPorLaCuentaDesactivada() {
		Usuario desactivado = administrador(ID_OTRO_ADMIN);
		desactivado.desactivar();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(desactivado);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.CLIENTE))
				.isInstanceOf(RolDeCuentaDesactivadaException.class);
	}

	// --- cambiarEstado ---

	// HU-05 RF-5
	@Test
	void desactivaLaCuentaDeOtroUsuarioSinCambiarSuRol() {
		Usuario ana = ana();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.DESACTIVADA)).isSameAs(ana);

		assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
		assertThat(ana.getRol()).isEqualTo(Rol.CLIENTE);
		InOrder orden = inOrder(usuarioRepository);
		orden.verify(usuarioRepository).findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA);
		orden.verify(usuarioRepository).findConBloqueoByIdUsuario(ID_ANA);
	}

	// HU-05 RF-6
	@Test
	void reactivaUnaCuentaDesactivadaSinOtrasReglas() {
		Usuario ana = ana();
		ana.desactivar();
		cuenta(ana);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.ACTIVA).getEstado())
				.isEqualTo(EstadoCuenta.ACTIVA);
	}

	// HU-03 RF-9: el enlace pedido antes de desactivar deja de servir, también si después se reactiva la cuenta
	// HU-05 RF-5
	@Test
	void desactivarAnulaLosEnlacesDeRecuperacionPendientes() {
		Usuario ana = ana();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);
		RecuperacionContrasena pendiente = RecuperacionContrasena.crear(ana, "a".repeat(64), REGISTRO);
		given(recuperacionRepository.findByUsuarioAndUsadoFalse(ana)).willReturn(List.of(pendiente));

		servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.DESACTIVADA);

		assertThat(pendiente.isUsado()).isTrue();
	}

	// HU-05 RF-6
	@Test
	void reactivarNoTocaLosEnlacesDeRecuperacion() {
		Usuario ana = ana();
		cuenta(ana);

		servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.ACTIVA);

		then(recuperacionRepository).shouldHaveNoInteractions();
	}

	// HU-05 RF-8
	@Test
	void desactivarLaPropiaCuentaSeRechazaSinTocarLaBase() {
		assertThatThrownBy(() -> servicio.cambiarEstado(ID_ADMIN, ID_ADMIN, EstadoCuenta.DESACTIVADA))
				.isInstanceOf(CuentaPropiaNoDesactivableException.class);
		then(usuarioRepository).shouldHaveNoInteractions();
	}

	// HU-05 RF-6
	@Test
	void activarLaPropiaCuentaNoTieneReglaExtra() {
		Usuario admin = administrador(ID_ADMIN);
		cuenta(admin);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_ADMIN, EstadoCuenta.ACTIVA).getEstado())
				.isEqualTo(EstadoCuenta.ACTIVA);
	}

	// HU-05 RF-6
	@Test
	void reactivarNoCuentaNiBloqueaALosAdministradores() {
		// Reactivar nunca puede dejar al sistema sin administradores activos, así que no hay nada que contar: bloquearlos
		// haría esperar a las demás operaciones de la gestión de usuarios sin motivo
		Usuario ana = ana();
		ana.desactivar();
		cuenta(ana);

		servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.ACTIVA);

		then(usuarioRepository).should(never()).findConBloqueoByRolAndEstadoOrderByIdUsuario(any(), any());
	}

	// HU-05 RF-9
	@Test
	void desactivarAlUltimoAdministradorActivoSeRechaza() {
		Usuario ultimo = administrador(ID_OTRO_ADMIN);
		administradoresActivos(ultimo);
		cuenta(ultimo);

		assertThatThrownBy(() -> servicio.cambiarEstado(ID_ADMIN, ID_OTRO_ADMIN, EstadoCuenta.DESACTIVADA))
				.isInstanceOf(UltimoAdministradorActivoException.class);
		assertThat(ultimo.estaActiva()).isTrue();
	}

	// HU-05 RF-9
	@Test
	void desactivarAUnAdministradorConOtroActivoSePermite() {
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(administrador(ID_ADMIN), otro);
		cuenta(otro);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_OTRO_ADMIN, EstadoCuenta.DESACTIVADA).estaActiva()).isFalse();
	}

	// HU-05 RF-9
	@Test
	void desactivarUnAdministradorYaDesactivadoNoLoCuentaComoElUltimo() {
		Usuario desactivado = administrador(ID_OTRO_ADMIN);
		desactivado.desactivar();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(desactivado);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_OTRO_ADMIN, EstadoCuenta.DESACTIVADA).estaActiva()).isFalse();
	}

	// HU-05 RF-11
	@Test
	void usuarioInexistenteAlCambiarElEstado() {
		administradoresActivos(administrador(ID_ADMIN));
		given(usuarioRepository.findConBloqueoByIdUsuario(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarEstado(ID_ADMIN, 99L, EstadoCuenta.DESACTIVADA))
				.isInstanceOf(UsuarioNoEncontradoException.class);
	}
}
