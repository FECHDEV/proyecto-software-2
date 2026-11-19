package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.ReporteCompleto;
import bo.edu.uagrm.tienda.service.ReportesService;

// HU-18 RF-23 a RF-26 (ampliación): «Clientes que compran», /api/reportes/compradores. Como ReportesIntegracionTest,
// los pedidos se arman con las entidades en fechas de 2025, lejos de los de las otras pruebas
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesCompradoresIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String COMPRADORES = "/api/reportes/compradores";
	private static final String OCTUBRE = "?desde=2025-10-01&hasta=2025-10-22";
	private static final String SUFIJO = ".hu18c@mail.com";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private PedidoRepository pedidoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ReportesService reportesService;

	private final List<String> correos = new ArrayList<>();
	private String admin;
	private String contador;
	private Usuario ana;
	private Usuario beto;
	private Usuario personal;
	private Producto pieza;

	@BeforeEach
	void preparar() throws Exception {
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		contador = cuentaCon("conta" + SUFIJO, "CONTADOR");
		ana = cliente("ana", TipoCliente.PUBLICO_GENERAL);
		beto = cliente("beto", TipoCliente.CONTRATISTA);
		personal = usuarioRepository.findByCorreo(ADMIN).orElseThrow();
		Categoria herramientas = categoriaRepository.save(Categoria.crear("Herramientas", null));
		// Bs 100 la unidad: cada pedido vale 100 × la cantidad
		pieza = productoRepository.save(Producto.crear("Pieza", null, null, new BigDecimal("100"), UnidadVenta.UNIDAD,
				herramientas, List.of(), LocalDateTime.of(2025, 1, 1, 9, 0)));
	}

	@AfterEach
	void limpiar() {
		pedidoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		usuarioRepository.findAll().stream().filter(usuario -> usuario.getCorreo().endsWith(SUFIJO))
				.forEach(usuarioRepository::delete);
		correos.clear();
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
				.content("{\"nombre\":\"Carla\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(correo))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		if (rol != null) {
			Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
			assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
					.content("{\"rol\":\"%s\"}".formatted(rol)).exchange()).hasStatus(HttpStatus.OK);
		}
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private Usuario cliente(String nombre, TipoCliente tipo) {
		Usuario usuario = Usuario.registrarCliente(nombre, "Paz", nombre + SUFIJO, "x", "70000000",
				LocalDateTime.of(2025, 1, 1, 9, 0));
		usuario.cambiarTipo(tipo);
		return usuarioRepository.save(usuario);
	}

	// Un pedido en línea de 100 × piezas, pagado a esa hora
	private Pedido enLinea(Usuario cliente, LocalDateTime pago, int piezas) {
		Pedido pedido = Pedido.confirmar(cliente, "Ana Paz", "70000000", null, pago.minusMinutes(5),
				pago.plusMinutes(25));
		pedido.agregarLinea(pieza, new BigDecimal(piezas), PrecioDeVenta.sinDescuento(pieza.getPrecio()));
		pedido.pagar(pago);
		return pedidoRepository.save(pedido);
	}

	// Una venta del mostrador en efectivo de 100 × piezas; sin comprador, el consumidor final
	private Pedido enTienda(Usuario comprador, LocalDateTime pago, int piezas) {
		Pedido venta = Pedido.enTienda(comprador, personal, MedioPago.EFECTIVO, new BigDecimal("100000"),
				pago.minusMinutes(1), Duration.ofMinutes(10));
		venta.agregarLinea(pieza, new BigDecimal(piezas), PrecioDeVenta.sinDescuento(pieza.getPrecio()));
		venta.cobrarEnTienda(pago);
		return pedidoRepository.save(venta);
	}

	private static LocalDateTime dia(int mes, int dia, int hora, int minuto) {
		return LocalDateTime.of(2025, mes, dia, hora, minuto);
	}

	private MvcTestResult compradores(String token, String consulta) {
		return mvc.get().uri(COMPRADORES + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private List<String> correos(MvcTestResult resultado) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$.contenido[*].correo");
	}

	// HU-18 RF-23 a RF-25, escenario «Los mejores clientes del mes»
	@Test
	void compradoresConSusCifras() throws Exception {
		enLinea(ana, dia(10, 1, 10, 0), 1);
		enLinea(ana, dia(10, 11, 10, 0), 2);
		enLinea(ana, dia(10, 21, 10, 0), 3);
		enTienda(beto, dia(10, 5, 12, 0), 8);
		enTienda(null, dia(10, 6, 12, 0), 1);

		MvcTestResult resultado = compradores(contador, OCTUBRE);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(correos(resultado)).containsExactly("beto" + SUFIJO, "ana" + SUFIJO);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].compras").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].total").isEqualTo(800.0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].diasEntreCompras").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].enLinea").isEqualTo(0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].enTienda").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].tipo").isEqualTo("CONTRATISTA");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].telefono").isEqualTo("70000000");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].compras").isEqualTo(3);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].total").isEqualTo(600.0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].promedio").isEqualTo(200.0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].primera").isEqualTo("2025-10-01T10:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].ultima").isEqualTo("2025-10-21T10:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].diasEntreCompras").isEqualTo(10.0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].enLinea").isEqualTo(3);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].activa").isEqualTo(true);
		// El consumidor final no tiene cuenta: no entra
		assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(2);
	}

	// HU-18 RF-23 con RF-4: solo las ventas del período, por la fecha del pago
	@Test
	void soloCuentanLasVentasDelPeriodo() throws Exception {
		Pedido aDevolver = Pedido.confirmar(ana, "Ana Paz", "70000000", null, dia(10, 3, 9, 0), dia(10, 3, 9, 30));
		aDevolver.agregarLinea(pieza, BigDecimal.ONE, PrecioDeVenta.sinDescuento(pieza.getPrecio()));
		aDevolver.vencer(dia(10, 3, 9, 30));
		aDevolver.marcarPagoADevolver(dia(10, 3, 10, 0));
		pedidoRepository.save(aDevolver);
		Pedido anulada = enTienda(ana, dia(10, 4, 10, 0), 1);
		anulada.anular(personal, "Error", dia(10, 4, 10, 5));
		pedidoRepository.save(anulada);
		Pedido pendiente = Pedido.confirmar(ana, "Ana Paz", "70000000", null, dia(10, 5, 9, 0), dia(10, 5, 9, 30));
		pendiente.agregarLinea(pieza, BigDecimal.ONE, PrecioDeVenta.sinDescuento(pieza.getPrecio()));
		pedidoRepository.save(pendiente);
		// El último día hasta las 23:59 cuenta; el siguiente, no
		enLinea(beto, dia(10, 22, 23, 59), 1);
		enLinea(beto, dia(10, 23, 0, 0), 1);
		// Confirmado en septiembre, pagado en octubre: cuenta en octubre
		Pedido deSeptiembre = Pedido.confirmar(beto, "Beto Paz", "70000000", null, dia(9, 30, 23, 50),
				dia(10, 1, 0, 20));
		deSeptiembre.agregarLinea(pieza, BigDecimal.ONE, PrecioDeVenta.sinDescuento(pieza.getPrecio()));
		deSeptiembre.pagar(dia(10, 1, 0, 5));
		pedidoRepository.save(deSeptiembre);

		MvcTestResult resultado = compradores(contador, OCTUBRE);

		assertThat(correos(resultado)).containsExactly("beto" + SUFIJO);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].compras").isEqualTo(2);
	}

	// HU-18 RF-24: con una compra no hay «cada cuánto»; dos el mismo día dan 0
	@Test
	void unaSolaCompraYElMismoDia() throws Exception {
		enLinea(ana, dia(10, 7, 9, 0), 1);
		enLinea(ana, dia(10, 7, 18, 0), 1);
		enLinea(beto, dia(10, 8, 9, 0), 1);

		MvcTestResult resultado = compradores(contador, OCTUBRE);

		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].correo").isEqualTo("ana" + SUFIJO);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].diasEntreCompras").isEqualTo(0.0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].diasEntreCompras").isNull();
	}

	// HU-18 RF-25: del mayor total al menor; a igual total, más compras primero
	@Test
	void ordenaPorTotalYAIgualTotalPorCompras() throws Exception {
		enLinea(ana, dia(10, 2, 10, 0), 4);
		enLinea(beto, dia(10, 3, 10, 0), 2);
		enLinea(beto, dia(10, 4, 10, 0), 2);

		assertThat(correos(compradores(contador, OCTUBRE))).containsExactly("beto" + SUFIJO, "ana" + SUFIJO);
	}

	// HU-18 RF-25, escenario «Solo el mostrador»: el canal cuenta solo sus compras; el tipo elige las cuentas
	@Test
	void filtraPorTipoYPorCanal() throws Exception {
		enLinea(ana, dia(10, 2, 10, 0), 6);
		enTienda(ana, dia(10, 3, 10, 0), 1);
		enLinea(beto, dia(10, 4, 10, 0), 1);

		MvcTestResult enLaTienda = compradores(contador, OCTUBRE + "&canal=TIENDA");
		assertThat(correos(enLaTienda)).containsExactly("ana" + SUFIJO);
		assertThat(enLaTienda).bodyJson().extractingPath("$.contenido[0].compras").isEqualTo(1);
		assertThat(enLaTienda).bodyJson().extractingPath("$.contenido[0].total").isEqualTo(100.0);
		assertThat(enLaTienda).bodyJson().extractingPath("$.contenido[0].enLinea").isEqualTo(0);

		assertThat(correos(compradores(contador, OCTUBRE + "&tipo=CONTRATISTA"))).containsExactly("beto" + SUFIJO);
		MvcTestResult losDos = compradores(contador, OCTUBRE);
		assertThat(losDos).bodyJson().extractingPath("$.contenido[0].enLinea").isEqualTo(1);
		assertThat(losDos).bodyJson().extractingPath("$.contenido[0].enTienda").isEqualTo(1);
	}

	// HU-18 RF-23: la lista suma lo mismo que el resumen, menos lo vendido al consumidor final
	@Test
	void laListaCuadraConElResumen() throws Exception {
		enLinea(ana, dia(10, 2, 10, 0), 3);
		enTienda(beto, dia(10, 3, 10, 0), 2);
		enTienda(null, dia(10, 4, 10, 0), 1);

		MvcTestResult lista = compradores(contador, OCTUBRE);
		List<Double> totales = JsonPath.read(lista.getResponse().getContentAsString(), "$.contenido[*].total");
		MvcTestResult resumen = mvc.get().uri("/api/reportes/ventas" + OCTUBRE)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + contador).exchange();
		Double ingresos = JsonPath.read(resumen.getResponse().getContentAsString(), "$.actual.ingresos");

		assertThat(totales.stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(ingresos - 100.0);
	}

	// HU-18 RF-23 (P3): una cuenta desactivada hoy sigue saliendo, marcada
	@Test
	void laCuentaDesactivadaSigueSaliendo() throws Exception {
		enLinea(ana, dia(10, 2, 10, 0), 1);
		ana.desactivar();
		usuarioRepository.save(ana);

		MvcTestResult resultado = compradores(contador, OCTUBRE);

		assertThat(correos(resultado)).containsExactly("ana" + SUFIJO);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].activa").isEqualTo(false);
	}

	// HU-18 RF-25: de a 10 por página; una página imposible da la primera o la última
	@Test
	void paginaDeA10() throws Exception {
		for (int i = 0; i < 11; i++) {
			enLinea(cliente("c" + i, TipoCliente.PUBLICO_GENERAL), dia(10, 2, 10, i), 11 - i);
		}

		assertThat(compradores(contador, OCTUBRE + "&pagina=1")).bodyJson().extractingPath("$.contenido.length()")
				.isEqualTo(1);
		assertThat(compradores(contador, OCTUBRE + "&pagina=-1")).bodyJson().extractingPath("$.pagina").isEqualTo(0);
		assertThat(compradores(contador, OCTUBRE + "&pagina=zz")).bodyJson().extractingPath("$.pagina").isEqualTo(0);
		assertThat(compradores(contador, OCTUBRE + "&pagina=99")).bodyJson().extractingPath("$.pagina").isEqualTo(1);
	}

	// HU-18 RF-26: el reporte que se exporta los trae todos, sin paginar, en el orden de la pantalla
	@Test
	void completoTraeTodosLosQueCompranSinPaginar() throws Exception {
		for (int i = 0; i < 12; i++) {
			enLinea(cliente("c" + i, TipoCliente.PUBLICO_GENERAL), dia(10, 2, 10, i), 12 - i);
		}

		ReporteCompleto completo = reportesService.completo("2025-10-01", "2025-10-22", null, null, null,
				personal.getIdUsuario());

		assertThat(completo.clientesQueCompran()).hasSize(12);
		assertThat(completo.clientesQueCompran().get(0).correo()).isEqualTo("c0" + SUFIJO);
		assertThat(completo.clientesQueCompran().get(11).correo()).isEqualTo("c11" + SUFIJO);
	}

	// HU-18 RF-3: un período que no se entiende es 400, como en las otras listas
	@Test
	void periodoInvalidoEs400() throws Exception {
		MvcTestResult resultado = compradores(contador, "?desde=xx");

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("PERIODO_INVALIDO");
	}

	// HU-18 RF-1: solo el Contador y el Administrador
	@Test
	void soloContadorYAdministrador() throws Exception {
		String empleado = cuentaCon("emp" + SUFIJO, "EMPLEADO");
		String clienteToken = cuentaCon("cli" + SUFIJO, null);

		assertThat(compradores(empleado, OCTUBRE)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(compradores(clienteToken, OCTUBRE)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(COMPRADORES + OCTUBRE).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(compradores(admin, OCTUBRE)).hasStatus(HttpStatus.OK);
	}

	// HU-18 RF-25: un tipo que no existe es 400; un canal que no existe, los dos
	@Test
	void tipoBasuraEs400YCanalBasuraSonLosDos() throws Exception {
		enLinea(ana, dia(10, 2, 10, 0), 1);
		enTienda(beto, dia(10, 3, 10, 0), 1);

		assertThat(compradores(contador, OCTUBRE + "&tipo=zz")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(compradores(contador, OCTUBRE + "&canal=zz")).bodyJson().extractingPath("$.totalElementos")
				.isEqualTo(2);
	}
}
