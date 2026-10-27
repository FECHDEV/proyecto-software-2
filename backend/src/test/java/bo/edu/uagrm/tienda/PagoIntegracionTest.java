package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
import org.mockito.ArgumentCaptor;
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
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.LineaCarrito;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.ConciliacionAutomaticaDePagos;
import bo.edu.uagrm.tienda.service.ConciliacionDePagos;
import bo.edu.uagrm.tienda.service.CorreoDelPedido;
import bo.edu.uagrm.tienda.service.NotificadorCorreo;
import bo.edu.uagrm.tienda.service.PagoService;
import bo.edu.uagrm.tienda.service.PedidoService;
import bo.edu.uagrm.tienda.service.VencimientoPedidos;
import bo.edu.uagrm.tienda.service.pago.EstadoDelCobro;
import bo.edu.uagrm.tienda.service.pago.SimuladorDePago;
import bo.edu.uagrm.tienda.service.pago.SistemaDePagoNoDisponibleException;

// HU-13: pagar el pedido, de punta a punta, con el simulador de pago. Lo que haría el banco lo hace
// SimuladorDePago.forzar; las fallas del sistema de pago, el simulador espiado
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagoIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String PEDIDOS = "/api/pedidos";
	private static final String CARRITO = "/api/carrito";
	private static final String AVISO = "/api/pagos/aviso";
	private static final List<String> CORREOS = List.of("ana.hu13@mail.com", "beto.hu13@mail.com",
			"emp.hu13@mail.com", "con.hu13@mail.com");

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
	private VencimientoPedidos vencimiento;

	@Autowired
	private ConciliacionDePagos conciliacion;

	@Autowired
	private PagoService pagoService;

	@Autowired
	private ConciliacionAutomaticaDePagos conciliacionAutomatica;

	@MockitoSpyBean
	private SimuladorDePago simulador;

	// HU-21: los correos del pedido; en las pruebas, el simulador de correo
	@MockitoSpyBean
	private NotificadorCorreo notificador;

	@MockitoSpyBean
	private PedidoService pedidoService;

	@Autowired
	private RelojDePrueba reloj;

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
	private Integer herramientas;
	private Integer martillo;
	private Integer cable;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		ana = cuentaCon("ana.hu13@mail.com", null);
		beto = cuentaCon("beto.hu13@mail.com", null);
		herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		martillo = producto("Martillo", "UNIDAD", "85.50", "10");
		cable = producto("Cable", "METRO", "12", "100");
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
		verificarCorreo(correo);
		String token = JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
		if (rol != null) {
			Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
			assertThat(put("/api/usuarios/{id}/rol", admin, "{\"rol\":\"%s\"}".formatted(rol), idUsuario))
					.hasStatus(HttpStatus.OK);
		}
		return token;
	}

	// Para confirmar hace falta el correo verificado (HU-20 RF-6); el enlace se prueba en VerificacionCorreoIntegracionTest
	private void verificarCorreo(String correo) {
		Usuario usuario = usuarioRepository.findByCorreo(correo).orElseThrow();
		usuario.marcarCorreoVerificado();
		usuarioRepository.save(usuario);
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

	private void agregar(String token, Integer idProducto, String cantidad) {
		assertThat(post(CARRITO + "/lineas", token, "{\"idProducto\":%s,\"cantidad\":%s}".formatted(idProducto,
				cantidad))).hasStatus(HttpStatus.OK);
	}

	private Integer confirmado(String token, String total) throws Exception {
		MvcTestResult pedido = post(PEDIDOS, token, """
				{"nombreRetira":"Ana Paz","telefonoRetira":"70000000","nota":null,"totalEsperado":%s}"""
				.formatted(total));
		assertThat(pedido).hasStatus(HttpStatus.CREATED);
		return id(pedido, "$.idPedido");
	}

	// Un pedido de 2 martillos (Bs 171)
	private Integer pedidoDeMartillos(String token) throws Exception {
		agregar(token, martillo, "2");
		return confirmado(token, "171");
	}

	private MvcTestResult pagar(String token, Integer idPedido) {
		return post(PEDIDOS + "/{id}/cobro", token, "", idPedido);
	}

	private MvcTestResult consultar(String token, Integer idPedido) {
		return post(PEDIDOS + "/{id}/cobro/consulta", token, "", idPedido);
	}

	private MvcTestResult simular(String token, Integer idPedido, String resultado) {
		return post(PEDIDOS + "/{id}/cobro/simulacion", token, "{\"resultado\":\"%s\"}".formatted(resultado),
				idPedido);
	}

	private MvcTestResult cancelar(String token, Integer idPedido) {
		return post(PEDIDOS + "/{id}/cancelacion", token, "", idPedido);
	}

	// Paga y devuelve el cobro generado
	private Cobro conCobro(String token, Integer idPedido) {
		assertThat(pagar(token, idPedido)).hasStatus(HttpStatus.OK);
		return cobroPendiente(idPedido);
	}

	private Cobro cobroPendiente(Integer idPedido) {
		return cobroRepository.findFirstByPedido_IdPedidoAndEstado(idPedido.longValue(), EstadoCobro.PENDIENTE)
				.orElseThrow();
	}

	// Lo que haría el Cliente desde la app de su banco
	private void elBancoPaga(Cobro cobro) {
		simulador.forzar(cobro.getReferencia(), EstadoDelCobro.PAGADO);
	}

	private Double stock(Integer idProducto) throws Exception {
		return JsonPath.read(get("/api/inventario/{id}", admin, idProducto).getResponse().getContentAsString(),
				"$.stock");
	}

	private EstadoPedido estado(Integer idPedido) {
		return pedidoRepository.findById(idPedido.longValue()).orElseThrow().getEstado();
	}

	private long reservas() {
		return movimientoRepository.findAll().stream().filter(m -> m.getTipo() == TipoMovimiento.RESERVA).count();
	}

	private void caido() {
		SistemaDePagoNoDisponibleException falla = new SistemaDePagoNoDisponibleException("sin conexión", null);
		doThrow(falla).when(simulador).generarCobro(any(), any(), any());
		doThrow(falla).when(simulador).consultar(any());
	}

	// --- RF-1 a RF-3: el cobro ---

	// HU-13 RF-1: el cobro es por el total que guardó el pedido, aunque después cambie un precio
	@Test
	void pagarGeneraElCobroPorElTotalDelPedido() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		assertThat(put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Martillo","idCategoria":%d,"unidad":"UNIDAD","precio":99}""".formatted(herramientas),
				martillo)).hasStatus(HttpStatus.OK);

		MvcTestResult pago = pagar(ana, idPedido);

		assertThat(pago).hasStatus(HttpStatus.OK);
		assertThat(pago).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(pago).bodyJson().extractingPath("$.cobro.codigoQr").asString().endsWith("|171.00");
		assertThat(pago).bodyJson().extractingPath("$.cobro.urlPasarela").isNull();
		assertThat(pago).bodyJson().extractingPath("$.cobro.simulador").isEqualTo(true);
		Cobro cobro = cobroPendiente(idPedido);
		assertThat(cobro.getMonto()).isEqualByComparingTo("171.00");
		verify(simulador).generarCobro(eq(idPedido.longValue()), any(), eq("Pedido N.º " + idPedido
				+ " — Tienda Montero"));
		// Al abrir el pedido, el QR sigue ahí
		assertThat(get(PEDIDOS + "/{id}", ana, idPedido)).bodyJson().extractingPath("$.cobro.codigoQr")
				.isEqualTo(cobro.getCodigoQr());
	}

	// HU-13 RF-1: solo mientras espera el pago
	@Test
	void noSePagaUnPedidoVencidoOCancelado() throws Exception {
		Integer cancelado = pedidoDeMartillos(ana);
		assertThat(cancelar(ana, cancelado)).hasStatus(HttpStatus.OK);
		lineaRepository.deleteAll();
		Integer vencido = pedidoDeMartillos(beto);
		reloj.avanzar(Duration.ofMinutes(30));

		for (MvcTestResult rechazo : List.of(pagar(ana, cancelado), pagar(beto, vencido))) {
			assertThat(rechazo).hasStatus(HttpStatus.CONFLICT);
			assertThat(rechazo).bodyJson().extractingPath("$.codigo").isEqualTo("PEDIDO_NO_PENDIENTE");
		}
		assertThat(cobroRepository.count()).isZero();
	}

	// HU-13 RF-2: volver a pedirlo muestra el mismo QR
	@Test
	void pagarDeNuevoDevuelveElMismoCobro() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		String primero = qr(pagar(ana, idPedido));

		assertThat(pagar(ana, idPedido)).bodyJson().extractingPath("$.cobro.codigoQr").isEqualTo(primero);
		assertThat(cobroRepository.count()).isEqualTo(1);
	}

	// HU-13 RF-2: dos pestañas a la vez ven un solo cobro
	@Test
	void dosPagarALaVezMuestranUnSoloCobro() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);

		List<Object> qrs = aLaVez(() -> qr(pagar(ana, idPedido)), () -> qr(pagar(ana, idPedido)));

		assertThat(qrs.get(0)).isEqualTo(qrs.get(1));
		assertThat(cobroRepository.count()).isEqualTo(1);
	}

	private static String qr(MvcTestResult resultado) throws Exception {
		assertThat(resultado).hasStatus(HttpStatus.OK);
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$.cobro.codigoQr");
	}

	// HU-13 RF-3
	@Test
	void sinSistemaDePagoNoGuardaNadaYRespondeCobroNoDisponible() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		caido();

		MvcTestResult rechazo = pagar(ana, idPedido);

		assertThat(rechazo).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(rechazo).bodyJson().extractingPath("$.codigo").isEqualTo("COBRO_NO_DISPONIBLE");
		assertThat(cobroRepository.count()).isZero();
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// --- RF-4 a RF-6: la consulta y su resultado ---

	// HU-13 RF-4, RF-5: pagado, la reserva queda como venta sin otro movimiento
	@Test
	void laConsultaPagadaPasaElPedidoAPagado() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		elBancoPaga(conCobro(ana, idPedido));

		MvcTestResult pedido = consultar(ana, idPedido);

		assertThat(pedido).hasStatus(HttpStatus.OK);
		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PAGADO");
		assertThat(pedido).bodyJson().extractingPath("$.pagado").isNotNull();
		assertThat(pedido).bodyJson().extractingPath("$.cobro").isNull();
		assertThat(pedido).bodyJson().extractingPath("$.segundosParaPagar").isEqualTo(0);
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(reservas()).isEqualTo(1);
	}

	// Revisión total (26/10/2026): mientras una consulta del cobro espera al sistema de pago, otra a la vez desde la
	// página no vuelve a llamarlo (el aviso sí: pudo llegar después del pago); una vez terminada, la siguiente consulta
	@Test
	void unaSolaConsultaALaVezPorCobro() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		CountDownLatch adentro = new CountDownLatch(1);
		CountDownLatch seguir = new CountDownLatch(1);
		doAnswer(invocacion -> {
			adentro.countDown();
			seguir.await();
			return invocacion.callRealMethod();
		}).when(simulador).consultar(cobro.getReferencia());
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			Future<MvcTestResult> primera = hilo.submit(() -> consultar(ana, idPedido));
			adentro.await();

			assertThat(consultar(ana, idPedido)).hasStatus(HttpStatus.OK);
			verify(simulador, times(1)).consultar(cobro.getReferencia());

			seguir.countDown();
			assertThat(primera.get()).hasStatus(HttpStatus.OK);
		} finally {
			seguir.countDown();
			hilo.shutdown();
		}
		doCallRealMethod().when(simulador).consultar(any());

		consultar(ana, idPedido);
		pagoService.procesarAviso(cobro.getReferencia());
		verify(simulador, times(3)).consultar(cobro.getReferencia());
	}

	// HU-13 RF-4: el pago nunca se da por hecho sin el sistema de pago
	@Test
	void laConsultaPendienteNoCambiaNada() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		conCobro(ana, idPedido);

		MvcTestResult pedido = consultar(ana, idPedido);

		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(pedido).bodyJson().extractingPath("$.cobro.codigoQr").isNotNull();
		assertThat(cobroPendiente(idPedido).getConsultadoSinPago()).isNotNull();
	}

	// HU-13 RF-5: pagado no se cancela ni vence
	@Test
	void pagadoNoSeCancelaNiVenceYNoMueveElStock() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		elBancoPaga(conCobro(ana, idPedido));
		consultar(ana, idPedido);

		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.CONFLICT);
		reloj.avanzar(Duration.ofMinutes(31));
		vencimiento.vencer();

		assertThat(get(PEDIDOS + "/{id}", ana, idPedido)).bodyJson().extractingPath("$.estado").isEqualTo("PAGADO");
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
		assertThat(pagar(ana, idPedido)).hasStatus(HttpStatus.CONFLICT);
	}

	// HU-13 RF-6: el error descarta el cobro y se puede generar otro
	@Test
	void elErrorDescartaElCobroYPermiteOtro() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro primero = conCobro(ana, idPedido);
		simulador.forzar(primero.getReferencia(), EstadoDelCobro.ERROR);

		MvcTestResult pedido = consultar(ana, idPedido);

		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(pedido).bodyJson().extractingPath("$.cobro").isNull();
		assertThat(cobroRepository.findById(primero.getIdCobro()).orElseThrow().getEstado())
				.isEqualTo(EstadoCobro.ERROR);
		assertThat(qr(pagar(ana, idPedido))).isNotEqualTo(primero.getCodigoQr());
	}

	// --- RF-7 y RF-8: vencer con cobro y la tarea de cada minuto ---

	// HU-13 RF-7: sin consultar después de la hora, no vence; leer no llama al sistema de pago
	@Test
	void noVenceSinConsultarElCobro() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		conCobro(ana, idPedido);
		reloj.avanzar(Duration.ofMinutes(31));

		MvcTestResult pedido = get(PEDIDOS + "/{id}", ana, idPedido);

		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(pedido).bodyJson().extractingPath("$.segundosParaPagar").isEqualTo(0);
		verify(simulador, never()).consultar(any());
		// El carrito lo sigue avisando, y no deja confirmar otro
		agregar(ana, cable, "1");
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.idPedidoPendiente").isEqualTo(idPedido);
		MvcTestResult otro = post(PEDIDOS, ana, """
				{"nombreRetira":"Ana Paz","telefonoRetira":"70000000","nota":null,"totalEsperado":12}""");
		assertThat(otro).bodyJson().extractingPath("$.codigo").isEqualTo("PEDIDO_PENDIENTE");
		assertThat(stock(martillo)).isEqualTo(8.0);
	}

	// HU-13 RF-7: consultado después de la hora y sin pagar, vence; también desde la página al llegar a cero
	@Test
	void venceSiLaConsultaDiceQueNoSePago() throws Exception {
		Integer porLaTarea = pedidoDeMartillos(ana);
		conCobro(ana, porLaTarea);
		Integer porLaPagina = pedidoDeMartillos(beto);
		conCobro(beto, porLaPagina);
		reloj.avanzar(Duration.ofMinutes(30));

		MvcTestResult pagina = consultar(beto, porLaPagina);
		vencimiento.vencer();

		assertThat(estado(porLaTarea)).isEqualTo(EstadoPedido.VENCIDO);
		assertThat(pagina).bodyJson().extractingPath("$.estado").isEqualTo("VENCIDO");
		assertThat(stock(martillo)).isEqualTo(10.0);
	}

	// HU-13 RF-7: pagó en el último segundo y el sistema se entera al ir a vencerlo
	@Test
	void pagadoAlUltimoSegundoQuedaPagado() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		reloj.avanzar(Duration.ofMinutes(30).minusSeconds(1));
		elBancoPaga(cobro);
		reloj.avanzar(Duration.ofMinutes(1));

		vencimiento.vencer();

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
	}

	// HU-13 RF-7: si la consulta falla, no vence y se reintenta
	@Test
	void siLaConsultaFallaNoVence() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		conCobro(ana, idPedido);
		reloj.avanzar(Duration.ofMinutes(31));
		caido();

		vencimiento.vencer();
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);

		doCallRealMethod().when(simulador).consultar(any());
		vencimiento.vencer();
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.VENCIDO);
	}

	// HU-13 RF-8: la tarea mira los cobros pendientes de las últimas 24 horas
	@Test
	void laTareaConciliaLosDeLasUltimas24Horas() throws Exception {
		Integer viejo = pedidoDeMartillos(ana);
		Cobro cobroViejo = conCobro(ana, viejo);
		assertThat(cancelar(ana, viejo)).hasStatus(HttpStatus.OK);
		lineaRepository.deleteAll();
		reloj.avanzar(Duration.ofHours(25));
		// El token dura 8 horas
		beto = token("beto.hu13@mail.com", "secreta12");
		Integer reciente = pedidoDeMartillos(beto);
		Cobro cobroReciente = conCobro(beto, reciente);
		elBancoPaga(cobroViejo);
		elBancoPaga(cobroReciente);

		conciliacionAutomatica.conciliarPendientes();

		assertThat(estado(reciente)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(estado(viejo)).isEqualTo(EstadoPedido.CANCELADO);
	}

	// HU-13 RF-8: un cobro que no se puede consultar no frena a los demás
	@Test
	void unoQueFallaNoFrenaAlResto() throws Exception {
		Integer deAna = pedidoDeMartillos(ana);
		Cobro cobroDeAna = conCobro(ana, deAna);
		Integer deBeto = pedidoDeMartillos(beto);
		Cobro cobroDeBeto = conCobro(beto, deBeto);
		elBancoPaga(cobroDeBeto);
		doThrow(new SistemaDePagoNoDisponibleException("sin conexión", null)).when(simulador)
				.consultar(cobroDeAna.getReferencia());

		conciliacionAutomatica.conciliarPendientes();

		assertThat(estado(deBeto)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(estado(deAna)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// --- RF-9: el pago tardío ---

	// HU-13 RF-9: con stock, revive; el stock vuelve a reservarse y sus cantidades salen del carrito
	@Test
	void pagoTardioConStockRevive() throws Exception {
		agregar(ana, martillo, "2");
		agregar(ana, cable, "3.5");
		Integer idPedido = confirmado(ana, "213");
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		// El Cliente sigue comprando: un martillo más
		agregar(ana, martillo, "1");
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(stock(cable)).isEqualTo(96.5);
		assertThat(reservas()).isEqualTo(4);
		MvcTestResult carrito = get(CARRITO, ana);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[*].idProducto").asArray().containsExactly(martillo);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(1.0);
	}

	// HU-13 RF-9, RF-15: sin stock, queda para devolver sin tocar el stock ni el carrito
	@Test
	void pagoTardioSinStockQuedaParaDevolver() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":1,\"motivo\":\"CONTEO\"}", martillo))
				.hasStatus(HttpStatus.CREATED);
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		MvcTestResult pedido = get(PEDIDOS + "/{id}", ana, idPedido);
		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PAGO_A_DEVOLVER");
		assertThat(pedido).bodyJson().extractingPath("$.pagado").isNotNull();
		assertThat(stock(martillo)).isEqualTo(1.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
	}

	// HU-13 RF-9 (revisión de HU-13, /code-review): solo sale del carrito lo que volvió; lo que quedó fuera por el
	// tope de 999 no se resta de lo que el Cliente ya tenía
	@Test
	void pagoTardioRestaSoloLoQueVolvioAlCarrito() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		lineaRepository.save(LineaCarrito.crear(usuarioRepository.findByCorreo("ana.hu13@mail.com").orElseThrow(),
				productoRepository.findById(martillo.longValue()).orElseThrow(), new BigDecimal("998"),
				LocalDateTime.now(reloj)));
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		// Volvió 1 (998 + 2 queda en 999): el pago tardío resta ese 1
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(998.0);
	}

	// HU-13 RF-9 (revisión total): si el Cliente quitó lo que volvió y después agregó el producto otra vez, esa línea
	// es nueva y no tiene nada del pedido: el pago tardío no la toca
	@Test
	void pagoTardioNoRestaUnaLineaAgregadaDespues() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		assertThat(mvc.delete().uri(CARRITO + "/lineas/{id}", martillo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ana).exchange()).hasStatus2xxSuccessful();
		reloj.avanzar(Duration.ofMinutes(1));
		agregar(ana, martillo, "1");
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(1.0);
	}

	// HU-13 RF-7 (revisión de HU-13, /code-review ronda 2): un pago entre la conciliación y el vencimiento no se
	// pierde, porque la tarea de vencimiento consulta otra vez
	@Test
	void unPagoDespuesDeLaConciliacionNoSePierde() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		reloj.avanzar(Duration.ofMinutes(31));
		conciliacion.conciliar(cobro.getIdCobro());
		elBancoPaga(cobro);

		vencimiento.vencer();

		verify(simulador, times(2)).consultar(any());
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(stock(martillo)).isEqualTo(8.0);
	}

	// HU-13 RF-9: un producto que ya no se vende tampoco revive
	@Test
	void pagoTardioDeUnProductoQueYaNoSeVendeVaADevolver() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		reloj.avanzar(Duration.ofMinutes(30));
		vencimiento.vencer();
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.VENCIDO);
		assertThat(put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", martillo))
				.hasStatus(HttpStatus.OK);
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGO_A_DEVOLVER);
		assertThat(stock(martillo)).isEqualTo(10.0);
	}

	// HU-13 RF-9: el que revive no choca con otro pedido pendiente
	@Test
	void pagoTardioConOtroPedidoPendienteRevive() throws Exception {
		Integer primero = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, primero);
		assertThat(cancelar(ana, primero)).hasStatus(HttpStatus.OK);
		Integer segundo = confirmado(ana, "171");
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(primero)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(estado(segundo)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
		assertThat(stock(martillo)).isEqualTo(6.0);
	}

	// HU-13 RF-9, RF-11: cancelar y conciliar a la vez terminan en pagado, con el stock reservado una vez
	@Test
	void cancelarYPagarALaVezTerminaPagadoOParaDevolver() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		elBancoPaga(cobro);

		aLaVez(() -> cancelar(ana, idPedido), () -> conciliacion.conciliar(cobro.getIdCobro()));

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
	}

	// --- RF-10 y RF-11: el aviso y una sola aplicación ---

	// HU-13 RF-10: sin sesión, siempre 200
	@Test
	void elAvisoSiempreResponde200SinSesion() {
		assertThat(mvc.get().uri(AVISO + "?referencia=desconocida").exchange()).hasStatus(HttpStatus.OK);
		assertThat(mvc.post().uri(AVISO + "?referencia=desconocida").exchange()).hasStatus(HttpStatus.OK);
		assertThat(mvc.post().uri(AVISO).exchange()).hasStatus(HttpStatus.OK);
	}

	// HU-13 RF-10: el aviso adelanta la consulta, que es la que aplica el pago
	@Test
	void elAvisoAdelantaLaConsulta() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(mvc.post().uri(AVISO + "?referencia={ref}", cobro.getReferencia()).exchange())
				.hasStatus(HttpStatus.OK);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
		elBancoPaga(cobro);

		assertThat(mvc.get().uri(AVISO + "?referencia={ref}", cobro.getReferencia()).exchange())
				.hasStatus(HttpStatus.OK);

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
	}

	// HU-13 RF-10: lo que no puede ser nuestro no llega a consultar
	@Test
	void unaReferenciaDesconocidaOLargaNoConsulta() {
		mvc.get().uri(AVISO + "?referencia={ref}", "x".repeat(121)).exchange();
		mvc.get().uri(AVISO + "?referencia=desconocida").exchange();
		mvc.get().uri(AVISO + "?referencia=").exchange();

		verify(simulador, never()).consultar(any());
	}

	// HU-13 RF-11: dos conciliaciones a la vez aplican el pago tardío una sola vez
	@Test
	void dosConciliacionesALaVezAplicanElPagoUnaVez() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		elBancoPaga(cobro);

		aLaVez(() -> conciliacion.conciliar(cobro.getIdCobro()), () -> conciliacion.conciliar(cobro.getIdCobro()));

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(reservas()).isEqualTo(2);
	}

	// --- RF-12 y RF-14: el simulador y los permisos ---

	// HU-13 RF-12: con el simulador, pagar y fallar a pedido
	@Test
	void simularPagoYError() throws Exception {
		Integer pagado = pedidoDeMartillos(ana);
		conCobro(ana, pagado);
		Integer conError = pedidoDeMartillos(beto);
		conCobro(beto, conError);

		assertThat(simular(ana, pagado, "PAGADO")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(simular(beto, conError, "ERROR")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(simular(beto, conError, "PENDIENTE")).hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(consultar(ana, pagado)).bodyJson().extractingPath("$.estado").isEqualTo("PAGADO");
		assertThat(consultar(beto, conError)).bodyJson().extractingPath("$.cobro").isNull();
	}

	// HU-13 RF-12, RF-14: solo el dueño, y solo con un cobro pendiente
	@Test
	void simularElCobroDeOtroResponde404() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);

		assertThat(simular(ana, idPedido, "PAGADO")).hasStatus(HttpStatus.NOT_FOUND);
		conCobro(ana, idPedido);
		assertThat(simular(beto, idPedido, "PAGADO")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// HU-13 RF-14
	@Test
	void soloElClientePagaYConsulta() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		String empleado = cuentaCon("emp.hu13@mail.com", "EMPLEADO");
		String contador = cuentaCon("con.hu13@mail.com", "CONTADOR");

		for (String personal : List.of(admin, empleado, contador)) {
			assertThat(pagar(personal, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(consultar(personal, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(simular(personal, idPedido, "PAGADO")).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(mvc.post().uri(PEDIDOS + "/{id}/cobro", idPedido).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(cobroRepository.count()).isZero();
	}

	// HU-13 RF-14: el de otro, uno inexistente o un id que no es un número responden 404
	@Test
	void elPedidoDeOtroResponde404() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);

		assertThat(pagar(beto, idPedido)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(consultar(beto, idPedido)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(pagar(ana, 999_999)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(PEDIDOS + "/abc/cobro", ana, "")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(PEDIDOS + "/99999999999999999999/cobro/consulta", ana, "")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(cobroRepository.count()).isZero();
	}

	// --- HU-21: los correos del pedido ---

	private CorreoDelPedido pedidoPagadoEnviado() {
		ArgumentCaptor<CorreoDelPedido> correo = ArgumentCaptor.forClass(CorreoDelPedido.class);
		verify(notificador).enviarPedidoPagado(correo.capture());
		return correo.getValue();
	}

	// HU-21 RF-1, RF-3: a la cuenta que hizo el pedido, con lo que guardó el pedido
	@Test
	void alPagarseElPedidoSeEnviaPedidoPagadoConSusDatos() throws Exception {
		agregar(ana, martillo, "2");
		agregar(ana, cable, "3.5");
		Integer idPedido = confirmado(ana, "213");
		elBancoPaga(conCobro(ana, idPedido));

		assertThat(consultar(ana, idPedido)).bodyJson().extractingPath("$.estado").isEqualTo("PAGADO");

		CorreoDelPedido correo = pedidoPagadoEnviado();
		assertThat(correo.correo()).isEqualTo("ana.hu13@mail.com");
		assertThat(correo.nombre()).isEqualTo("Ana");
		assertThat(correo.numero()).isEqualTo(idPedido.longValue());
		assertThat(correo.fechaPago()).isNotNull();
		assertThat(correo.total()).isEqualByComparingTo("213");
		assertThat(correo.nombreRetira()).isEqualTo("Ana Paz");
		assertThat(correo.telefonoRetira()).isEqualTo("70000000");
		assertThat(correo.lineas()).extracting(CorreoDelPedido.Linea::nombre).containsExactly("Martillo", "Cable");
		CorreoDelPedido.Linea delCable = correo.lineas().get(1);
		assertThat(delCable.unidad()).isEqualTo(UnidadVenta.METRO);
		assertThat(delCable.cantidad()).isEqualByComparingTo("3.5");
		assertThat(delCable.precio()).isEqualByComparingTo("12");
		assertThat(delCable.subtotal()).isEqualByComparingTo("42");
		verify(notificador, never()).enviarPagoADevolver(any());
	}

	// HU-21 RF-1: «Simular pago» pasa por la misma conciliación
	@Test
	void simularPagoTambienEnviaElCorreo() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		conCobro(ana, idPedido);

		assertThat(simular(ana, idPedido, "PAGADO")).hasStatus(HttpStatus.NO_CONTENT);
		consultar(ana, idPedido);

		assertThat(pedidoPagadoEnviado().numero()).isEqualTo(idPedido.longValue());
	}

	// HU-21 RF-2
	@Test
	void unPagoTardioQueReviveEnviaPedidoPagado() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(pedidoPagadoEnviado().numero()).isEqualTo(idPedido.longValue());
		verify(notificador, never()).enviarPagoADevolver(any());
	}

	// HU-21 RF-4
	@Test
	void unPagoTardioSinStockEnviaPagoADevolverYNoPedidoPagado() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":1,\"motivo\":\"CONTEO\"}", martillo))
				.hasStatus(HttpStatus.CREATED);
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGO_A_DEVOLVER);
		ArgumentCaptor<CorreoDelPedido> correo = ArgumentCaptor.forClass(CorreoDelPedido.class);
		verify(notificador).enviarPagoADevolver(correo.capture());
		assertThat(correo.getValue().correo()).isEqualTo("ana.hu13@mail.com");
		assertThat(correo.getValue().total()).isEqualByComparingTo("171");
		assertThat(correo.getValue().telefonoRetira()).isEqualTo("70000000");
		verify(notificador, never()).enviarPedidoPagado(any());
	}

	// HU-21 RF-5: el pago se aplica una sola vez, y el correo también; volver a consultar no manda otro
	@Test
	void dosConciliacionesALaVezEnvianUnSoloCorreo() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		elBancoPaga(cobro);

		aLaVez(() -> conciliacion.conciliar(cobro.getIdCobro()), () -> conciliacion.conciliar(cobro.getIdCobro()));
		consultar(ana, idPedido);
		conciliacion.conciliar(cobro.getIdCobro());

		verify(notificador, times(1)).enviarPedidoPagado(any());
	}

	// HU-21 RF-6: si la transacción del pago se deshace, no sale el correo
	@Test
	void siLaTransaccionDelPagoSeDeshaceNoSeEnviaElCorreo() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);
		elBancoPaga(cobro);
		doThrow(new IllegalStateException("falla al revivir")).when(pedidoService).revivir(any(), any(), any());

		assertThatThrownBy(() -> conciliacion.conciliar(cobro.getIdCobro())).hasMessageContaining("falla al revivir");

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.CANCELADO);
		assertThat(cobroPendiente(idPedido)).isNotNull();
		verify(notificador, never()).enviarPedidoPagado(any());
		verify(notificador, never()).enviarPagoADevolver(any());
	}

	// HU-21 RF-7: el envío no cambia el pago
	@Test
	void siElNotificadorFallaElPedidoQuedaPagado() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		elBancoPaga(conCobro(ana, idPedido));
		doThrow(new IllegalStateException("correo caído")).when(notificador).enviarPedidoPagado(any());

		assertThat(consultar(ana, idPedido)).bodyJson().extractingPath("$.estado").isEqualTo("PAGADO");
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		// Se intentó enviar: el fallo no deshizo el pago (/code-review, ronda 2)
		verify(notificador).enviarPedidoPagado(any());
	}

	// HU-21 caso límite: el pago es de un pedido suyo, así que el correo sale aunque la cuenta esté desactivada
	@Test
	void conLaCuentaDesactivadaElCorreoSaleIgual() throws Exception {
		Integer idPedido = pedidoDeMartillos(ana);
		Cobro cobro = conCobro(ana, idPedido);
		Usuario usuario = usuarioRepository.findByCorreo("ana.hu13@mail.com").orElseThrow();
		usuario.desactivar();
		usuarioRepository.save(usuario);
		elBancoPaga(cobro);

		conciliacion.conciliar(cobro.getIdCobro());

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
		assertThat(pedidoPagadoEnviado().correo()).isEqualTo("ana.hu13@mail.com");
	}

	private static List<Object> aLaVez(Callable<?>... tareas) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<Object>> futuros = Arrays.stream(tareas).map(tarea -> hilos.submit(() -> {
				largada.await();
				return (Object) tarea.call();
			})).toList();
			largada.countDown();
			List<Object> resultados = new ArrayList<>();
			for (Future<Object> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			hilos.shutdown();
		}
	}
}
