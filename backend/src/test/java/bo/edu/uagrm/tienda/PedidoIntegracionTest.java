package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

import bo.edu.uagrm.tienda.dto.RetiroResponse;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.VencimientoPedidos;

// HU-12: confirmar, ver, cancelar y vencer el pedido, de punta a punta. El reloj lo mueve cada prueba: arranca en la
// hora real, así la tarea programada de otro contexto (con el reloj real) no vence estos pedidos antes de tiempo
@SpringBootTest(properties = { "tienda.retiro.direccion=Av. Principal 123, Montero",
		"tienda.retiro.horario=Lunes a sábado, de 8:00 a 18:00" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String PEDIDOS = "/api/pedidos";
	private static final String CARRITO = "/api/carrito";
	private static final List<String> CORREOS = List.of("ana.hu12@mail.com", "beto.hu12@mail.com",
			"emp.hu12@mail.com", "con.hu12@mail.com");

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
	private RelojDePrueba reloj;

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
		ana = cuentaCon("ana.hu12@mail.com", null);
		beto = cuentaCon("beto.hu12@mail.com", null);
		idAna = usuarioRepository.findByCorreo("ana.hu12@mail.com").orElseThrow().getIdUsuario().intValue();
		herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		martillo = producto("Martillo", "UNIDAD", "85.50", "10");
		cable = producto("Cable", "METRO", "12", "100");
	}

	@AfterEach
	void limpiar() {
		reloj.reiniciar();
		lineaRepository.deleteAll();
		movimientoRepository.deleteAll();
		pedidoRepository.deleteAll();
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
		verificarCorreo(correo);
		String token = JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
		if (rol != null) {
			Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
			assertThat(put("/api/usuarios/{id}/rol", admin, "{\"rol\":\"%s\"}".formatted(rol), idUsuario))
					.hasStatus(HttpStatus.OK);
		}
		return token;
	}

	// Como recién registrada: sin el enlace del correo abierto
	private String clienteSinVerificar(String correo) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Carla\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(correo))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
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

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	private void agregar(String token, Integer idProducto, String cantidad) {
		assertThat(post(CARRITO + "/lineas", token, "{\"idProducto\":%s,\"cantidad\":%s}".formatted(idProducto,
				cantidad))).hasStatus(HttpStatus.OK);
	}

	private MvcTestResult confirmar(String token, String total) {
		return confirmar(token, "Ana Paz", "70000000", null, total);
	}

	private MvcTestResult confirmar(String token, String nombre, String telefono, String nota, String total) {
		return post(PEDIDOS, token, """
				{"nombreRetira":%s,"telefonoRetira":%s,"nota":%s,"totalEsperado":%s}"""
				.formatted(json(nombre), json(telefono), json(nota), total));
	}

	private static String json(String texto) {
		return texto == null ? "null" : "\"" + texto + "\"";
	}

	private Integer confirmado(String token, String total) throws Exception {
		MvcTestResult pedido = confirmar(token, total);
		assertThat(pedido).hasStatus(HttpStatus.CREATED);
		return id(pedido, "$.idPedido");
	}

	private MvcTestResult cancelar(String token, Integer idPedido) {
		return post(PEDIDOS + "/{id}/cancelacion", token, "", idPedido);
	}

	private Double stock(Integer idProducto) throws Exception {
		return JsonPath.read(get("/api/inventario/{id}", admin, idProducto).getResponse().getContentAsString(),
				"$.stock");
	}

	private void ajustarStock(Integer idProducto, String contada) {
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":%s,\"motivo\":\"CONTEO\"}"
				.formatted(contada), idProducto)).hasStatus(HttpStatus.CREATED);
	}

	private EstadoPedido estado(Integer idPedido) {
		return pedidoRepository.findById(idPedido.longValue()).orElseThrow().getEstado();
	}

	// HU-20 RF-6: el rechazo llega antes de tocar el carrito o el stock
	@Test
	void sinElCorreoVerificadoNoConfirmaNiReservaStock() throws Exception {
		String carla = clienteSinVerificar("con.hu12@mail.com");
		agregar(carla, martillo, "2");

		MvcTestResult rechazo = confirmar(carla, "171");

		assertThat(rechazo).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(rechazo).bodyJson().extractingPath("$.codigo").isEqualTo("CORREO_SIN_VERIFICAR");
		assertThat(pedidoRepository.count()).isZero();
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(get(CARRITO, carla)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(1);
	}

	// HU-20 RF-6, RF-15: al verificar, la misma sesión confirma sin volver a entrar
	@Test
	void alVerificarElCorreoYaPuedeConfirmar() throws Exception {
		String carla = clienteSinVerificar("con.hu12@mail.com");
		agregar(carla, martillo, "2");
		assertThat(confirmar(carla, "171")).hasStatus(HttpStatus.FORBIDDEN);

		verificarCorreo("con.hu12@mail.com");

		assertThat(confirmar(carla, "171")).hasStatus(HttpStatus.CREATED);
	}

	// HU-12 RF-2
	@Test
	void confirmarCreaElPedidoReservaYVaciaElCarrito() throws Exception {
		agregar(ana, martillo, "2");
		agregar(ana, cable, "3.5");

		MvcTestResult pedido = confirmar(ana, "213");

		assertThat(pedido).hasStatus(HttpStatus.CREATED);
		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(pedido).bodyJson().extractingPath("$.total").isEqualTo(213.0);
		assertThat(pedido).bodyJson().extractingPath("$.lineas[*].nombre").asArray()
				.containsExactly("Martillo", "Cable");
		assertThat(pedido).bodyJson().extractingPath("$.segundosParaPagar").isEqualTo(1800);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(0);
		assertThat(stock(martillo)).isEqualTo(8.0);
		assertThat(stock(cable)).isEqualTo(96.5);
	}

	// HU-12 RF-2
	@Test
	void elPedidoCopiaPrecioNombreYUnidad() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		assertThat(put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Martillo de uña","idCategoria":%d,"unidad":"UNIDAD","precio":99}""".formatted(herramientas),
				martillo)).hasStatus(HttpStatus.OK);

		MvcTestResult pedido = get(PEDIDOS + "/{id}", ana, idPedido);

		assertThat(pedido).bodyJson().extractingPath("$.lineas[0].idProducto").isEqualTo(martillo);
		assertThat(pedido).bodyJson().extractingPath("$.lineas[0].nombre").isEqualTo("Martillo");
		assertThat(pedido).bodyJson().extractingPath("$.lineas[0].unidad").isEqualTo("UNIDAD");
		assertThat(pedido).bodyJson().extractingPath("$.lineas[0].precio").isEqualTo(85.5);
		assertThat(pedido).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
		assertThat(pedido).bodyJson().extractingPath("$.lineas[0].subtotal").isEqualTo(171.0);
		assertThat(pedido).bodyJson().extractingPath("$.total").isEqualTo(171.0);
	}

	// HU-12 RF-3
	@Test
	void sinStockSuficienteNoCreaNiReservaNada() throws Exception {
		agregar(ana, martillo, "5");
		agregar(ana, cable, "1");
		ajustarStock(martillo, "3");

		MvcTestResult rechazo = confirmar(ana, "439.5");

		assertThat(rechazo).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazo).bodyJson().extractingPath("$.codigo").isEqualTo("STOCK_INSUFICIENTE");
		assertThat(rechazo).bodyJson().extractingPath("$.lineas[0].idProducto").isEqualTo(martillo);
		assertThat(rechazo).bodyJson().extractingPath("$.lineas[0].motivo").isEqualTo("NO_ALCANZA");
		// Sin cuánto hay: solo el producto y el motivo
		assertThat(rechazo).bodyJson().extractingPath("$.lineas[0]").asMap().containsOnlyKeys("idProducto", "motivo");
		assertThat(rechazo.getResponse().getContentAsString()).doesNotContain("\"stock\"");
		assertThat(pedidoRepository.count()).isZero();
		assertThat(stock(martillo)).isEqualTo(3.0);
		assertThat(stock(cable)).isEqualTo(100.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(2);
	}

	// HU-12 RF-3
	@Test
	void diceCadaLineaQueFallaSinElStock() throws Exception {
		Integer pala = producto("Pala", "UNIDAD", "50", "5");
		Integer llave = producto("Llave", "UNIDAD", "10", "2");
		Integer tornillo = producto("Tornillo", "UNIDAD", "1", "100");
		agregar(ana, martillo, "1");
		agregar(ana, cable, "2.5");
		agregar(ana, pala, "1");
		agregar(ana, llave, "4");
		agregar(ana, tornillo, "10");
		ajustarStock(martillo, "0");
		ajustarStock(cable, "0");
		assertThat(put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Cable","idCategoria":%d,"unidad":"ROLLO","precio":12}""".formatted(herramientas),
				cable)).hasStatus(HttpStatus.OK);
		ajustarStock(cable, "5");
		put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", pala);

		MvcTestResult rechazo = confirmar(ana, "10");

		assertThat(rechazo).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazo).bodyJson().extractingPath("$.lineas[*].idProducto").asArray()
				.containsExactly(martillo, cable, pala, llave);
		assertThat(rechazo).bodyJson().extractingPath("$.lineas[*].motivo").asArray()
				.containsExactly("AGOTADO", "CANTIDAD_INVALIDA", "NO_DISPONIBLE", "NO_ALCANZA");
		assertThat(stock(llave)).isEqualTo(2.0);
		assertThat(stock(tornillo)).isEqualTo(100.0);
	}

	// HU-12 RF-4
	@Test
	void carritoVacioNoConfirma() {
		MvcTestResult vacio = confirmar(ana, "0");

		assertThat(vacio).hasStatus(HttpStatus.CONFLICT);
		assertThat(vacio).bodyJson().extractingPath("$.codigo").isEqualTo("CARRITO_VACIO");
		assertThat(pedidoRepository.count()).isZero();
	}

	// HU-12 RF-5
	@Test
	void conUnPedidoPendienteNoConfirmaOtro() throws Exception {
		agregar(ana, martillo, "1");
		Integer idPedido = confirmado(ana, "85.5");
		agregar(ana, cable, "1");

		MvcTestResult otro = confirmar(ana, "12");

		assertThat(otro).hasStatus(HttpStatus.CONFLICT);
		assertThat(otro).bodyJson().extractingPath("$.codigo").isEqualTo("PEDIDO_PENDIENTE");
		assertThat(otro).bodyJson().extractingPath("$.idPedido").isEqualTo(idPedido);
		assertThat(pedidoRepository.count()).isEqualTo(1);
		assertThat(stock(cable)).isEqualTo(100.0);
	}

	// HU-12 RF-5
	@Test
	void elCarritoDiceElPedidoPendiente() throws Exception {
		agregar(ana, martillo, "1");
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.idPedidoPendiente").isNull();

		Integer idPedido = confirmado(ana, "85.5");
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.idPedidoPendiente").isEqualTo(idPedido);
		assertThat(get(CARRITO, beto)).bodyJson().extractingPath("$.idPedidoPendiente").isNull();

		cancelar(ana, idPedido);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.idPedidoPendiente").isNull();
	}

	// HU-12 RF-6
	@Test
	void validaLosDatosDeRetiro() throws Exception {
		agregar(ana, martillo, "1");
		String largo = "a".repeat(160);

		assertThat(confirmar(ana, "   ", "70000000", null, "85.5")).bodyJson().extractingPath("$.errores[0].campo")
				.isEqualTo("nombreRetira");
		assertThat(confirmar(ana, largo + "a", "70000000", null, "85.5")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(confirmar(ana, "Ana", " ", null, "85.5")).bodyJson().extractingPath("$.errores[0].campo")
				.isEqualTo("telefonoRetira");
		assertThat(confirmar(ana, "Ana", "1".repeat(21), null, "85.5")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(confirmar(ana, "Ana", "70000000", "n".repeat(201), "85.5")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(confirmar(ana, "Ana", "70000000", null, "null")).bodyJson().extractingPath("$.errores[0].campo")
				.isEqualTo("totalEsperado");
		assertThat(pedidoRepository.count()).isZero();

		// Los espacios alrededor no cuentan
		MvcTestResult valido = confirmar(ana, "  " + largo + "  ", " 70000000 ", "   ", "85.5");
		assertThat(valido).hasStatus(HttpStatus.CREATED);
		assertThat(valido).bodyJson().extractingPath("$.nombreRetira").isEqualTo(largo);
		assertThat(valido).bodyJson().extractingPath("$.telefonoRetira").isEqualTo("70000000");
		assertThat(valido).bodyJson().extractingPath("$.nota").isNull();
	}

	// HU-12 RF-1, RF-6
	@Test
	void otrosDatosDeRetiroNoCambianLaCuenta() {
		agregar(ana, martillo, "1");

		MvcTestResult pedido = confirmar(ana, "Luis Rojas", "71234567", "Retira mi hermano", "85.5");

		assertThat(pedido).bodyJson().extractingPath("$.nombreRetira").isEqualTo("Luis Rojas");
		assertThat(pedido).bodyJson().extractingPath("$.telefonoRetira").isEqualTo("71234567");
		assertThat(pedido).bodyJson().extractingPath("$.nota").isEqualTo("Retira mi hermano");
		MvcTestResult cuenta = get("/api/cuenta/datos-personales", ana);
		assertThat(cuenta).bodyJson().extractingPath("$.nombre").isEqualTo("Ana");
		assertThat(cuenta).bodyJson().extractingPath("$.telefono").isNull();
	}

	// HU-12 RF-7
	@Test
	void dosConfirmacionesPorElUltimoStockReservanUnaSola() throws Exception {
		Integer pala = producto("Pala", "UNIDAD", "50", "1");
		agregar(ana, pala, "1");
		agregar(beto, pala, "1");

		List<Object> resultados = aLaVez(() -> confirmar(ana, "50"), () -> confirmar(beto, "50"));

		assertThat(resultados).extracting(resultado -> ((MvcTestResult) resultado).getResponse().getStatus())
				.containsExactlyInAnyOrder(201, 409);
		assertThat(pedidoRepository.count()).isEqualTo(1);
		assertThat(stock(pala)).isZero();
	}

	// HU-12 RF-7
	@Test
	void dosConfirmacionesDelMismoClienteCreanUnSoloPedido() throws Exception {
		agregar(ana, martillo, "1");

		List<Object> resultados = aLaVez(() -> confirmar(ana, "85.5"), () -> confirmar(ana, "85.5"),
				() -> confirmar(ana, "85.5"));

		assertThat(resultados).extracting(resultado -> ((MvcTestResult) resultado).getResponse().getStatus())
				.containsExactlyInAnyOrder(201, 409, 409);
		assertThat(pedidoRepository.count()).isEqualTo(1);
		assertThat(stock(martillo)).isEqualTo(9.0);
	}

	// HU-12 RF-8, RF-17
	@Test
	void verElPedidoConSusDatos() throws Exception {
		agregar(ana, martillo, "1");
		Integer idPedido = confirmado(ana, "85.5");
		reloj.avanzar(Duration.ofMinutes(10));

		MvcTestResult pedido = get(PEDIDOS + "/{id}", ana, idPedido);

		assertThat(pedido).hasStatus(HttpStatus.OK);
		assertThat(pedido).bodyJson().extractingPath("$.idPedido").isEqualTo(idPedido);
		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_PAGO");
		assertThat(pedido).bodyJson().extractingPath("$.total").isEqualTo(85.5);
		assertThat(pedido).bodyJson().extractingPath("$.nombreRetira").isEqualTo("Ana Paz");
		assertThat(pedido).bodyJson().extractingPath("$.telefonoRetira").isEqualTo("70000000");
		assertThat(pedido).bodyJson().extractingPath("$.confirmado").isNotNull();
		assertThat(pedido).bodyJson().extractingPath("$.vence").isNotNull();
		assertThat(pedido).bodyJson().extractingPath("$.segundosParaPagar").isEqualTo(1200);
		assertThat(pedido).bodyJson().extractingPath("$.retiro.direccion").isEqualTo("Av. Principal 123, Montero");
	}

	// HU-12 RF-9
	@Test
	void cancelarDevuelveStockYLineasAlCarrito() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");

		MvcTestResult cancelado = cancelar(ana, idPedido);

		assertThat(cancelado).hasStatus(HttpStatus.OK);
		assertThat(cancelado).bodyJson().extractingPath("$.pedido.estado").isEqualTo("CANCELADO");
		assertThat(cancelado).bodyJson().extractingPath("$.pedido.segundosParaPagar").isEqualTo(0);
		assertThat(cancelado).bodyJson().extractingPath("$.lineasQueNoVolvieron").isEqualTo(0);
		assertThat(stock(martillo)).isEqualTo(10.0);
		MvcTestResult carrito = get(CARRITO, ana);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].idProducto").isEqualTo(martillo);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
	}

	// HU-12 RF-9, RF-10 (caso límite: el producto deja de venderse con el pedido pendiente)
	@Test
	void productoInactivoVuelveAlCarritoNoDisponible() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", martillo);

		assertThat(cancelar(ana, idPedido)).hasStatus(HttpStatus.OK);

		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].estado").isEqualTo("NO_DISPONIBLE");
	}

	// Caso límite: la cuenta se desactiva con el pedido pendiente
	@Test
	void unaCuentaDesactivadaTambienVence() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		assertThat(put("/api/usuarios/{id}/estado", admin, "{\"estado\":\"DESACTIVADA\"}", idAna))
				.hasStatus(HttpStatus.OK);
		reloj.avanzar(Duration.ofMinutes(30));

		vencimiento.vencer();

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.VENCIDO);
		assertThat(stock(martillo)).isEqualTo(10.0);
	}

	// Caso límite: un ajuste con el pedido pendiente escribe lo contado; la reserva ya se restó
	@Test
	void unAjusteConPedidoPendienteUsaLoContado() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		ajustarStock(martillo, "5");

		cancelar(ana, idPedido);

		assertThat(stock(martillo)).isEqualTo(7.0);
	}

	// HU-12 RF-10
	@Test
	void aLos30MinutosVenceYDevuelveTodo() throws Exception {
		agregar(ana, martillo, "2");
		agregar(ana, cable, "3.5");
		Integer idPedido = confirmado(ana, "213");
		reloj.avanzar(Duration.ofMinutes(30).minusSeconds(1));

		vencimiento.vencer();
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);

		reloj.avanzar(Duration.ofSeconds(1));
		vencimiento.vencer();

		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.VENCIDO);
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(stock(cable)).isEqualTo(100.0);
		MvcTestResult carrito = get(CARRITO, ana);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[*].cantidad").asArray().containsExactly(2.0, 3.5);
		assertThat(carrito).bodyJson().extractingPath("$.idPedidoPendiente").isNull();
	}

	// HU-12 RF-5, RF-10: aunque la tarea programada todavía no pasó, el pedido vencido no bloquea
	@Test
	void unPedidoVencidoNoBloqueaOtro() throws Exception {
		agregar(ana, martillo, "2");
		Integer vencido = confirmado(ana, "171");
		reloj.avanzar(Duration.ofMinutes(31));

		MvcTestResult otro = confirmar(ana, "171");

		assertThat(otro).hasStatus(HttpStatus.CREATED);
		assertThat(estado(vencido)).isEqualTo(EstadoPedido.VENCIDO);
		assertThat(stock(martillo)).isEqualTo(8.0);
	}

	// HU-12 RF-10: al abrir el pedido vencido, ya dice «Vencido»
	@Test
	void abrirElPedidoVencidoLoVence() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		reloj.avanzar(Duration.ofMinutes(31));

		MvcTestResult pedido = get(PEDIDOS + "/{id}", ana, idPedido);

		assertThat(pedido).bodyJson().extractingPath("$.estado").isEqualTo("VENCIDO");
		assertThat(pedido).bodyJson().extractingPath("$.segundosParaPagar").isEqualTo(0);
		assertThat(stock(martillo)).isEqualTo(10.0);
	}

	// HU-12 RF-11
	@Test
	void lasLineasVuelvenSumandoseAlCarrito() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		agregar(ana, martillo, "3");
		agregar(ana, cable, "1");

		cancelar(ana, idPedido);

		MvcTestResult carrito = get(CARRITO, ana);
		assertThat(carrito).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(2);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(5.0);
	}

	// HU-12 RF-12
	@Test
	void cancelarUnoNoPendienteNoTocaNada() throws Exception {
		agregar(ana, martillo, "2");
		Integer cancelado = confirmado(ana, "171");
		cancelar(ana, cancelado);

		MvcTestResult otraVez = cancelar(ana, cancelado);

		assertThat(otraVez).hasStatus(HttpStatus.CONFLICT);
		assertThat(otraVez).bodyJson().extractingPath("$.codigo").isEqualTo("PEDIDO_NO_PENDIENTE");
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);

		Integer vencido = confirmado(ana, "171");
		reloj.avanzar(Duration.ofMinutes(30));
		vencimiento.vencer();
		assertThat(cancelar(ana, vencido)).hasStatus(HttpStatus.CONFLICT);
		assertThat(stock(martillo)).isEqualTo(10.0);
	}

	// HU-12 RF-13
	@Test
	void elPersonalRecibe403() throws Exception {
		agregar(ana, martillo, "1");
		Integer idPedido = confirmado(ana, "85.5");
		String empleado = cuentaCon("emp.hu12@mail.com", "EMPLEADO");
		String contador = cuentaCon("con.hu12@mail.com", "CONTADOR");

		for (String personal : List.of(admin, empleado, contador)) {
			assertThat(get(PEDIDOS + "/retiro", personal)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(confirmar(personal, "85.5")).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(get(PEDIDOS + "/{id}", personal, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(cancelar(personal, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(mvc.get().uri(PEDIDOS + "/{id}", idPedido).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// HU-12 RF-13
	@Test
	void elPedidoDeOtroClienteDa404() throws Exception {
		agregar(ana, martillo, "1");
		Integer idPedido = confirmado(ana, "85.5");

		MvcTestResult ajeno = get(PEDIDOS + "/{id}", beto, idPedido);

		assertThat(ajeno).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(ajeno).bodyJson().extractingPath("$.codigo").isEqualTo("PEDIDO_NO_ENCONTRADO");
		assertThat(cancelar(beto, idPedido)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(PEDIDOS + "/{id}", ana, 999_999)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// HU-12 RF-14
	@Test
	void elHistorialMuestraReservaYLiberacionConElPedido() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		cancelar(ana, idPedido);

		MvcTestResult historial = get("/api/inventario/{id}/movimientos", admin, martillo);

		assertThat(historial).bodyJson().extractingPath("$.contenido[0].tipo").isEqualTo("LIBERACION");
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].cantidad").isEqualTo(2.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].stockResultante").isEqualTo(10.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].idPedido").isEqualTo(idPedido);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].usuario").isEqualTo("Ana Paz");
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].tipo").isEqualTo("RESERVA");
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].cantidad").isEqualTo(-2.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].stockResultante").isEqualTo(8.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].idPedido").isEqualTo(idPedido);
		assertThat(historial).bodyJson().extractingPath("$.contenido[2].idPedido").isNull();
	}

	// HU-12 RF-15
	@Test
	void unTotalDistintoNoConfirma() throws Exception {
		agregar(ana, martillo, "2");
		assertThat(put("/api/gestion/productos/{id}", admin, """
				{"nombre":"Martillo","idCategoria":%d,"unidad":"UNIDAD","precio":90}""".formatted(herramientas),
				martillo)).hasStatus(HttpStatus.OK);

		MvcTestResult rechazo = confirmar(ana, "171");

		assertThat(rechazo).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazo).bodyJson().extractingPath("$.codigo").isEqualTo("TOTAL_CAMBIADO");
		assertThat(rechazo).bodyJson().extractingPath("$.total").isEqualTo(180.0);
		assertThat(pedidoRepository.count()).isZero();
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(confirmar(ana, "180.00")).hasStatus(HttpStatus.CREATED);
	}

	// HU-12 RF-16
	@Test
	void alVolverRespetaLos999YLas50Lineas() throws Exception {
		post("/api/inventario/{id}/entradas", admin, "{\"cantidad\":1000}", cable);
		agregar(ana, cable, "600");
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "7371");
		agregar(ana, cable, "500");
		for (int i = 2; i <= 50; i++) {
			agregar(ana, producto("Llave " + i, "UNIDAD", "10", "5"), "1");
		}

		MvcTestResult cancelado = cancelar(ana, idPedido);

		assertThat(cancelado).bodyJson().extractingPath("$.lineasQueNoVolvieron").isEqualTo(1);
		MvcTestResult carrito = get(CARRITO, ana);
		assertThat(carrito).bodyJson().extractingPath("$.cantidadLineas").isEqualTo(50);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(999.0);
		assertThat(carrito).bodyJson().extractingPath("$.lineas[*].idProducto").asArray().doesNotContain(martillo);
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(stock(cable)).isEqualTo(1100.0);
	}

	// HU-12 RF-17
	@Test
	void elRetiroSaleDeLaConfiguracion() {
		MvcTestResult retiro = get(PEDIDOS + "/retiro", ana);

		assertThat(retiro).hasStatus(HttpStatus.OK);
		assertThat(retiro).bodyJson().extractingPath("$.direccion").isEqualTo("Av. Principal 123, Montero");
		assertThat(retiro).bodyJson().extractingPath("$.horario").isEqualTo("Lunes a sábado, de 8:00 a 18:00");
	}

	// HU-12 RF-17
	@Test
	void sinConfigurarVieneVacio() {
		assertThat(RetiroResponse.de(null, null)).isEqualTo(new RetiroResponse(null, null));
		assertThat(RetiroResponse.de("  ", "")).isEqualTo(new RetiroResponse(null, null));
		assertThat(RetiroResponse.de(" Av. Principal ", null))
				.isEqualTo(new RetiroResponse("Av. Principal", null));
	}

	// Caso límite: cancelar justo cuando vence; gana uno y el stock vuelve una sola vez (RF-12)
	@Test
	void cancelarJustoCuandoVence() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		reloj.avanzar(Duration.ofMinutes(30));

		aLaVez(() -> cancelar(ana, idPedido), () -> {
			vencimiento.vencer();
			return null;
		});

		assertThat(estado(idPedido)).isIn(EstadoPedido.CANCELADO, EstadoPedido.VENCIDO);
		assertThat(stock(martillo)).isEqualTo(10.0);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
		assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.LIBERACION)
				.hasSize(1);
	}

	// Revisión de HU-12 (/code-review): una entrada pudo dejar el stock en el máximo mientras el pedido esperaba; al
	// devolver, queda en el máximo y el pedido igual se cancela (antes fallaba al guardar y quedaba pendiente)
	@Test
	void devolverNoPasaDelStockMaximo() throws Exception {
		agregar(ana, martillo, "2");
		Integer idPedido = confirmado(ana, "171");
		// Por unidad: el máximo sin decimales
		ajustarStock(martillo, "9999999");

		MvcTestResult cancelado = cancelar(ana, idPedido);

		assertThat(cancelado).hasStatus(HttpStatus.OK);
		assertThat(stock(martillo)).isEqualTo(9_999_999.0);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.CANCELADO);
		assertThat(get(CARRITO, ana)).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
	}

	// Revisión de HU-12 (/code-review): las escrituras del carrito no vencen; uno vencido no se informa como pendiente
	@Test
	void elCarritoNoDicePendienteUnPedidoQueYaVencio() throws Exception {
		agregar(ana, martillo, "1");
		confirmado(ana, "85.5");
		reloj.avanzar(Duration.ofMinutes(31));

		MvcTestResult agregado = post(CARRITO + "/lineas", ana, "{\"idProducto\":%d,\"cantidad\":1}".formatted(cable));

		assertThat(agregado).bodyJson().extractingPath("$.idPedidoPendiente").isNull();
	}

	// Caso límite: datos basura en la dirección
	@Test
	void idDePedidoBasuraDa404() {
		assertThat(get(PEDIDOS + "/abc", ana)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(PEDIDOS + "/99999999999999999999", ana)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(PEDIDOS + "/abc/cancelacion", ana, "")).hasStatus(HttpStatus.NOT_FOUND);
	}

	static List<Object> aLaVez(Callable<?>... pedidos) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(pedidos.length);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<Object>> futuros = Arrays.stream(pedidos).map(pedido -> hilos.submit(() -> {
				largada.await();
				return (Object) pedido.call();
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

	// Arranca en la hora real y avanza solo cuando la prueba lo pide. En microsegundos, como el de la aplicación
	// (ClockConfig)
	static class RelojDePrueba extends Clock {

		private static final ZoneId ZONA = ZoneId.of("America/La_Paz");

		private volatile Instant ahora = horaReal();

		void avanzar(Duration tiempo) {
			ahora = ahora.plus(tiempo);
		}

		void reiniciar() {
			ahora = horaReal();
		}

		private static Instant horaReal() {
			return Instant.now().truncatedTo(ChronoUnit.MICROS);
		}

		@Override
		public ZoneId getZone() {
			return ZONA;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return Clock.fixed(ahora, zona);
		}

		@Override
		public Instant instant() {
			return ahora;
		}
	}
}
