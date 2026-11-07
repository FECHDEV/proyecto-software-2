package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.PedidoIntegracionTest.RelojDePrueba;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
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
import bo.edu.uagrm.tienda.service.VencimientoPedidos;
import bo.edu.uagrm.tienda.service.VentaTiendaService;
import bo.edu.uagrm.tienda.service.pago.EstadoDelCobro;
import bo.edu.uagrm.tienda.service.pago.SimuladorDePago;
import bo.edu.uagrm.tienda.service.pago.SistemaDePagoNoDisponibleException;

// HU-23: la venta del mostrador, por su API (/api/ventas), con el simulador de pago y el reloj de prueba
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VentaTiendaIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String VENTAS = "/api/ventas";
	private static final ZoneId BOLIVIA = ZoneId.of("America/La_Paz");
	private static final List<String> CORREOS = List.of("ana.hu23@mail.com", "eva.hu23@mail.com",
			"con.hu23@mail.com", "des.hu23@mail.com");

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
	private VencimientoPedidos vencimiento;

	@Autowired
	private ConciliacionDePagos conciliacion;

	@MockitoSpyBean
	private SimuladorDePago simulador;

	@Autowired
	private VentaTiendaService ventaTiendaService;

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
	private String empleado;
	private String contador;
	private String ana;
	private Integer idAna;
	private Integer herramientas;
	private Integer martillo;
	private Integer cable;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		empleado = cuentaCon("eva.hu23@mail.com", "EMPLEADO");
		contador = cuentaCon("con.hu23@mail.com", "CONTADOR");
		ana = cuentaCon("ana.hu23@mail.com", null);
		idAna = usuarioRepository.findByCorreo("ana.hu23@mail.com").orElseThrow().getIdUsuario().intValue();
		herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		martillo = producto("Martillo", "UNIDAD", "85", "10");
		cable = producto("Cable", "METRO", "4.50", "100");
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
		if (rol == null) {
			return token;
		}
		Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
		assertThat(put("/api/usuarios/{id}/rol", admin, "{\"rol\":\"%s\"}".formatted(rol), idUsuario))
				.hasStatus(HttpStatus.OK);
		// El rol nuevo va en un token nuevo
		return token(correo, "secreta12");
	}

	private Integer producto(String nombre, String unidad, String precio, String stock) throws Exception {
		MvcTestResult creado = post("/api/gestion/productos", admin, """
				{"nombre":"%s","idCategoria":%d,"unidad":"%s","precio":%s}"""
				.formatted(nombre, herramientas, unidad, precio));
		assertThat(creado).hasStatus(HttpStatus.CREATED);
		Integer id = id(creado, "$.idProducto");
		assertThat(post("/api/inventario/{id}/entradas", admin, "{\"cantidad\":%s}".formatted(stock), id))
				.hasStatus(HttpStatus.CREATED);
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

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	// Un martillo y 2,5 m de cable: Bs 96,25
	private static final String LINEAS = "[{\"idProducto\":%d,\"cantidad\":1},{\"idProducto\":%d,\"cantidad\":2.5}]";

	private MvcTestResult vender(String token, String lineas, Integer idCliente, String medio, String recibido,
			String totalEsperado) {
		return post(VENTAS, token, """
				{"lineas":%s,"idCliente":%s,"medio":"%s","recibido":%s,"totalEsperado":%s}"""
				.formatted(lineas, idCliente, medio, recibido, totalEsperado));
	}

	private Integer enEfectivo(String token) throws Exception {
		MvcTestResult venta = vender(token, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "100", "96.25");
		assertThat(venta).hasStatus(HttpStatus.CREATED);
		return id(venta, "$.idPedido");
	}

	private Integer conQr(String token) throws Exception {
		MvcTestResult venta = vender(token, LINEAS.formatted(martillo, cable), null, "QR", "null", "96.25");
		assertThat(venta).hasStatus(HttpStatus.CREATED);
		return id(venta, "$.idPedido");
	}

	private void elBancoPaga(Integer idVenta) {
		String referencia = cobroRepository.findFirstByPedido_IdPedidoAndEstado(idVenta.longValue(),
				EstadoCobro.PENDIENTE).orElseThrow().getReferencia();
		simulador.forzar(referencia, EstadoDelCobro.PAGADO);
	}

	private Long idCobro(Integer idVenta) {
		return cobroRepository.findFirstByPedido_IdPedidoAndEstado(idVenta.longValue(), EstadoCobro.PENDIENTE)
				.orElseThrow().getIdCobro();
	}

	private Double stock(Integer idProducto) throws Exception {
		return JsonPath.read(get("/api/inventario/{id}", admin, idProducto).getResponse().getContentAsString(),
				"$.stock");
	}

	private EstadoPedido estado(Integer idVenta) {
		return pedidoRepository.findById(idVenta.longValue()).orElseThrow().getEstado();
	}

	// --- RF-1 a RF-4: armar la venta ---

	// HU-23 RF-1, RF-3: la búsqueda trae solo productos activos, con su precio, su unidad y el disponible
	@Test
	void buscaSoloProductosActivosConSuDisponible() throws Exception {
		Integer viejo = producto("Martillo viejo", "UNIDAD", "50", "3");
		assertThat(put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", viejo))
				.hasStatus(HttpStatus.OK);

		MvcTestResult resultado = get(VENTAS + "/productos?texto=martillo", empleado);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$[*].idProducto").asArray().containsExactly(martillo);
		assertThat(resultado).bodyJson().extractingPath("$[0].precio").isEqualTo(85.0);
		assertThat(resultado).bodyJson().extractingPath("$[0].unidad").isEqualTo("UNIDAD");
		assertThat(resultado).bodyJson().extractingPath("$[0].disponible").isEqualTo(10.0);
	}

	// HU-23 RF-4: el comprador se busca por correo exacto, sin distinguir mayúsculas, y solo entre Clientes activos
	@Test
	void encuentraAlClientePorCorreoExactoYNoAlPersonalNiAlDesactivado() throws Exception {
		MvcTestResult encontrado = get(VENTAS + "/clientes?correo= ANA.hu23@Mail.com ", empleado);
		assertThat(encontrado).hasStatus(HttpStatus.OK);
		assertThat(encontrado).bodyJson().extractingPath("$.idUsuario").isEqualTo(idAna);
		assertThat(encontrado).bodyJson().extractingPath("$.nombre").isEqualTo("Ana Paz");

		assertThat(get(VENTAS + "/clientes?correo=ana.hu23", empleado)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(VENTAS + "/clientes?correo=con.hu23@mail.com", empleado)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(put("/api/usuarios/{id}/estado", admin, "{\"estado\":\"DESACTIVADA\"}", idAna))
				.hasStatus(HttpStatus.OK);
		assertThat(get(VENTAS + "/clientes?correo=ana.hu23@mail.com", empleado)).hasStatus(HttpStatus.NOT_FOUND);
		// La API también rechaza vender a esa cuenta
		assertThat(vender(empleado, LINEAS.formatted(martillo, cable), idAna, "EFECTIVO", "100", "96.25"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// --- RF-5, RF-6: efectivo ---

	// HU-23 RF-5, RF-6: resta el stock con la reserva del pedido a nombre de quien vende y queda entregada con el cambio
	@Test
	void enEfectivoRestaElStockYQuedaEntregadaConElCambio() throws Exception {
		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "100", "96.25");

		assertThat(venta).hasStatus(HttpStatus.CREATED);
		assertThat(venta).bodyJson().extractingPath("$.estado").isEqualTo("ENTREGADO");
		assertThat(venta).bodyJson().extractingPath("$.medio").isEqualTo("EFECTIVO");
		assertThat(venta).bodyJson().extractingPath("$.total").isEqualTo(96.25);
		assertThat(venta).bodyJson().extractingPath("$.recibido").isEqualTo(100.0);
		assertThat(venta).bodyJson().extractingPath("$.cambio").isEqualTo(3.75);
		assertThat(venta).bodyJson().extractingPath("$.comprador").isNull();
		assertThat(venta).bodyJson().extractingPath("$.vendidoPor").isEqualTo("Ana Paz");
		assertThat(venta).bodyJson().extractingPath("$.pagado").isNotNull();
		assertThat(stock(martillo)).isEqualTo(9.0);
		assertThat(stock(cable)).isEqualTo(97.5);
		Usuario eva = usuarioRepository.findByCorreo("eva.hu23@mail.com").orElseThrow();
		assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.RESERVA)
				.hasSize(2).allMatch(m -> m.getUsuario().getIdUsuario().equals(eva.getIdUsuario()));
	}

	// HU-23 RF-5 (casos límite): lo recibido igual al total se acepta, con cambio cero
	@Test
	void aceptaElRecibidoIgualAlTotalConCambioCero() throws Exception {
		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "96.25", "96.25");

		assertThat(venta).hasStatus(HttpStatus.CREATED);
		assertThat(venta).bodyJson().extractingPath("$.cambio").isEqualTo(0.0);
	}

	// --- RF-7 a RF-11: QR ---

	// HU-23 RF-7: con QR reserva el stock, espera 10 minutos y trae el QR
	@Test
	void conQrReservaYGeneraElCobro() throws Exception {
		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "QR", "null", "96.25");

		assertThat(venta).hasStatus(HttpStatus.CREATED);
		assertThat(venta).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(venta).bodyJson().extractingPath("$.segundosParaPagar").isEqualTo(600);
		assertThat(venta).bodyJson().extractingPath("$.cobro.codigoQr").isNotNull();
		assertThat(venta).bodyJson().extractingPath("$.cobro.simulador").isEqualTo(true);
		assertThat(venta).bodyJson().extractingPath("$.cambio").isNull();
		assertThat(stock(martillo)).isEqualTo(9.0);
	}

	// HU-23 RF-8: la consulta se entera del pago y la venta queda entregada
	@Test
	void conQrPagadaATiempoQuedaEntregada() throws Exception {
		Integer venta = conQr(empleado);
		assertThat(post(VENTAS + "/{id}/consulta", empleado, "", venta)).bodyJson().extractingPath("$.estado")
				.isEqualTo("PENDIENTE_PAGO");
		elBancoPaga(venta);

		MvcTestResult consulta = post(VENTAS + "/{id}/consulta", empleado, "", venta);

		assertThat(consulta).hasStatus(HttpStatus.OK);
		assertThat(consulta).bodyJson().extractingPath("$.estado").isEqualTo("ENTREGADO");
		assertThat(consulta).bodyJson().extractingPath("$.cobro").isNull();
		assertThat(stock(martillo)).isEqualTo(9.0);
	}

	// HU-23 RF-9: cancelar devuelve el stock; una venta ya cancelada no se cancela otra vez
	@Test
	void cancelarDevuelveElStock() throws Exception {
		Integer venta = conQr(empleado);

		MvcTestResult cancelada = post(VENTAS + "/{id}/cancelacion", empleado, "", venta);

		assertThat(cancelada).hasStatus(HttpStatus.OK);
		assertThat(cancelada).bodyJson().extractingPath("$.estado").isEqualTo("CANCELADO");
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(stock(cable)).isEqualTo(100.0);
		assertThat(post(VENTAS + "/{id}/cancelacion", empleado, "", venta)).hasStatus(HttpStatus.CONFLICT);
	}

	// HU-23 RF-10: a los 10 minutos vence, pero antes se consulta el cobro: si se pagó, queda entregada
	@Test
	void venceALosDiezMinutosYConsultaAntes() throws Exception {
		Integer sinPagar = conQr(empleado);
		Integer pagada = conQr(empleado);
		elBancoPaga(pagada);
		reloj.avanzar(Duration.ofMinutes(9));
		vencimiento.vencer();
		assertThat(estado(sinPagar)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);

		reloj.avanzar(Duration.ofMinutes(2));
		vencimiento.vencer();

		assertThat(estado(sinPagar)).isEqualTo(EstadoPedido.VENCIDO);
		assertThat(estado(pagada)).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(stock(martillo)).isEqualTo(9.0);
	}

	// HU-23 RF-11: el pago tardío vuelve a reservar si alcanza (queda para entregar) y si no, queda para devolver
	@Test
	void elPagoTardioReservaDeNuevoOQuedaParaDevolver() throws Exception {
		Integer revive = conQr(empleado);
		MvcTestResult otra = vender(empleado, "[{\"idProducto\":%d,\"cantidad\":3}]".formatted(cable), null, "QR",
				"null", "13.5");
		Integer aDevolver = id(otra, "$.idPedido");
		assertThat(post(VENTAS + "/{id}/cancelacion", empleado, "", revive)).hasStatus(HttpStatus.OK);
		assertThat(post(VENTAS + "/{id}/cancelacion", empleado, "", aDevolver)).hasStatus(HttpStatus.OK);
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":0,\"motivo\":\"CONTEO\"}", cable))
				.hasStatus(HttpStatus.CREATED);
		elBancoPaga(aDevolver);

		conciliacion.conciliar(idCobro(aDevolver));

		assertThat(estado(aDevolver)).isEqualTo(EstadoPedido.PAGO_A_DEVOLVER);
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":100,\"motivo\":\"CONTEO\"}", cable))
				.hasStatus(HttpStatus.CREATED);
		elBancoPaga(revive);
		conciliacion.conciliar(idCobro(revive));
		assertThat(estado(revive)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(stock(martillo)).isEqualTo(9.0);
		assertThat(stock(cable)).isEqualTo(97.5);
	}

	// HU-23 (casos límite): si el sistema de pago no genera el QR, la venta se cancela y el stock vuelve
	@Test
	void siElSistemaDePagoNoGeneraElQrLaVentaSeCancela() throws Exception {
		doThrow(new SistemaDePagoNoDisponibleException("sin conexión", null)).when(simulador)
				.generarCobro(any(), any(), any());

		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "QR", "null", "96.25");

		assertThat(venta).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(venta).bodyJson().extractingPath("$.codigo").isEqualTo("COBRO_NO_DISPONIBLE");
		assertThat(pedidoRepository.findAll()).singleElement()
				.satisfies(pedido -> assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO));
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(stock(cable)).isEqualTo(100.0);
	}

	// HU-23 (casos límite, /code-review): si la cancelan mientras se generaba el QR y después falla, sigue el 503 y el
	// stock vuelve una sola vez
	@Test
	void siLaCancelanMientrasSeGeneraYFallaSigueEl503() throws Exception {
		doAnswer(llamada -> {
			ventaTiendaService.cancelar(llamada.getArgument(0));
			throw new SistemaDePagoNoDisponibleException("sin conexión", null);
		}).when(simulador).generarCobro(any(), any(), any());

		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "QR", "null", "96.25");

		assertThat(venta).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(venta).bodyJson().extractingPath("$.codigo").isEqualTo("COBRO_NO_DISPONIBLE");
		assertThat(pedidoRepository.findAll()).singleElement()
				.satisfies(pedido -> assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO));
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(stock(cable)).isEqualTo(100.0);
		// Una liberación por línea: la de la cancelación, no otra al fallar
		assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.LIBERACION).hasSize(2);
	}

	// --- RF-12 a RF-14: rechazos ---

	// HU-23 RF-12: si una línea no alcanza no se registra nada, y la respuesta dice cuánto hay
	@Test
	void siNoAlcanzaNoRegistraNadaYDiceCuantoHay() throws Exception {
		MvcTestResult venta = vender(empleado,
				"[{\"idProducto\":%d,\"cantidad\":11},{\"idProducto\":%d,\"cantidad\":1}]".formatted(martillo, cable),
				null, "EFECTIVO", "1000", "939.5");

		assertThat(venta).hasStatus(HttpStatus.CONFLICT);
		assertThat(venta).bodyJson().extractingPath("$.codigo").isEqualTo("STOCK_INSUFICIENTE");
		assertThat(venta).bodyJson().extractingPath("$.lineas[*].idProducto").asArray().containsExactly(martillo);
		assertThat(venta).bodyJson().extractingPath("$.lineas[0].motivo").isEqualTo("NO_ALCANZA");
		assertThat(venta).bodyJson().extractingPath("$.lineas[0].disponible").isEqualTo(10.0);
		assertThat(pedidoRepository.count()).isZero();
		assertThat(stock(cable)).isEqualTo(100.0);
	}

	// HU-23 RF-12 (casos límite): un producto desactivado mientras se armaba sale como «no disponible»
	@Test
	void unProductoDesactivadoSaleComoNoDisponible() throws Exception {
		assertThat(put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", martillo))
				.hasStatus(HttpStatus.OK);

		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "100", "96.25");

		assertThat(venta).hasStatus(HttpStatus.CONFLICT);
		assertThat(venta).bodyJson().extractingPath("$.lineas[0].motivo").isEqualTo("NO_DISPONIBLE");
		assertThat(pedidoRepository.count()).isZero();
	}

	// HU-23 RF-13: líneas y montos inválidos, 400 sin registrar nada
	@ParameterizedTest
	@ValueSource(strings = {
			// Sin líneas
			"{\"lineas\":[],\"medio\":\"EFECTIVO\",\"recibido\":10,\"totalEsperado\":0}",
			// Cantidad cero, negativa, de más de 999 o con decimales en una unidad entera
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":0}],\"medio\":\"EFECTIVO\",\"recibido\":100,\"totalEsperado\":0}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":-1}],\"medio\":\"EFECTIVO\",\"recibido\":100,\"totalEsperado\":-85}",
			"{\"lineas\":[{\"idProducto\":CABLE,\"cantidad\":1000}],\"medio\":\"EFECTIVO\",\"recibido\":9999,\"totalEsperado\":4500}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1.5}],\"medio\":\"EFECTIVO\",\"recibido\":200,\"totalEsperado\":127.5}",
			// El mismo producto dos veces
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1},{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":200,\"totalEsperado\":170}",
			// Un producto que no existe
			"{\"lineas\":[{\"idProducto\":999999,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":100,\"totalEsperado\":85}",
			// Recibido menor que el total, con tres decimales, negativo, de más de 999 999,99 o vacío en efectivo
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":84.99,\"totalEsperado\":85}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":100.001,\"totalEsperado\":85}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":-100,\"totalEsperado\":85}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":1000000,\"totalEsperado\":85}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"EFECTIVO\",\"recibido\":null,\"totalEsperado\":85}",
			// Con QR no se recibe nada; sin medio
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"medio\":\"QR\",\"recibido\":100,\"totalEsperado\":85}",
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"recibido\":100,\"totalEsperado\":85}",
			// Un comprador que no es una cuenta de Cliente
			"{\"lineas\":[{\"idProducto\":MARTILLO,\"cantidad\":1}],\"idCliente\":999999,\"medio\":\"EFECTIVO\",\"recibido\":100,\"totalEsperado\":85}" })
	void rechazaLineasYMontosInvalidos(String plantilla) throws Exception {
		String cuerpo = plantilla.replace("MARTILLO", martillo.toString()).replace("CABLE", cable.toString());

		MvcTestResult venta = post(VENTAS, empleado, cuerpo);

		assertThat(venta).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(pedidoRepository.count()).isZero();
		assertThat(stock(martillo)).isEqualTo(10.0);
	}

	// HU-23 RF-13: más de 50 líneas
	@Test
	void rechazaMasDeCincuentaLineas() throws Exception {
		List<String> lineas = new ArrayList<>();
		for (int i = 0; i < 51; i++) {
			lineas.add("{\"idProducto\":%d,\"cantidad\":1}".formatted(producto("P" + i, "UNIDAD", "1", "5")));
		}

		assertThat(vender(empleado, "[" + String.join(",", lineas) + "]", null, "EFECTIVO", "100", "51"))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(pedidoRepository.count()).isZero();
	}

	// HU-23 RF-14: si cambió un precio, no se registra y la respuesta trae el total nuevo
	@Test
	void rechazaSiCambioElPrecio() throws Exception {
		assertThat(put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Martillo","idCategoria":%d,"unidad":"UNIDAD","precio":90}""".formatted(herramientas),
				martillo)).hasStatus(HttpStatus.OK);

		MvcTestResult venta = vender(empleado, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "200", "96.25");

		assertThat(venta).hasStatus(HttpStatus.CONFLICT);
		assertThat(venta).bodyJson().extractingPath("$.codigo").isEqualTo("TOTAL_CAMBIADO");
		assertThat(venta).bodyJson().extractingPath("$.total").isEqualTo(101.25);
		assertThat(pedidoRepository.count()).isZero();
	}

	// --- RF-15: el comprobante ---

	// HU-23 RF-15: el comprobante trae las líneas, el comprador, quién vendió y el cobro
	@Test
	void elComprobanteTraeTodo() throws Exception {
		MvcTestResult creada = vender(empleado, LINEAS.formatted(martillo, cable), idAna, "EFECTIVO", "100",
				"96.25");
		Integer venta = id(creada, "$.idPedido");

		MvcTestResult comprobante = get(VENTAS + "/{id}", contador, venta);

		assertThat(comprobante).hasStatus(HttpStatus.OK);
		assertThat(comprobante).bodyJson().extractingPath("$.idPedido").isEqualTo(venta);
		assertThat(comprobante).bodyJson().extractingPath("$.comprador.nombre").isEqualTo("Ana Paz");
		assertThat(comprobante).bodyJson().extractingPath("$.comprador.correo").isEqualTo("ana.hu23@mail.com");
		assertThat(comprobante).bodyJson().extractingPath("$.lineas[*].nombre").asArray()
				.containsExactly("Martillo", "Cable");
		assertThat(comprobante).bodyJson().extractingPath("$.lineas[1].cantidad").isEqualTo(2.5);
		assertThat(comprobante).bodyJson().extractingPath("$.lineas[1].subtotal").isEqualTo(11.25);
		assertThat(comprobante).bodyJson().extractingPath("$.vendidoPor").isEqualTo("Ana Paz");
		assertThat(comprobante).bodyJson().extractingPath("$.confirmado").isNotNull();
		assertThat(comprobante).bodyJson().extractingPath("$.anulable").isEqualTo(true);
	}

	// HU-23 (casos límite): el id de un pedido en línea, o uno que no existe, no es una venta
	@Test
	void unPedidoEnLineaNoEsUnaVenta() throws Exception {
		assertThat(post("/api/carrito/lineas", ana, "{\"idProducto\":%d,\"cantidad\":1}".formatted(martillo)))
				.hasStatus(HttpStatus.OK);
		usuarioRepository.findByCorreo("ana.hu23@mail.com").ifPresent(u -> {
			u.marcarCorreoVerificado();
			usuarioRepository.save(u);
		});
		MvcTestResult pedido = post("/api/pedidos", ana, """
				{"nombreRetira":"Ana Paz","telefonoRetira":"70000000","nota":null,"totalEsperado":85}""");
		assertThat(pedido).hasStatus(HttpStatus.CREATED);
		Integer enLinea = id(pedido, "$.idPedido");

		for (Integer id : List.of(enLinea, 999999)) {
			assertThat(get(VENTAS + "/{id}", empleado, id)).bodyJson().extractingPath("$.codigo")
					.isEqualTo("VENTA_NO_ENCONTRADA");
			assertThat(post(VENTAS + "/{id}/cancelacion", empleado, "", id)).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", id))
					.hasStatus(HttpStatus.NOT_FOUND);
		}
		assertThat(get(VENTAS + "/abc", empleado)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(estado(enLinea)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// --- RF-18 a RF-20: anular ---

	// HU-23 RF-18, RF-19: el Administrador anula la de hoy en efectivo; el stock vuelve a su nombre
	@Test
	void elAdministradorAnulaLaDeHoyEnEfectivo() throws Exception {
		Integer venta = enEfectivo(empleado);

		MvcTestResult anulada = post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"  Producto equivocado  \"}",
				venta);

		assertThat(anulada).hasStatus(HttpStatus.OK);
		assertThat(anulada).bodyJson().extractingPath("$.estado").isEqualTo("ANULADO");
		assertThat(anulada).bodyJson().extractingPath("$.motivoAnulacion").isEqualTo("Producto equivocado");
		assertThat(anulada).bodyJson().extractingPath("$.anuladoPor").isNotNull();
		assertThat(anulada).bodyJson().extractingPath("$.anulado").isNotNull();
		assertThat(anulada).bodyJson().extractingPath("$.anulable").isEqualTo(false);
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(stock(cable)).isEqualTo(100.0);
		Usuario administrador = usuarioRepository.findByCorreo(ADMIN).orElseThrow();
		assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.LIBERACION)
				.hasSize(2).allMatch(m -> m.getUsuario().getIdUsuario().equals(administrador.getIdUsuario()));
	}

	// HU-23 RF-18, RF-20: el motivo es obligatorio y de hasta 200 caracteres
	@Test
	void anularPideUnMotivo() throws Exception {
		Integer venta = enEfectivo(empleado);

		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"   \"}", venta))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"%s\"}".formatted("x".repeat(201)), venta))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(estado(venta)).isEqualTo(EstadoPedido.ENTREGADO);
	}

	// HU-23 RF-20: ni la de ayer, ni la de QR, ni dos veces, ni la pendiente
	@Test
	void noSeAnulaLaDeAyerNiLaDeQrNiDosVeces() throws Exception {
		Integer deQr = conQr(empleado);
		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", deQr)).bodyJson()
				.extractingPath("$.codigo").isEqualTo("VENTA_NO_ANULABLE");
		elBancoPaga(deQr);
		assertThat(post(VENTAS + "/{id}/consulta", empleado, "", deQr)).hasStatus(HttpStatus.OK);
		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", deQr))
				.hasStatus(HttpStatus.CONFLICT);

		Integer dosVeces = enEfectivo(empleado);
		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", dosVeces))
				.hasStatus(HttpStatus.OK);
		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", dosVeces))
				.hasStatus(HttpStatus.CONFLICT);

		Integer deAyer = enEfectivo(empleado);
		// A la 00:01 del día siguiente, en Bolivia
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDateTime manana = LocalDate.now(reloj.withZone(BOLIVIA)).plusDays(1).atTime(LocalTime.of(0, 1));
		reloj.avanzar(Duration.between(ahora, manana));
		MvcTestResult tarde = post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", deAyer);
		assertThat(tarde).hasStatus(HttpStatus.CONFLICT);
		assertThat(get(VENTAS + "/{id}", admin, deAyer)).bodyJson().extractingPath("$.anulable").isEqualTo(false);
		assertThat(estado(deAyer)).isEqualTo(EstadoPedido.ENTREGADO);
	}

	// HU-23 (decisiones de la historia): el historial del stock dice que el movimiento es de una venta del mostrador y
	// cómo quedó la venta, para nombrarlo «Venta en tienda» o «Anulación de venta»
	@Test
	void elHistorialDelStockDiceQueEsUnaVenta() throws Exception {
		Integer venta = enEfectivo(empleado);
		assertThat(post(VENTAS + "/{id}/anulacion", admin, "{\"motivo\":\"Error\"}", venta)).hasStatus(HttpStatus.OK);

		MvcTestResult historial = get("/api/inventario/{id}/movimientos", empleado, martillo);

		assertThat(historial).bodyJson().extractingPath("$.contenido[*].tipo").asArray()
				.containsExactly("LIBERACION", "RESERVA", "ENTRADA");
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].canal").isEqualTo("TIENDA");
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].estadoPedido").isEqualTo("ANULADO");
		assertThat(historial).bodyJson().extractingPath("$.contenido[2].canal").isNull();
	}

	// --- RF-23: permisos ---

	// HU-23 RF-23: registran y cancelan el Empleado y el Administrador; el Contador solo lee; anula solo el
	// Administrador; el Cliente y el visitante, nada
	@Test
	void permisosPorRol() throws Exception {
		Integer venta = enEfectivo(admin);
		Integer deQr = conQr(empleado);

		assertThat(vender(contador, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "100", "96.25"))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(vender(ana, LINEAS.formatted(martillo, cable), null, "EFECTIVO", "100", "96.25"))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri(VENTAS).contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(get(VENTAS + "/{id}", contador, venta)).hasStatus(HttpStatus.OK);
		assertThat(get(VENTAS + "/{id}", ana, venta)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(get(VENTAS + "/productos?texto=martillo", ana)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post(VENTAS + "/{id}/cancelacion", contador, "", deQr)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post(VENTAS + "/{id}/consulta", contador, "", deQr)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post(VENTAS + "/{id}/anulacion", empleado, "{\"motivo\":\"Error\"}", venta))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post(VENTAS + "/{id}/anulacion", contador, "{\"motivo\":\"Error\"}", venta))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(estado(venta)).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(estado(deQr)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// HU-23 (casos límite): con el simulador, el personal simula el pago de una venta con QR
	@Test
	void elSimuladorPagaUnaVentaConQr() throws Exception {
		Integer venta = conQr(empleado);

		assertThat(post(VENTAS + "/{id}/simulacion", empleado, "{\"resultado\":\"PAGADO\"}", venta))
				.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(post(VENTAS + "/{id}/consulta", empleado, "", venta)).bodyJson().extractingPath("$.estado")
				.isEqualTo("ENTREGADO");
	}

	// --- Casos límite: concurrencia ---

	// HU-23 (casos límite): dos ventas a la vez de la última unidad: una registra y la otra recibe STOCK_INSUFICIENTE
	@Test
	void dosVentasDeLaUltimaUnidadALaVez() throws Exception {
		Integer ultimo = producto("Taladro", "UNIDAD", "500", "1");
		String lineas = "[{\"idProducto\":%d,\"cantidad\":1}]".formatted(ultimo);
		CountDownLatch salida = new CountDownLatch(1);
		Callable<Integer> vende = () -> {
			salida.await();
			return vender(empleado, lineas, null, "EFECTIVO", "500", "500").getResponse().getStatus();
		};
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> primera = hilos.submit(vende);
			Future<Integer> segunda = hilos.submit(vende);
			salida.countDown();

			assertThat(List.of(primera.get(), segunda.get())).containsExactlyInAnyOrder(201, 409);
		} finally {
			hilos.shutdownNow();
		}
		assertThat(stock(ultimo)).isEqualTo(0.0);
		assertThat(pedidoRepository.count()).isEqualTo(1);
	}
}
