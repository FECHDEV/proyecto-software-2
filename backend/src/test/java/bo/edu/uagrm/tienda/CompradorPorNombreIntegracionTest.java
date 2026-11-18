package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-23 RF-25 a RF-30 (ampliación del 18/11/2026): el comprador del mostrador por nombre o apellido. Los nombres son
// raros a propósito: la H2 es compartida y otras pruebas crean cuentas «Ana Paz»
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompradorPorNombreIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String CLIENTES = "/api/ventas/clientes";
	// Todas las cuentas de esta prueba terminan así, para borrarlas al final
	private static final String DOMINIO = ".hu23n@mail.com";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private LineaCarritoRepository lineaRepository;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private CobroRepository cobroRepository;

	@Autowired
	private PedidoRepository pedidoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	private String admin;
	private String empleado;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		empleado = personal("Eulalio", "Vendedor", "eulalio", Rol.EMPLEADO);
	}

	@AfterEach
	void limpiar() {
		lineaRepository.deleteAll();
		movimientoRepository.deleteAll();
		cobroRepository.deleteAll();
		pedidoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		usuarioRepository.findAll().stream().filter(usuario -> usuario.getCorreo().endsWith(DOMINIO))
				.forEach(usuarioRepository::delete);
	}

	// --- Ayudantes ---

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	// Registrada por la API, para tener su token; sin verificar el correo, como toda cuenta nueva
	private String registrar(String nombre, String apellido, String usuario) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"%s\",\"apellido\":\"%s\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(nombre, apellido, usuario + DOMINIO))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private String personal(String nombre, String apellido, String usuario, Rol rol) throws Exception {
		registrar(nombre, apellido, usuario);
		Usuario cuenta = usuarioRepository.findByCorreo(usuario + DOMINIO).orElseThrow();
		cuenta.asignarRol(rol);
		usuarioRepository.save(cuenta);
		return token(usuario + DOMINIO, "secreta12");
	}

	// Directo en la base: más rápido para muchas cuentas
	private Long cliente(String nombre, String apellido, String usuario) {
		return usuarioRepository.save(Usuario.registrarCliente(nombre, apellido, usuario + DOMINIO, "$2a$10$hash",
				null, LocalDateTime.of(2026, 11, 18, 9, 0))).getIdUsuario();
	}

	private MvcTestResult buscar(String nombre, String token) {
		return mvc.get().uri(CLIENTES).param("nombre", nombre).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange();
	}

	private MvcTestResult post(String uri, String token, String cuerpo, Object... variables) {
		return mvc.post().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	// --- RF-25 a RF-29 ---

	// HU-23 RF-25: por nombre o por apellido, sin distinguir mayúsculas ni tildes, y por una parte de la palabra
	@Test
	void encuentraPorNombreOApellidoSinMayusculasNiTildes() {
		cliente("José", "Peñaranda", "jose");
		cliente("Ximena", "Quiroga", "ximena");

		assertThat(buscar("PEÑARANDA", empleado)).bodyJson().extractingPath("$.cuentas[*].correo").asArray()
				.containsExactly("jo***@mail.com");
		assertThat(buscar("penaranda", empleado)).bodyJson().extractingPath("$.cuentas[*].nombre").asArray()
				.containsExactly("José Peñaranda");
		assertThat(buscar("quiro", empleado)).bodyJson().extractingPath("$.cuentas[*].nombre").asArray()
				.containsExactly("Ximena Quiroga");
	}

	// HU-23 RF-25: el texto entero dentro de «nombre apellido», con un solo espacio entre palabras
	@Test
	void buscaElNombreSeguidoDelApellido() {
		cliente("Ximena", "Quiroga", "ximena");

		assertThat(buscar("ximena   quiroga", empleado)).bodyJson().extractingPath("$.cuentas[*].nombre").asArray()
				.containsExactly("Ximena Quiroga");
		assertThat(buscar("quiroga ximena", empleado)).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
	}

	// HU-23 RF-25 y casos límite: solo Clientes activos, aunque no hayan verificado el correo
	@Test
	void soloClientesActivosConOSinCorreoVerificadoNiPersonalNiDesactivados() throws Exception {
		cliente("Zenón", "Activo", "zenon.activo");
		personal("Zenón", "Empleado", "zenon.empleado", Rol.EMPLEADO);
		Usuario desactivado = usuarioRepository.findById(cliente("Zenón", "Desactivado", "zenon.desactivado"))
				.orElseThrow();
		desactivado.desactivar();
		usuarioRepository.save(desactivado);

		assertThat(buscar("zenon", empleado)).bodyJson().extractingPath("$.cuentas[*].nombre").asArray()
				.containsExactly("Zenón Activo");
	}

	// HU-23 RF-26: el correo viene enmascarado, y el completo no aparece en ninguna parte de la respuesta
	@Test
	void elCorreoVieneEnmascaradoYNuncaCompleto() throws Exception {
		Long id = cliente("Ximena", "Quiroga", "ximena");

		MvcTestResult resultado = buscar("ximena", empleado);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.cuentas[0].idUsuario").isEqualTo(id.intValue());
		assertThat(resultado).bodyJson().extractingPath("$.cuentas[0].correo").isEqualTo("xi***@mail.com");
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("ximena" + DOMINIO);
	}

	// HU-23 RF-28: la búsqueda por nombre no mira el correo
	@Test
	void noBuscaEnElCorreo() {
		cliente("Ximena", "Quiroga", "ximena");

		assertThat(buscar("hu23n", empleado)).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
		assertThat(buscar("@mail", empleado)).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
	}

	// HU-23 RF-25: hasta 10, por nombre y apellido, y avisa si hay más
	@Test
	void hastaDiezEnOrdenYAvisaSiHayMas() {
		for (char letra = 'L'; letra >= 'A'; letra--) {
			cliente("Zenobia", "Apellido" + letra, "zenobia" + letra);
		}

		MvcTestResult doce = buscar("zenobia", empleado);
		assertThat(doce).bodyJson().extractingPath("$.cuentas[*].nombre").asArray().hasSize(10)
				.startsWith("Zenobia ApellidoA", "Zenobia ApellidoB").endsWith("Zenobia ApellidoJ");
		assertThat(doce).bodyJson().extractingPath("$.hayMas").isEqualTo(true);

		MvcTestResult una = buscar("zenobia apellidol", empleado);
		assertThat(una).bodyJson().extractingPath("$.cuentas").asArray().hasSize(1);
		assertThat(una).bodyJson().extractingPath("$.hayMas").isEqualTo(false);
	}

	// HU-23 RF-29: menos de 3 letras, sin contar los espacios, da una lista vacía
	@Test
	void conMenosDeTresLetrasDaVacia() {
		cliente("Ze", "Ze", "zeze");

		for (String texto : new String[] { "ze", "z e", "   ", "" }) {
			MvcTestResult resultado = buscar(texto, empleado);
			assertThat(resultado).hasStatus(HttpStatus.OK);
			assertThat(resultado).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
			assertThat(resultado).bodyJson().extractingPath("$.hayMas").isEqualTo(false);
		}
	}

	// Casos límite: los comodines se buscan como letras
	@Test
	void losComodinesSeBuscanComoLetras() {
		cliente("Ximena", "Quiroga", "ximena");

		assertThat(buscar("%%%", empleado)).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
		assertThat(buscar("___", empleado)).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
	}

	// Casos límite: un texto de cientos de caracteres no rompe nada
	@Test
	void unTextoMuyLargoDaVaciaSinError() {
		MvcTestResult resultado = buscar("x".repeat(500), empleado);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.cuentas").asArray().isEmpty();
	}

	// HU-23 RF-27: elegida por nombre, la venta queda a nombre de esa cuenta
	@Test
	void laVentaQuedaAlNombreDeLaCuentaElegida() throws Exception {
		cliente("Ximena", "Quiroga", "ximena");
		Integer categoria = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"),
				"$.idCategoria");
		MvcTestResult creado = post("/api/gestion/productos", admin,
				"{\"nombre\":\"Martillo\",\"idCategoria\":%d,\"unidad\":\"UNIDAD\",\"precio\":85}".formatted(categoria));
		Integer martillo = id(creado, "$.idProducto");
		assertThat(post("/api/inventario/{id}/entradas", admin, "{\"cantidad\":5}", martillo))
				.hasStatus(HttpStatus.CREATED);
		Integer idCliente = id(buscar("ximena", empleado), "$.cuentas[0].idUsuario");

		MvcTestResult venta = post("/api/ventas", empleado,
				"{\"lineas\":[{\"idProducto\":%d,\"cantidad\":1}],\"idCliente\":%d,\"medio\":\"EFECTIVO\",\"recibido\":100,\"totalEsperado\":85}"
						.formatted(martillo, idCliente));

		assertThat(venta).hasStatus(HttpStatus.CREATED);
		assertThat(venta).bodyJson().extractingPath("$.comprador.idUsuario").isEqualTo(idCliente);
		assertThat(venta).bodyJson().extractingPath("$.comprador.nombre").isEqualTo("Ximena Quiroga");
	}

	// --- RF-30 ---

	// HU-23 RF-30: buscar compradores es para vender
	@Test
	void elContadorYElClienteReciben403YSinSesion401() throws Exception {
		String contador = personal("Zoilo", "Contador", "zoilo", Rol.CONTADOR);
		String clienta = registrar("Ximena", "Quiroga", "ximena");

		assertThat(buscar("ximena", contador)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(buscar("ximena", clienta)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(CLIENTES).param("nombre", "ximena").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(buscar("ximena", admin)).hasStatus(HttpStatus.OK);
	}
}
