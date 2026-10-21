package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.PedidoIntegracionTest.RelojDePrueba;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.ConciliacionDePagos;
import bo.edu.uagrm.tienda.service.pago.EstadoDelCobro;
import bo.edu.uagrm.tienda.service.pago.SimuladorDePago;

// HU-14: la lista de pedidos del Cliente, de punta a punta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MisPedidosIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String PEDIDOS = "/api/pedidos";
	private static final List<String> CORREOS = List.of("ana.hu14@mail.com", "beto.hu14@mail.com",
			"emp.hu14@mail.com");

	@TestConfiguration
	static class ConReloj {

		@Bean
		@Primary
		RelojDePrueba relojDePrueba() {
			return new RelojDePrueba();
		}
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private RelojDePrueba reloj;

	@Autowired
	private SimuladorDePago simulador;

	@Autowired
	private ConciliacionDePagos conciliacion;

	@Autowired
	private CobroRepository cobroRepository;

	@Autowired
	private LineaCarritoRepository lineaRepository;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private PedidoRepository pedidoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	private String admin;
	private String ana;
	private String beto;
	private Integer martillo;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		ana = cuentaCon("ana.hu14@mail.com", null);
		beto = cuentaCon("beto.hu14@mail.com", null);
		Integer herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"),
				"$.idCategoria");
		MvcTestResult creado = post("/api/gestion/productos", admin, """
				{"nombre":"Martillo","idCategoria":%d,"unidad":"UNIDAD","precio":85.50}""".formatted(herramientas));
		martillo = id(creado, "$.idProducto");
		assertThat(post("/api/inventario/{id}/entradas", admin, "{\"cantidad\":500}", martillo))
				.hasStatus(HttpStatus.CREATED);
	}

	@AfterEach
	void limpiar() {
		reloj.reiniciar();
		lineaRepository.deleteAll();
		movimientoRepository.deleteAll();
		cobroRepository.deleteAll();
		pedidoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		CORREOS.forEach(correo -> usuarioRepository.findByCorreo(correo).ifPresent(usuarioRepository::delete));
	}

	// --- Ayudantes ---

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

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
			assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
					.content("{\"rol\":\"%s\"}".formatted(rol)).exchange()).hasStatus(HttpStatus.OK);
		}
		return token;
	}

	private MvcTestResult post(String uri, String token, String cuerpo, Object... variables) {
		return mvc.post().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult get(String uri, String token, Object... variables) {
		return mvc.get().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	// Un pedido de 2 martillos (Bs 171); un minuto después del anterior, para que el orden sea claro
	private Integer pedido(String token) throws Exception {
		reloj.avanzar(Duration.ofMinutes(1));
		assertThat(post("/api/carrito/lineas", token, "{\"idProducto\":%d,\"cantidad\":2}".formatted(martillo)))
				.hasStatus(HttpStatus.OK);
		MvcTestResult confirmado = post(PEDIDOS, token, """
				{"nombreRetira":"Ana Paz","telefonoRetira":"70000000","nota":null,"totalEsperado":171}""");
		assertThat(confirmado).hasStatus(HttpStatus.CREATED);
		return id(confirmado, "$.idPedido");
	}

	private Integer cancelado(String token) throws Exception {
		Integer idPedido = pedido(token);
		assertThat(post(PEDIDOS + "/{id}/cancelacion", token, "", idPedido)).hasStatus(HttpStatus.OK);
		lineaRepository.deleteAll();
		return idPedido;
	}

	private MvcTestResult lista(String token) {
		return get(PEDIDOS, token);
	}

	// HU-14 RF-1, RF-3: todos, el más nuevo primero, con su resumen
	@Test
	void listaTodosElMasNuevoPrimeroConSuResumen() throws Exception {
		Integer primero = cancelado(ana);
		Integer segundo = cancelado(ana);
		// Un pago tardío sin stock: «Pago a devolver»
		Integer tercero = pedido(ana);
		assertThat(post(PEDIDOS + "/{id}/cobro", ana, "", tercero)).hasStatus(HttpStatus.OK);
		var cobro = cobroRepository.findAll().getFirst();
		assertThat(post(PEDIDOS + "/{id}/cancelacion", ana, "", tercero)).hasStatus(HttpStatus.OK);
		lineaRepository.deleteAll();
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":0,\"motivo\":\"CONTEO\"}", martillo))
				.hasStatus(HttpStatus.CREATED);
		simulador.forzar(cobro.getReferencia(), EstadoDelCobro.PAGADO);
		conciliacion.conciliar(cobro.getIdCobro());

		MvcTestResult resultado = lista(ana);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[*].idPedido").asArray()
				.containsExactly(tercero, segundo, primero);
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[*].estado").asArray()
				.containsExactly("PAGO_A_DEVOLVER", "CANCELADO", "CANCELADO");
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].cantidadProductos").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].total").isEqualTo(171.0);
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].confirmado").isNotNull();
		assertThat(resultado).bodyJson().extractingPath("$.pendiente").isNull();
	}

	// HU-14 RF-2: el que espera el pago va aparte, con su tiempo
	@Test
	void elPendienteVaAparteConSuTiempo() throws Exception {
		cancelado(ana);
		Integer pendiente = pedido(ana);
		reloj.avanzar(Duration.ofMinutes(10));

		MvcTestResult resultado = lista(ana);

		assertThat(resultado).bodyJson().extractingPath("$.pendiente.idPedido").isEqualTo(pendiente);
		assertThat(resultado).bodyJson().extractingPath("$.pendiente.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(resultado).bodyJson().extractingPath("$.pendiente.segundosParaPagar").isEqualTo(1200);
		// También en su lugar de la lista
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].idPedido").isEqualTo(pendiente);
	}

	// HU-14 RF-4: abrir la lista vence el que pasó la hora
	@Test
	void abrirLaListaVenceElPendienteQuePasoLaHora() throws Exception {
		Integer idPedido = pedido(ana);
		reloj.avanzar(Duration.ofMinutes(31));

		MvcTestResult resultado = lista(ana);

		assertThat(resultado).bodyJson().extractingPath("$.pendiente").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].idPedido").isEqualTo(idPedido);
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].estado").isEqualTo("VENCIDO");
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido[0].segundosParaPagar").isEqualTo(0);
	}

	// HU-14 RF-2, RF-4: con un cobro sin consultar pasada la hora, sigue arriba con 0 (HU-13 RF-7)
	@Test
	void unPendienteConCobroSigueArribaHastaLaConsulta() throws Exception {
		Integer idPedido = pedido(ana);
		assertThat(post(PEDIDOS + "/{id}/cobro", ana, "", idPedido)).hasStatus(HttpStatus.OK);
		reloj.avanzar(Duration.ofMinutes(31));

		MvcTestResult resultado = lista(ana);

		assertThat(resultado).bodyJson().extractingPath("$.pendiente.idPedido").isEqualTo(idPedido);
		assertThat(resultado).bodyJson().extractingPath("$.pendiente.segundosParaPagar").isEqualTo(0);
	}

	// HU-14 RF-5
	@Test
	void sinPedidosLaListaVieneVacia() {
		MvcTestResult resultado = lista(ana);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.pagina.contenido").asArray().isEmpty();
		assertThat(resultado).bodyJson().extractingPath("$.pagina.totalElementos").isEqualTo(0);
		assertThat(resultado).bodyJson().extractingPath("$.pendiente").isNull();
	}

	// HU-14 RF-7: solo el Cliente, y solo los suyos
	@Test
	void elPersonalRecibe403YSoloSeVenLosPropios() throws Exception {
		Integer deAna = cancelado(ana);
		Integer deBeto = cancelado(beto);
		String empleado = cuentaCon("emp.hu14@mail.com", "EMPLEADO");

		assertThat(lista(admin)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(lista(empleado)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(PEDIDOS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(lista(ana)).bodyJson().extractingPath("$.pagina.contenido[*].idPedido").asArray()
				.containsExactly(deAna);
		assertThat(lista(beto)).bodyJson().extractingPath("$.pagina.contenido[*].idPedido").asArray()
				.containsExactly(deBeto);
	}

	// HU-14 RF-1, RF-8: de a 10; fuera de rango, la última; negativa o basura, la primera
	@Test
	void paginasDeA10YUnaPaginaFueraDeRangoMuestraLaUltima() throws Exception {
		for (int i = 0; i < 12; i++) {
			cancelado(ana);
		}

		MvcTestResult primera = lista(ana);
		MvcTestResult segunda = get(PEDIDOS + "?pagina=1", ana);
		MvcTestResult lejos = get(PEDIDOS + "?pagina=5", ana);

		assertThat(primera).bodyJson().extractingPath("$.pagina.contenido").asArray().hasSize(10);
		assertThat(primera).bodyJson().extractingPath("$.pagina.totalPaginas").isEqualTo(2);
		assertThat(segunda).bodyJson().extractingPath("$.pagina.contenido").asArray().hasSize(2);
		assertThat(lejos).bodyJson().extractingPath("$.pagina.pagina").isEqualTo(1);
		assertThat(lejos).bodyJson().extractingPath("$.pagina.contenido").asArray().hasSize(2);
		for (String basura : List.of("-3", "abc", "1.5", "99999999999999999999")) {
			MvcTestResult resultado = get(PEDIDOS + "?pagina={p}", ana, basura);
			assertThat(resultado).hasStatus(HttpStatus.OK);
			assertThat(resultado).bodyJson().extractingPath("$.pagina.pagina").isEqualTo(0);
		}
	}
}
