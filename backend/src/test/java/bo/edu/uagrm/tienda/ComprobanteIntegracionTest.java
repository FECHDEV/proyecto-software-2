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
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import bo.edu.uagrm.tienda.entity.Usuario;
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

// HU-14 RF-9 a RF-13 (ampliación del 17/11/2026): el comprobante del Cliente, de punta a punta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComprobanteIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String PEDIDOS = "/api/pedidos";
	private static final String COMPROBANTE = PEDIDOS + "/{id}/comprobante";
	private static final List<String> CORREOS = List.of("ana.comp@mail.com", "beto.comp@mail.com",
			"eva.comp@mail.com");

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
	private String eva;
	private Integer idAna;
	private Integer martillo;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		ana = cuentaCon("ana.comp@mail.com", "Ana", "Paz", null);
		beto = cuentaCon("beto.comp@mail.com", "Beto", "Rojas", null);
		eva = cuentaCon("eva.comp@mail.com", "Eva", "Suárez", "EMPLEADO");
		idAna = usuarioRepository.findByCorreo("ana.comp@mail.com").orElseThrow().getIdUsuario().intValue();
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

	private String cuentaCon(String correo, String nombre, String apellido, String rol) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"%s\",\"apellido\":\"%s\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(nombre, apellido, correo))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		// Para confirmar un pedido hace falta el correo verificado (HU-20 RF-6)
		Usuario usuario = usuarioRepository.findByCorreo(correo).orElseThrow();
		usuario.marcarCorreoVerificado();
		usuarioRepository.save(usuario);
		String token = JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
		if (rol == null) {
			return token;
		}
		Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
		assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"%s\"}".formatted(rol)).exchange()).hasStatus(HttpStatus.OK);
		// El rol nuevo va en un token nuevo
		return token(correo, "secreta12");
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

	private MvcTestResult comprobante(String token, Object idPedido) {
		return get(COMPROBANTE, token, idPedido);
	}

	// Un pedido en línea de 2 martillos (Bs 171), pendiente de pago
	private Integer pedido(String token) throws Exception {
		reloj.avanzar(Duration.ofMinutes(1));
		assertThat(post("/api/carrito/lineas", token, "{\"idProducto\":%d,\"cantidad\":2}".formatted(martillo)))
				.hasStatus(HttpStatus.OK);
		MvcTestResult confirmado = post(PEDIDOS, token, """
				{"nombreRetira":"Ana Paz","telefonoRetira":"70000000","nota":null,"totalEsperado":171}""");
		assertThat(confirmado).hasStatus(HttpStatus.CREATED);
		return id(confirmado, "$.idPedido");
	}

	private Cobro cobroDe(Integer idPedido) {
		return cobroRepository.findFirstByPedido_IdPedidoAndEstado(idPedido.longValue(), EstadoCobro.PENDIENTE)
				.orElseThrow();
	}

	// Con el QR generado, el sistema de pago dice que se pagó y la conciliación lo confirma
	private void pagar(Integer idPedido, String token) {
		assertThat(post(PEDIDOS + "/{id}/cobro", token, "", idPedido)).hasStatus(HttpStatus.OK);
		Cobro cobro = cobroDe(idPedido);
		simulador.forzar(cobro.getReferencia(), EstadoDelCobro.PAGADO);
		conciliacion.conciliar(cobro.getIdCobro());
	}

	private Integer pagado(String token) throws Exception {
		Integer idPedido = pedido(token);
		pagar(idPedido, token);
		return idPedido;
	}

	// Una venta del mostrador de 2 martillos a nombre de Ana, que vende Eva
	private Integer venta(String medio, String recibido) throws Exception {
		MvcTestResult venta = post("/api/ventas", eva, """
				{"lineas":[{"idProducto":%d,"cantidad":2}],"idCliente":%d,"medio":"%s","recibido":%s,\
				"totalEsperado":171}""".formatted(martillo, idAna, medio, recibido));
		assertThat(venta).hasStatus(HttpStatus.CREATED);
		return id(venta, "$.idPedido");
	}

	// --- RF-9, RF-10: el ticket ---

	// HU-14 RF-10: en línea, su nombre, las líneas y el total, sin medio (no se sabe con qué se pagó), quien vendió ni efectivo
	@Test
	void enLineaPagadoDaElTicketSinMedioNiAtendio() throws Exception {
		Integer idPedido = pagado(ana);

		MvcTestResult resultado = comprobante(ana, idPedido);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.idPedido").isEqualTo(idPedido);
		assertThat(resultado).bodyJson().extractingPath("$.fecha").isNotNull();
		assertThat(resultado).bodyJson().extractingPath("$.cliente").isEqualTo("Ana Paz");
		assertThat(resultado).bodyJson().extractingPath("$.vendidoPor").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.lineas[0].nombre").isEqualTo("Martillo");
		assertThat(resultado).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
		assertThat(resultado).bodyJson().extractingPath("$.lineas[0].subtotal").isEqualTo(171.0);
		assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(171.0);
		assertThat(resultado).bodyJson().extractingPath("$.medio").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.recibido").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.cambio").isNull();
	}

	// HU-14 RF-9, RF-10: entregado también es una compra hecha
	@Test
	void enLineaEntregadoTambienTieneComprobante() throws Exception {
		Integer idPedido = pagado(ana);
		assertThat(post("/api/despacho/pedidos/{id}/entrega", eva, "", idPedido)).hasStatus(HttpStatus.OK);

		MvcTestResult resultado = comprobante(ana, idPedido);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.vendidoPor").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.medio").isNull();
	}

	// HU-14 RF-10: del mostrador en efectivo, como el papel: lo recibido, el cambio y quién vendió
	@Test
	void ventaEnEfectivoDaRecibidoCambioYQuienVendio() throws Exception {
		Integer idVenta = venta("EFECTIVO", "200");

		MvcTestResult resultado = comprobante(ana, idVenta);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.cliente").isEqualTo("Ana Paz");
		assertThat(resultado).bodyJson().extractingPath("$.vendidoPor").isEqualTo("Eva Suárez");
		assertThat(resultado).bodyJson().extractingPath("$.medio").isEqualTo("EFECTIVO");
		assertThat(resultado).bodyJson().extractingPath("$.recibido").isEqualTo(200.0);
		assertThat(resultado).bodyJson().extractingPath("$.cambio").isEqualTo(29.0);
		assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(171.0);
	}

	// HU-14 RF-10: del mostrador con QR, sin recibido ni cambio
	@Test
	void ventaConQrPagadaDaQrSinRecibidoNiCambio() throws Exception {
		Integer idVenta = venta("QR", "null");
		simulador.forzar(cobroDe(idVenta).getReferencia(), EstadoDelCobro.PAGADO);
		assertThat(post("/api/ventas/{id}/consulta", eva, "", idVenta)).bodyJson().extractingPath("$.estado")
				.isEqualTo("ENTREGADO");

		MvcTestResult resultado = comprobante(ana, idVenta);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.vendidoPor").isEqualTo("Eva Suárez");
		assertThat(resultado).bodyJson().extractingPath("$.medio").isEqualTo("QR");
		assertThat(resultado).bodyJson().extractingPath("$.recibido").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.cambio").isNull();
	}

	// --- RF-12: sin comprobante ---

	// HU-14 RF-12: lo que no fue una compra da 404
	@Test
	void pendienteCanceladoVencidoYPagoADevolverDan404() throws Exception {
		Integer pendiente = pedido(ana);
		assertThat(comprobante(ana, pendiente)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(post(PEDIDOS + "/{id}/cancelacion", ana, "", pendiente)).hasStatus(HttpStatus.OK);
		lineaRepository.deleteAll();
		assertThat(comprobante(ana, pendiente)).hasStatus(HttpStatus.NOT_FOUND);

		Integer vencido = pedido(ana);
		reloj.avanzar(Duration.ofMinutes(31));
		// Abrir la lista lo vence (RF-4)
		assertThat(get(PEDIDOS, ana)).bodyJson().extractingPath("$.pagina.contenido[0].estado").isEqualTo("VENCIDO");
		lineaRepository.deleteAll();
		assertThat(comprobante(ana, vencido)).hasStatus(HttpStatus.NOT_FOUND);

		// Pago tardío sin stock: «Pago a devolver»
		Integer aDevolver = pedido(ana);
		assertThat(post(PEDIDOS + "/{id}/cobro", ana, "", aDevolver)).hasStatus(HttpStatus.OK);
		Cobro cobro = cobroDe(aDevolver);
		assertThat(post(PEDIDOS + "/{id}/cancelacion", ana, "", aDevolver)).hasStatus(HttpStatus.OK);
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":0,\"motivo\":\"CONTEO\"}", martillo))
				.hasStatus(HttpStatus.CREATED);
		simulador.forzar(cobro.getReferencia(), EstadoDelCobro.PAGADO);
		conciliacion.conciliar(cobro.getIdCobro());
		assertThat(get(PEDIDOS + "/{id}", ana, aDevolver)).bodyJson().extractingPath("$.estado")
				.isEqualTo("PAGO_A_DEVOLVER");
		assertThat(comprobante(ana, aDevolver)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// HU-14 RF-12: la venta anulada no fue una compra
	@Test
	void ventaAnuladaDa404() throws Exception {
		Integer idVenta = venta("EFECTIVO", "200");
		assertThat(post("/api/ventas/{id}/anulacion", admin, "{\"motivo\":\"Error de cobro\"}", idVenta))
				.hasStatus(HttpStatus.OK);

		assertThat(comprobante(ana, idVenta)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// HU-14 RF-12: el de otro cliente, igual que uno que no existe
	@Test
	void elDeOtroClienteDa404() throws Exception {
		Integer deAna = pagado(ana);

		assertThat(comprobante(beto, deAna)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(comprobante(ana, 999_999)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// HU-14 RF-12 (casos límite): un número de pedido que no es un número
	@Test
	void idQueNoEsUnNumeroDa404() {
		assertThat(comprobante(ana, "abc")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(comprobante(ana, "-1")).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- RF-13: solo el Cliente ---

	@Test
	void elPersonalRecibe403YSinSesion401() throws Exception {
		Integer idVenta = venta("EFECTIVO", "200");

		assertThat(comprobante(eva, idVenta)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(comprobante(admin, idVenta)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(COMPROBANTE, idVenta).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}
}
