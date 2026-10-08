package bo.edu.uagrm.tienda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import bo.edu.uagrm.tienda.config.TokenJwt;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CuentaPropiaNoDesactivableException;
import bo.edu.uagrm.tienda.exception.ErrorNegocioException;
import bo.edu.uagrm.tienda.exception.RolDeCuentaDesactivadaException;
import bo.edu.uagrm.tienda.exception.RolPropioNoModificableException;
import bo.edu.uagrm.tienda.exception.UltimoAdministradorActivoException;
import bo.edu.uagrm.tienda.exception.UsuarioNoEncontradoException;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import bo.edu.uagrm.tienda.service.GestionUsuariosService;

// Autentica con tokens reales, como CuentaControllerTest: el rol sale de la cuenta que devuelve usuarioActivo
@WebMvcTest(UsuariosController.class)
@ActiveProfiles("test")
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class })
class UsuariosControllerTest {

	private static final String USUARIOS = "/api/usuarios";
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 12, 11, 0);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TokenJwt tokenJwt;

	@MockitoBean
	private AutenticacionService autenticacionService;

	@MockitoBean
	private GestionUsuariosService gestionUsuariosService;

	private final Usuario admin = conId(Usuario.crearAdministrador("Administrador", "Inicial", "admin@tienda.com",
			"$2a$10$hash", REGISTRO), 1L);
	private final Usuario ana = conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash",
			"70000000", REGISTRO), 7L);
	private final Usuario beto = conId(Usuario.registrarCliente("Beto", "Anaya", "beto@mail.com", "$2a$10$hash", null, REGISTRO), 8L);

	private static Usuario conId(Usuario usuario, long id) {
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	@BeforeEach
	void sesiones() {
		beto.asignarRol(Rol.EMPLEADO);
		given(autenticacionService.usuarioActivo(1L)).willReturn(Optional.of(admin));
		given(autenticacionService.usuarioActivo(7L)).willReturn(Optional.of(ana));
		given(autenticacionService.usuarioActivo(8L)).willReturn(Optional.of(beto));
	}

	private String bearer(Usuario sesion) {
		return "Bearer " + tokenJwt.emitir(sesion).token();
	}

	private MvcTestResult listar(String consulta, Usuario sesion) {
		return mvc.get().uri(USUARIOS + consulta).header(HttpHeaders.AUTHORIZATION, bearer(sesion)).exchange();
	}

	private MvcTestResult cambiarRol(String idUsuario, String cuerpo, Usuario sesion) {
		return mvc.put().uri(USUARIOS + "/" + idUsuario + "/rol").header(HttpHeaders.AUTHORIZATION, bearer(sesion))
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult cambiarEstado(String idUsuario, String cuerpo, Usuario sesion) {
		return mvc.put().uri(USUARIOS + "/" + idUsuario + "/estado").header(HttpHeaders.AUTHORIZATION, bearer(sesion))
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	@Test
	void sinSesionLasTresOperacionesDevuelven401() {
		assertThat(mvc.get().uri(USUARIOS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put().uri(USUARIOS + "/7/rol").contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"EMPLEADO\"}").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult estado = mvc.put().uri(USUARIOS + "/7/estado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\":\"DESACTIVADA\"}").exchange();

		assertThat(estado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(estado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
		then(gestionUsuariosService).shouldHaveNoInteractions();
	}

	@Test
	void clienteYPersonalReciben403ConElFormatoDeLaApi() {
		MvcTestResult cliente = listar("", ana);
		MvcTestResult personal = cambiarRol("7", "{\"rol\":\"ADMINISTRADOR\"}", beto);

		assertThat(cliente).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(cliente).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(cliente).bodyJson().extractingPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
		assertThat(personal).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(personal).bodyJson().extractingPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
		assertThat(cambiarEstado("8", "{\"estado\":\"DESACTIVADA\"}", ana)).hasStatus(HttpStatus.FORBIDDEN);
		then(gestionUsuariosService).shouldHaveNoInteractions();
	}

	@Test
	void listadoPasaLosFiltrosYDevuelveLaPaginaSinDatosSensibles() throws Exception {
		given(gestionUsuariosService.listar(Rol.CLIENTE, EstadoCuenta.ACTIVA, "ana", 1, 5))
				.willReturn(new PageImpl<>(List.of(ana), PageRequest.of(1, 5), 6));

		MvcTestResult resultado = listar("?rol=CLIENTE&estado=ACTIVA&texto=ana&pagina=1&tamano=5", admin);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].idUsuario").isEqualTo(7);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Ana");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].apellido").isEqualTo("Rojas");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].rol").isEqualTo("CLIENTE");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("ACTIVA");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].fechaRegistro").isEqualTo("2026-09-12T11:00:00");
		assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].telefono");
		assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].contrasena");
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("70000000", "$2a$10$hash");
		assertThat(resultado).bodyJson().extractingPath("$.pagina").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.tamano").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(6);
		assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
	}

	@Test
	void listadoSinParametrosPideLaPrimeraPaginaDe20SinFiltros() {
		given(gestionUsuariosService.listar(null, null, null, 0, 20))
				.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		assertThat(listar("", admin)).hasStatus(HttpStatus.OK);

		then(gestionUsuariosService).should().listar(null, null, null, 0, 20);
	}

	@Test
	void filtroDeRolInvalidoDevuelve400ConElCampo() {
		MvcTestResult resultado = listar("?rol=SUPERADMIN", admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactly("rol");
		then(gestionUsuariosService).shouldHaveNoInteractions();
	}

	@Test
	void cambioDeRolUsaElAdministradorDeLaSesionYDevuelveElResumen() {
		given(gestionUsuariosService.cambiarRol(1L, 7L, Rol.EMPLEADO)).willReturn(ana);

		MvcTestResult resultado = cambiarRol("7", "{\"rol\":\"EMPLEADO\",\"idAdministrador\":99}", admin);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().doesNotHavePath("$.telefono");
		then(gestionUsuariosService).should().cambiarRol(1L, 7L, Rol.EMPLEADO);
	}

	@Test
	void rolInvalidoOAusenteDevuelve400SinLlamarAlServicio() {
		MvcTestResult invalido = cambiarRol("7", "{\"rol\":\"SUPERADMIN\"}", admin);
		MvcTestResult ausente = cambiarRol("7", "{}", admin);

		assertThat(invalido).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(invalido).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactly("rol");
		assertThat(ausente).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ausente).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(ausente).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactly("rol");
		then(gestionUsuariosService).should(never()).cambiarRol(any(), any(), any());
	}

	@Test
	void idDeUsuarioNoNumericoDevuelve400ConElCampo() {
		MvcTestResult resultado = cambiarRol("abc", "{\"rol\":\"EMPLEADO\"}", admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactly("idUsuario");
	}

	@Test
	void cambioDeEstadoUsaElAdministradorDeLaSesion() {
		given(gestionUsuariosService.cambiarEstado(1L, 7L, EstadoCuenta.DESACTIVADA)).willReturn(ana);

		assertThat(cambiarEstado("7", "{\"estado\":\"DESACTIVADA\"}", admin)).hasStatus(HttpStatus.OK);

		then(gestionUsuariosService).should().cambiarEstado(1L, 7L, EstadoCuenta.DESACTIVADA);
	}

	@Test
	void estadoInvalidoDevuelve400ConElCampo() {
		MvcTestResult resultado = cambiarEstado("7", "{\"estado\":\"BORRADA\"}", admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactly("estado");
		then(gestionUsuariosService).should(never()).cambiarEstado(any(), any(), any());
	}

	static Stream<Arguments> cambioDeRolNoPermitidoDevuelve409ConSuCodigo() {
		return Stream.of(
				Arguments.of(new RolPropioNoModificableException(), "ROL_PROPIO_NO_MODIFICABLE"),
				Arguments.of(new UltimoAdministradorActivoException(), "ULTIMO_ADMINISTRADOR_ACTIVO"),
				Arguments.of(new RolDeCuentaDesactivadaException(), "ROL_DE_CUENTA_DESACTIVADA"));
	}

	@ParameterizedTest
	@MethodSource
	void cambioDeRolNoPermitidoDevuelve409ConSuCodigo(ErrorNegocioException error, String codigo) {
		given(gestionUsuariosService.cambiarRol(anyLong(), anyLong(), any(Rol.class))).willThrow(error);

		MvcTestResult resultado = cambiarRol("7", "{\"rol\":\"CLIENTE\"}", admin);

		assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	@Test
	void unFalloDeBloqueoDevuelve409YNo500() {
		// El diseño de la gestión de usuarios cuenta con que dos operaciones simultáneas se esperen; si una agota la espera, la API
		// responde en su formato y no como un error inesperado
		given(gestionUsuariosService.cambiarRol(anyLong(), anyLong(), any(Rol.class)))
				.willThrow(new CannotAcquireLockException("tiempo de espera del bloqueo agotado"));

		MvcTestResult resultado = cambiarRol("7", "{\"rol\":\"CLIENTE\"}", admin);

		assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("OPERACION_SIMULTANEA");
	}

	@Test
	void desactivarLaPropiaCuentaDevuelve409() {
		given(gestionUsuariosService.cambiarEstado(1L, 1L, EstadoCuenta.DESACTIVADA))
				.willThrow(new CuentaPropiaNoDesactivableException());

		MvcTestResult resultado = cambiarEstado("1", "{\"estado\":\"DESACTIVADA\"}", admin);

		assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_PROPIA_NO_DESACTIVABLE");
	}

	@Test
	void usuarioInexistenteDevuelve404() {
		given(gestionUsuariosService.cambiarEstado(1L, 99L, EstadoCuenta.DESACTIVADA))
				.willThrow(new UsuarioNoEncontradoException());

		MvcTestResult resultado = cambiarEstado("99", "{\"estado\":\"DESACTIVADA\"}", admin);

		assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("USUARIO_NO_ENCONTRADO");
	}
}
