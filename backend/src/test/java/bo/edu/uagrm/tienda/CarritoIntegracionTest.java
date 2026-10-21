package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-11: la API del carrito de punta a punta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CarritoIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String CARRITO = "/api/carrito";
	private static final String LINEAS = CARRITO + "/lineas";
	private static final List<String> CORREOS = List.of("ana.hu11@mail.com", "beto.hu11@mail.com",
			"emp.hu11@mail.com", "con.hu11@mail.com");
	// La firma de un PNG: el formato se reconoce por el contenido
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0 };

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private LineaCarritoRepository lineaRepository;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	private String admin;
	private String ana;
	private String beto;
	private Integer herramientas;
	private Integer martillo;
	private Integer cable;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		ana = cuentaCon("ana.hu11@mail.com", null);
		beto = cuentaCon("beto.hu11@mail.com", null);
		herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		martillo = producto("Martillo", "UNIDAD", "85.50", "10");
		cable = producto("Cable", "METRO", "12", "100");
	}

	@AfterEach
	void limpiar() {
		lineaRepository.deleteAll();
		movimientoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		CORREOS.forEach(correo -> usuarioRepository.findByCorreo(correo).ifPresent(usuarioRepository::delete));
	}

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	// Se registra como cliente y, si hace falta, el Administrador le da otro rol
	private String cuentaCon(String correo, String rol) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(correo))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		String token = JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
		if (rol != null) {
			Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
			assertThat(put("/api/usuarios/{id}/rol", admin, "{\"rol\":\"%s\"}".formatted(rol), idUsuario))
					.hasStatus(HttpStatus.OK);
		}
		return token;
	}

	private Integer producto(String nombre, String unidad, String precio, String stock) throws Exception {
		MvcTestResult creado = post("/api/gestion/productos", admin, """
				{"nombre":"%s","idCategoria":%d,"unidad":"%s","precio":%s}"""
				.formatted(nombre, herramientas, unidad, precio));
		assertThat(creado).hasStatus(HttpStatus.CREATED);
		Integer id = id(creado, "$.idProducto");
		if (!"0".equals(stock)) {
			assertThat(post("/api/inventario/{id}/entradas", admin, "{\"cantidad\":%s}".formatted(stock), id))
					.hasStatus(HttpStatus.CREATED);
		}
		return id;
	}

	private MvcTestResult post(String uri, String token, String cuerpo, Object... variables) {
		return mvc.post().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult put(String uri, String token, String cuerpo, Object... variables) {
		return mvc.put().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult get(String uri, String token, Object... variables) {
		return mvc.get().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private MvcTestResult delete(String uri, String token, Object... variables) {
		return mvc.delete().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	private MvcTestResult agregar(String token, Integer idProducto, String cantidad) {
		return post(LINEAS, token, "{\"idProducto\":%s,\"cantidad\":%s}".formatted(idProducto, cantidad));
	}

	private MvcTestResult cambiar(String token, Integer idProducto, String cantidad) {
		return put(LINEAS + "/{id}", token, "{\"cantidad\":%s}".formatted(cantidad), idProducto);
	}

	private void ajustarStock(Integer idProducto, String contada) {
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":%s,\"motivo\":\"CONTEO\"}"
				.formatted(contada), idProducto)).hasStatus(HttpStatus.CREATED);
	}

	// HU-20 RF-7, RF-15: el carrito dice si se puede confirmar, y se pone al día sin volver a iniciar sesión
	@Test
	void elCarritoDiceSiElCorreoEstaVerificado() {
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.correoVerificado").isEqualTo(false);

		Usuario cuenta = usuarioRepository.findByCorreo("ana.hu11@mail.com").orElseThrow();
		cuenta.marcarCorreoVerificado();
		usuarioRepository.save(cuenta);

		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.correoVerificado").isEqualTo(true);
	}

	// HU-11 RF-1
	@Test
	void agregaUnProductoDisponible() {
		MvcTestResult agregado = agregar(ana, martillo, "1");

		assertThat(agregado).hasStatus(HttpStatus.OK);
		assertThat(agregado).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(1);
		assertThat(agregado).bodyJson().extractingPath("$.lineas[0].idProducto").isEqualTo(martillo);
		assertThat(agregado).bodyJson().extractingPath("$.lineas[0].nombre").isEqualTo("Martillo");
		assertThat(agregado).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(1.0);
		assertThat(agregado).bodyJson().extractingPath("$.lineas[0].estado").isEqualTo("DISPONIBLE");
	}

	// HU-11 RF-2
	@Test
	void agregarOtraVezSumaEnLaMismaLinea() {
		agregar(ana, martillo, "2");

		MvcTestResult otraVez = agregar(ana, martillo, "3");

		assertThat(otraVez).hasStatus(HttpStatus.OK);
		assertThat(otraVez).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(1);
		assertThat(otraVez).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(5.0);
	}

	// HU-11 RF-3
	@Test
	void cantidadInvalidaNoCambiaElCarrito() {
		for (String cantidad : List.of("0", "-1", "1000", "1.5", "\"tres\"", "null")) {
			assertThat(agregar(ana, martillo, cantidad)).as(cantidad).hasStatus(HttpStatus.BAD_REQUEST);
		}
		MvcTestResult conDecimales = agregar(ana, cable, "2.555");
		assertThat(conDecimales).hasStatus(HttpStatus.BAD_REQUEST);
		MvcTestResult enteroConDecimales = agregar(ana, martillo, "1.5");
		assertThat(enteroConDecimales).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("cantidad");
		assertThat(enteroConDecimales).bodyJson().extractingPath("$.errores[0].mensaje")
				.isEqualTo("tiene que ser un número entero");

		assertThat(agregar(ana, cable, "2.5")).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.5);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(1);
	}

	// HU-11 RF-3
	@Test
	void laSumaNoPasaDe999() {
		agregar(ana, cable, "900");

		MvcTestResult demasiado = agregar(ana, cable, "100");

		assertThat(demasiado).hasStatus(HttpStatus.BAD_REQUEST);
		// La suma se explica distinto que una cantidad fuera de rango: es lo que ya estaba más lo nuevo
		assertThat(demasiado).bodyJson().extractingPath("$.errores[0].mensaje")
				.isEqualTo("sumada a la del carrito pasaría de 999");
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(900.0);
	}

	// HU-11 RF-3: lo pedido se explica antes que la suma
	@Test
	void losDecimalesDeLoPedidoGananALaSuma() {
		agregar(ana, martillo, "900");

		MvcTestResult conDecimales = agregar(ana, martillo, "100.5");

		assertThat(conDecimales).bodyJson().extractingPath("$.errores[0].mensaje")
				.isEqualTo("tiene que ser un número entero");
	}

	// HU-11 RF-4
	@Test
	void agotadoNoSeAgrega() throws Exception {
		Integer pala = producto("Pala", "UNIDAD", "50", "0");

		MvcTestResult agotado = agregar(ana, pala, "1");

		assertThat(agotado).hasStatus(HttpStatus.CONFLICT);
		assertThat(agotado).bodyJson().extractingPath("$.codigo").isEqualTo("PRODUCTO_AGOTADO");
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
	}

	// HU-11 RF-4
	@Test
	void inactivoOcultoEInexistenteResponden404() throws Exception {
		Integer inactivo = producto("Serrucho", "UNIDAD", "40", "5");
		put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", inactivo);
		Integer jardin = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Jardín\"}"), "$.idCategoria");
		MvcTestResult creado = post("/api/gestion/productos", admin, """
				{"nombre":"Regadera","idCategoria":%d,"unidad":"UNIDAD","precio":70}""".formatted(jardin));
		Integer oculto = id(creado, "$.idProducto");
		post("/api/inventario/{id}/entradas", admin, "{\"cantidad\":5}", oculto);
		put("/api/gestion/categorias/{id}/visible", admin, "{\"visible\":false}", jardin);

		for (Integer idProducto : List.of(inactivo, oculto, 999_999)) {
			MvcTestResult resultado = agregar(ana, idProducto, "1");
			assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("PRODUCTO_NO_ENCONTRADO");
		}
	}

	// HU-11 RF-5
	@Test
	void laLinea51NoEntraPeroUnaExistenteSiSuma() throws Exception {
		agregar(ana, martillo, "1");
		for (int i = 2; i <= 50; i++) {
			assertThat(agregar(ana, producto("Llave " + i, "UNIDAD", "10", "5"), "1")).hasStatus(HttpStatus.OK);
		}

		MvcTestResult lleno = agregar(ana, cable, "1");

		assertThat(lleno).hasStatus(HttpStatus.CONFLICT);
		assertThat(lleno).bodyJson().extractingPath("$.codigo").isEqualTo("CARRITO_LLENO");
		assertThat(agregar(ana, martillo, "1")).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(50);
	}

	// HU-11 RF-7
	@Test
	void elPersonalRecibe403YSinSesion401() throws Exception {
		String empleado = cuentaCon("emp.hu11@mail.com", "EMPLEADO");
		String contador = cuentaCon("con.hu11@mail.com", "CONTADOR");

		for (String personal : List.of(admin, empleado, contador)) {
			assertThat(get(CARRITO, personal)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(agregar(personal, martillo, "1")).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(mvc.get().uri(CARRITO).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(LINEAS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idProducto\":%d,\"cantidad\":1}".formatted(martillo)).exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// HU-11 RF-8
	@Test
	void muestraLasLineasConElPrecioDeHoyYElTotal() {
		assertThat(mvc.post().uri("/api/gestion/productos/{id}/imagenes", martillo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).multipart()
				.file(new MockMultipartFile("imagen", "foto.png", "image/png", PNG)).exchange())
				.hasStatus(HttpStatus.OK);
		agregar(ana, martillo, "2");
		agregar(ana, cable, "2.5");
		put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Cable","idCategoria":%d,"unidad":"METRO","precio":12.40}""".formatted(herramientas),
				cable);

		MvcTestResult carrito = get(CARRITO, ana);

		assertThat(carrito).hasStatus(HttpStatus.OK);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[*].nombre").asArray()
				.containsExactly("Martillo", "Cable");
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].imagen").asString().endsWith(".png");
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].unidad").isEqualTo("UNIDAD");
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].subtotal").isEqualTo(171.0);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[1].precio").isEqualTo(12.4);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[1].imagen").isNull();
		assertThat(carrito).bodyJson().extractingPath("$.lineas[1].subtotal").isEqualTo(31.0);
		assertThat(carrito).bodyJson().extractingPath("$.total").isEqualTo(202.0);
	}

	// HU-11 RF-9
	@Test
	void agotadoYNoDisponibleQuedanMarcadosFueraDelTotal() throws Exception {
		Integer pala = producto("Pala", "UNIDAD", "50", "3");
		agregar(ana, martillo, "1");
		agregar(ana, cable, "1");
		agregar(ana, pala, "1");
		ajustarStock(martillo, "0");
		put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", cable);

		MvcTestResult carrito = get(CARRITO, ana);

		assertThat(carrito).bodyJson().extractingPath("$.lineas[*].estado").asArray()
				.containsExactly("AGOTADO", "NO_DISPONIBLE", "DISPONIBLE");
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].subtotal").isNull();
		assertThat(carrito).bodyJson().extractingPath("$.lineas[1].subtotal").isNull();
		assertThat(carrito).bodyJson().extractingPath("$.total").isEqualTo(50.0);
		assertThat(delete(LINEAS + "/{id}", ana, cable)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(2);
	}

	// HU-11 RF-9 (caso límite: la unidad cambia a una sin decimales)
	@Test
	void unidadSinDecimalesMarcaCantidadInvalida() {
		agregar(ana, cable, "2.5");
		agregar(ana, martillo, "1");
		ajustarStock(cable, "0");
		assertThat(put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Cable","idCategoria":%d,"unidad":"ROLLO","precio":12}""".formatted(herramientas),
				cable)).hasStatus(HttpStatus.OK);
		ajustarStock(cable, "5");

		MvcTestResult carrito = get(CARRITO, ana);

		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].estado").isEqualTo("CANTIDAD_INVALIDA");
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].subtotal").isNull();
		assertThat(carrito).bodyJson().extractingPath("$.total").isEqualTo(85.5);
		assertThat(cambiar(ana, cable, "3")).bodyJson().extractingPath("$.lineas[0].estado").isEqualTo("DISPONIBLE");
	}

	// HU-11 RF-10
	@Test
	void cambiarLaCantidadRecalcula() {
		agregar(ana, martillo, "2");

		MvcTestResult cambiado = cambiar(ana, martillo, "3");

		assertThat(cambiado).hasStatus(HttpStatus.OK);
		assertThat(cambiado).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(3.0);
		assertThat(cambiado).bodyJson().extractingPath("$.lineas[0].subtotal").isEqualTo(256.5);
		assertThat(cambiado).bodyJson().extractingPath("$.total").isEqualTo(256.5);
		assertThat(cambiar(ana, martillo, "1.5")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(cambiar(ana, martillo, "1000")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(cambiar(ana, cable, "1")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(3.0);
	}

	// HU-11 RF-11
	@Test
	void quitarYVaciar() {
		agregar(ana, martillo, "1");
		agregar(ana, cable, "1");

		MvcTestResult sinMartillo = delete(LINEAS + "/{id}", ana, martillo);
		assertThat(sinMartillo).hasStatus(HttpStatus.OK);
		assertThat(sinMartillo).bodyJson().extractingPath("$.lineas[*].nombre").asArray().containsExactly("Cable");
		assertThat(delete(LINEAS + "/{id}", ana, martillo)).hasStatus(HttpStatus.OK);

		MvcTestResult vacio = delete(CARRITO, ana);
		assertThat(vacio).hasStatus(HttpStatus.OK);
		assertThat(vacio).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
	}

	// HU-11 RF-12
	@Test
	void carritoVacio() {
		MvcTestResult vacio = get(CARRITO, ana);

		assertThat(vacio).hasStatus(HttpStatus.OK);
		assertThat(vacio).bodyJson().extractingPath("$.lineas").asArray().isEmpty();
		assertThat(vacio).bodyJson().extractingPath("$.total").isEqualTo(0.0);
		assertThat(vacio).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
	}

	// HU-11 RF-13
	@Test
	void elCarritoSeGuardaEntreSesiones() throws Exception {
		agregar(ana, martillo, "2");

		String otraSesion = token("ana.hu11@mail.com", "secreta12");

		assertThat(get(CARRITO, otraSesion)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
	}

	// HU-11 RF-13
	@Test
	void elClienteNoTocaElCarritoDeOtro() {
		agregar(ana, martillo, "2");

		assertThat(get(CARRITO, beto)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
		assertThat(cambiar(beto, martillo, "5")).hasStatus(HttpStatus.NOT_FOUND);
		delete(LINEAS + "/{id}", beto, martillo);
		delete(CARRITO, beto);

		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
	}

	// HU-11 RF-14
	@Test
	void ningunaRespuestaNiErrorTraeElStock() throws Exception {
		Integer pala = producto("Pala", "UNIDAD", "50", "0");
		List<MvcTestResult> respuestas = List.of(agregar(ana, martillo, "1"), get(CARRITO, ana),
				cambiar(ana, martillo, "999"), agregar(ana, pala, "1"), agregar(ana, martillo, "500"));

		assertThat(respuestas.get(1)).hasStatus(HttpStatus.OK);
		assertThat(respuestas.get(3)).hasStatus(HttpStatus.CONFLICT);
		assertThat(respuestas.get(4)).hasStatus(HttpStatus.BAD_REQUEST);
		for (MvcTestResult respuesta : respuestas) {
			String cuerpo = respuesta.getResponse().getContentAsString();
			assertThat(cuerpo).doesNotContain("stock").doesNotContain("Stock");
		}
	}

	// HU-11 RF-2 (caso límite: dos pestañas a la vez)
	@Test
	void dosAgregadosALaVezSumanLosDos() throws Exception {
		List<MvcTestResult> resultados = aLaVez(() -> agregar(ana, cable, "1"), () -> agregar(ana, cable, "2"),
				() -> agregar(ana, cable, "3"));

		assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).hasStatus(HttpStatus.OK));
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(6.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(1);
	}

	// Caso límite: datos basura en la dirección
	@Test
	void idQueNoEsNumeroResponde400() {
		assertThat(put(LINEAS + "/abc", ana, "{\"cantidad\":1}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(delete(LINEAS + "/abc", ana)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(LINEAS, ana, "{\"idProducto\":\"abc\",\"cantidad\":1}")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// Caso límite: la notación científica es un número de JSON y pasa por las mismas reglas
	@Test
	void notacionCientificaSeLeeComoSuValor() {
		assertThat(agregar(ana, cable, "1e-1")).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(0.1);
		assertThat(agregar(ana, martillo, "1e3")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@SafeVarargs
	private static List<MvcTestResult> aLaVez(Callable<MvcTestResult>... pedidos) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(pedidos.length);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<MvcTestResult>> futuros = Arrays.stream(pedidos).map(pedido -> hilos.submit(() -> {
				largada.await();
				return pedido.call();
			})).toList();
			largada.countDown();
			List<MvcTestResult> resultados = new ArrayList<>();
			for (Future<MvcTestResult> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			hilos.shutdown();
		}
	}
}
