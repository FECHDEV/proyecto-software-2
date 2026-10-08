package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest {

	private static final LocalDateTime FECHA_REGISTRO = LocalDateTime.of(2026, 9, 12, 11, 0);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private UsuarioRepository usuarioRepository;

	private static Usuario cliente(String correo) {
		return Usuario.registrarCliente("Ana", "Rojas", correo, "$2a$10$hash", null, FECHA_REGISTRO);
	}

	private static Usuario administrador(String correo) {
		return Usuario.crearAdministrador("Administrador", "Inicial", correo, "$2a$10$hash", FECHA_REGISTRO);
	}

	@Test
	void guardaYLeeTodasLasColumnas() {
		Long id = em.persistAndGetId(cliente("ana@mail.com"), Long.class);
		em.flush();
		em.clear();

		Usuario leido = usuarioRepository.findById(id).orElseThrow();

		assertThat(leido.getNombre()).isEqualTo("Ana");
		assertThat(leido.getApellido()).isEqualTo("Rojas");
		assertThat(leido.getCorreo()).isEqualTo("ana@mail.com");
		assertThat(leido.getContrasena()).isEqualTo("$2a$10$hash");
		assertThat(leido.getTelefono()).isNull();
		assertThat(leido.getRol()).isEqualTo(Rol.CLIENTE);
		assertThat(leido.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(leido.getFechaRegistro()).isEqualTo(FECHA_REGISTRO);
	}

	@Test
	void existsByCorreoDistingueCorreosRegistrados() {
		em.persist(cliente("ana@mail.com"));
		em.flush();
		em.clear();

		assertThat(usuarioRepository.existsByCorreo("ana@mail.com")).isTrue();
		assertThat(usuarioRepository.existsByCorreo("otra@mail.com")).isFalse();
	}

	@Test
	void correoRepetidoSeReconoceComoDuplicado() {
		em.persistAndFlush(cliente("ana@mail.com"));

		assertThatThrownBy(() -> usuarioRepository.saveAndFlush(cliente("ana@mail.com")))
				.isInstanceOfSatisfying(DataIntegrityViolationException.class,
						ex -> assertThat(UsuarioRepository.esCorreoDuplicado(ex)).isTrue());
	}

	@Test
	void datoDemasiadoLargoNoSeConfundeConCorreoDuplicado() {
		Usuario nombreLargo = Usuario.registrarCliente("a".repeat(81), "Rojas", "ana@mail.com", "$2a$10$hash", null, FECHA_REGISTRO);

		assertThatThrownBy(() -> usuarioRepository.saveAndFlush(nombreLargo))
				.isInstanceOfSatisfying(DataIntegrityViolationException.class,
						ex -> assertThat(UsuarioRepository.esCorreoDuplicado(ex)).isFalse());
	}

	@Test
	void findByCorreoDevuelveLaCuentaRegistrada() {
		em.persist(cliente("ana@mail.com"));
		em.flush();
		em.clear();

		assertThat(usuarioRepository.findByCorreo("ana@mail.com"))
				.hasValueSatisfying(usuario -> assertThat(usuario.getNombre()).isEqualTo("Ana"));
		assertThat(usuarioRepository.findByCorreo("otra@mail.com")).isEmpty();
	}

	@Test
	void existsByRolIndicaSiHayAlgunAdministrador() {
		// La H2 es compartida: un @SpringBootTest que arrancó antes pudo crear el administrador inicial. El borrado se
		// deshace al terminar el test
		em.getEntityManager().createQuery("delete from Usuario u where u.rol = :rol")
				.setParameter("rol", Rol.ADMINISTRADOR).executeUpdate();
		em.persist(cliente("ana@mail.com"));
		em.flush();
		em.clear();
		assertThat(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).isFalse();

		em.persist(administrador("admin@tienda.com"));
		em.flush();
		em.clear();
		assertThat(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).isTrue();
	}

	@Test
	void administradorSeGuardaActivoConCorreoNormalizadoYSinFechaDeNacimiento() {
		Long id = em.persistAndGetId(administrador(" Admin@Tienda.com "), Long.class);
		em.flush();
		em.clear();

		Usuario leido = usuarioRepository.findById(id).orElseThrow();

		assertThat(leido.getRol()).isEqualTo(Rol.ADMINISTRADOR);
		assertThat(leido.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(leido.getCorreo()).isEqualTo("admin@tienda.com");
		assertThat(leido.getNombre()).isEqualTo("Administrador");
		assertThat(leido.getApellido()).isEqualTo("Inicial");
		assertThat(leido.getTelefono()).isNull();
		assertThat(leido.getFechaRegistro()).isEqualTo(FECHA_REGISTRO);
	}

	@Test
	void cambiarContrasenaReemplazaElHashGuardado() {
		Long id = em.persistAndGetId(cliente("ana@mail.com"), Long.class);
		em.flush();
		em.clear();

		usuarioRepository.findById(id).orElseThrow().cambiarContrasena("$2a$10$hashNuevo");
		em.flush();
		em.clear();

		assertThat(usuarioRepository.findById(id).orElseThrow().getContrasena()).isEqualTo("$2a$10$hashNuevo");
	}

	private static final Pageable TODOS = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fechaRegistro", "idUsuario"));

	private static Usuario cliente(String nombre, String apellido, String correo, LocalDateTime fechaRegistro) {
		return Usuario.registrarCliente(nombre, apellido, correo, "$2a$10$hash", null, fechaRegistro);
	}

	// La H2 es compartida con los @SpringBootTest: se vacía dentro de la transacción del test, que se deshace al terminar
	private void vaciarUsuarios() {
		em.getEntityManager().createQuery("delete from RecuperacionContrasena").executeUpdate();
		em.getEntityManager().createQuery("delete from Usuario").executeUpdate();
	}

	private void registrarUsuariosDeBusqueda() {
		em.persist(Usuario.crearAdministrador("Administrador", "Inicial", "admin@tienda.com", "$2a$10$hash",
				LocalDateTime.of(2026, 9, 9, 10, 0)));
		em.persist(cliente("Ana", "Rojas", "ana@mail.com", LocalDateTime.of(2026, 9, 10, 10, 0)));
		Usuario beto = cliente("Beto", "Anaya", "beto@mail.com", LocalDateTime.of(2026, 9, 11, 10, 0));
		beto.asignarRol(Rol.EMPLEADO);
		em.persist(beto);
		Usuario carla = cliente("Carla", "Paz", "carla@mail.com", LocalDateTime.of(2026, 9, 12, 10, 0));
		carla.desactivar();
		em.persist(carla);
		em.persist(cliente("Dani", "Suarez", "dan_i@mail.com", LocalDateTime.of(2026, 9, 13, 10, 0)));
		em.flush();
		em.clear();
	}

	private static List<String> correos(Page<Usuario> pagina) {
		return pagina.getContent().stream().map(Usuario::getCorreo).toList();
	}

	@Test
	void rolYEstadoSeGuardanYLaFechaDeNacimientoSeConserva() {
		Long id = em.persistAndGetId(cliente("ana@mail.com"), Long.class);
		em.flush();
		em.clear();

		Usuario ana = usuarioRepository.findById(id).orElseThrow();
		ana.asignarRol(Rol.EMPLEADO);
		ana.desactivar();
		em.flush();
		em.clear();

		Usuario leida = usuarioRepository.findById(id).orElseThrow();
		assertThat(leida.getRol()).isEqualTo(Rol.EMPLEADO);
		assertThat(leida.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
		assertThat(leida.estaActiva()).isFalse();

		leida.activar();
		em.flush();
		em.clear();
		assertThat(usuarioRepository.findById(id).orElseThrow().estaActiva()).isTrue();
	}

	@Test
	void administradoresActivosConBloqueoExcluyeLosDesactivadosYLosOtrosRolesYVienenOrdenadosPorId() {
		vaciarUsuarios();
		Long primero = em.persistAndGetId(administrador("uno@tienda.com"), Long.class);
		Long segundo = em.persistAndGetId(administrador("dos@tienda.com"), Long.class);
		Usuario desactivado = administrador("tres@tienda.com");
		desactivado.desactivar();
		em.persist(desactivado);
		em.persist(cliente("ana@mail.com"));
		em.flush();
		em.clear();

		assertThat(usuarioRepository.findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA))
				.extracting(Usuario::getIdUsuario).containsExactly(primero, segundo);
	}

	@Test
	void buscarSinFiltrosDevuelveTodosDelRegistroMasRecienteAlMasAntiguoPorPaginas() {
		vaciarUsuarios();
		registrarUsuariosDeBusqueda();
		Sort orden = Sort.by(Sort.Direction.DESC, "fechaRegistro", "idUsuario");

		Page<Usuario> primera = usuarioRepository.buscar(null, null, null, PageRequest.of(0, 2, orden));
		Page<Usuario> tercera = usuarioRepository.buscar(null, null, null, PageRequest.of(2, 2, orden));

		assertThat(correos(primera)).containsExactly("dan_i@mail.com", "carla@mail.com");
		assertThat(correos(tercera)).containsExactly("admin@tienda.com");
		assertThat(primera.getTotalElements()).isEqualTo(5);
		assertThat(primera.getTotalPages()).isEqualTo(3);
		assertThat(correos(usuarioRepository.buscar(null, null, null, PageRequest.of(7, 2, orden)))).isEmpty();
	}

	@Test
	void buscarCombinaRolEstadoYTextoEnCorreoNombreYApellido() {
		vaciarUsuarios();
		registrarUsuariosDeBusqueda();

		assertThat(correos(usuarioRepository.buscar(Rol.CLIENTE, null, null, TODOS)))
				.containsExactly("dan_i@mail.com", "carla@mail.com", "ana@mail.com");
		assertThat(correos(usuarioRepository.buscar(Rol.CLIENTE, EstadoCuenta.ACTIVA, null, TODOS)))
				.containsExactly("dan_i@mail.com", "ana@mail.com");
		// "Ana" por nombre y correo, "Anaya" por apellido: la columna se compara en minúsculas
		assertThat(correos(usuarioRepository.buscar(null, null, "%ana%", TODOS)))
				.containsExactly("beto@mail.com", "ana@mail.com");
		assertThat(correos(usuarioRepository.buscar(Rol.EMPLEADO, null, "%ana%", TODOS))).containsExactly("beto@mail.com");
		assertThat(correos(usuarioRepository.buscar(null, EstadoCuenta.DESACTIVADA, "%ana%", TODOS))).isEmpty();
	}

	@Test
	void laTablaUsuarioTieneUnIndicePorRolYEstado() {
		// El bloqueo de administradores activos filtra por rol y estado: sin índice, MySQL bloquea en cada operación de
		// la gestión de usuarios todas las filas que recorre, y hasta un registro nuevo o un cambio de contraseña quedan esperando
		java.util.List<?> columnas = em.getEntityManager().createNativeQuery("""
				select column_name from information_schema.index_columns
				where upper(table_name) = 'USUARIO' and upper(index_name) = 'IDX_USUARIO_ROL_ESTADO'
				order by ordinal_position
				""").getResultList();

		assertThat(columnas).extracting(columna -> String.valueOf(columna).toLowerCase(java.util.Locale.ROOT))
				.containsExactly("rol", "estado");
	}

	@Test
	void comodinesEscapadosDelTextoSeBuscanLiteralmente() {
		vaciarUsuarios();
		registrarUsuariosDeBusqueda();

		// Sin el escape, "n_" también encontraría "ana" y "anaya"
		assertThat(correos(usuarioRepository.buscar(null, null, "%n!_%", TODOS))).containsExactly("dan_i@mail.com");
		assertThat(correos(usuarioRepository.buscar(null, null, "%!%%", TODOS))).isEmpty();
	}
}
