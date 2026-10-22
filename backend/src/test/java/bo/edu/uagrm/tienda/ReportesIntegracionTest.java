package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-18: el resumen de ventas de /api/reportes/ventas, de punta a punta. Los pedidos se arman con las entidades, en
// fechas de 2025: así ningún pedido de las otras pruebas (con la hora real) cae en el período
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String VENTAS = "/api/reportes/ventas";
	private static final List<String> CORREOS = List.of("ana.hu18@mail.com", "beto.hu18@mail.com",
			"emp.hu18@mail.com", "conta.hu18@mail.com", "cli.hu18@mail.com");
	// 01/10/2025 es miércoles
	private static final String OCTUBRE = "?desde=2025-10-01&hasta=2025-10-22";

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

	private String admin;
	private String contador;
	private Usuario ana;
	private Usuario beto;
	private Usuario personal;
	private Categoria herramientas;
	private Categoria jardin;
	private Producto pieza;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		contador = cuentaCon("conta.hu18@mail.com", "CONTADOR");
		ana = cliente("ana.hu18@mail.com", TipoCliente.PUBLICO_GENERAL);
		beto = cliente("beto.hu18@mail.com", TipoCliente.CONTRATISTA);
		personal = usuarioRepository.findByCorreo(ADMIN).orElseThrow();
		herramientas = categoriaRepository.save(Categoria.crear("Herramientas", null));
		jardin = categoriaRepository.save(Categoria.crear("Jardín", null));
		// 50 Bs la unidad: cada pedido vale 50 × la cantidad
		pieza = producto("Pieza", "50", null, herramientas);
	}

	@AfterEach
	void limpiar() {
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

	private Usuario cliente(String correo, TipoCliente tipo) {
		Usuario usuario = Usuario.registrarCliente("Cliente", correo.substring(0, 4), correo, "x", "70000000",
				LocalDateTime.of(2025, 1, 1, 9, 0));
		usuario.cambiarTipo(tipo);
		return usuarioRepository.save(usuario);
	}

	private Producto producto(String nombre, String precio, String costo, Categoria categoria) {
		Producto producto = Producto.crear(nombre, null, null, new BigDecimal(precio), UnidadVenta.UNIDAD, categoria,
				List.of(), LocalDateTime.of(2025, 1, 1, 9, 0));
		producto.cambiarCosto(costo == null ? null : new BigDecimal(costo));
		return productoRepository.save(producto);
	}

	// Pares producto, cantidad. Confirmado a la hora dada, todavía pendiente de pago
	private Pedido confirmado(Usuario cliente, LocalDateTime cuando, Object... lineas) {
		Pedido pedido = Pedido.confirmar(cliente, "Ana Paz", "70000000", null, cuando, cuando.plusMinutes(30));
		for (int i = 0; i < lineas.length; i += 2) {
			pedido.agregarLinea((Producto) lineas[i], new BigDecimal(lineas[i + 1].toString()));
		}
		return pedido;
	}

	private Pedido pagado(Usuario cliente, LocalDateTime pago, Object... lineas) {
		Pedido pedido = confirmado(cliente, pago.minusMinutes(5), lineas);
		pedido.pagar(pago);
		return pedidoRepository.save(pedido);
	}

	// Un pedido de 50 × piezas, pagado a esa hora
	private Pedido pagado(Usuario cliente, LocalDateTime pago, int piezas) {
		return pagado(cliente, pago, pieza, piezas);
	}

	private static LocalDateTime dia(int mes, int dia, int hora, int minuto) {
		return LocalDateTime.of(2025, mes, dia, hora, minuto);
	}

	private MvcTestResult ventas(String token, String consulta) {
		return mvc.get().uri(VENTAS + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private MvcTestResult perdidos(String consulta) {
		return mvc.get().uri("/api/reportes/perdidos/resumen" + consulta)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + contador).exchange();
	}

	private static List<Double> numeros(MvcTestResult resultado, String ruta) throws Exception {
		List<Object> crudos = JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
		List<Double> numeros = new ArrayList<>();
		crudos.forEach(crudo -> numeros.add(crudo == null ? null : ((Number) crudo).doubleValue()));
		return numeros;
	}

	// HU-18 RF-1
	@Test
	void soloContadorYAdministrador() throws Exception {
		assertThat(mvc.get().uri(VENTAS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(ventas(cuentaCon("emp.hu18@mail.com", "EMPLEADO"), "")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ventas(cuentaCon("cli.hu18@mail.com", null), "")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ventas(contador, "")).hasStatus(HttpStatus.OK);
		assertThat(ventas(admin, "")).hasStatus(HttpStatus.OK);
	}

	// HU-18 RF-3
	@Test
	void periodoInvalidoEs400() {
		for (String consulta : List.of("?desde=xx&hasta=2025-10-01", "?desde=2025-10-10&hasta=2025-10-01",
				"?desde=2025-10-01", "?desde=2024-01-01&hasta=2025-10-01", "?desde=2025-10-01&hasta=2999-01-01")) {
			MvcTestResult rechazado = ventas(contador, consulta);
			assertThat(rechazado).as(consulta).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(rechazado).bodyJson().extractingPath("$.codigo").isEqualTo("PERIODO_INVALIDO");
		}
		assertThat(ventas(contador, "?tipo=MAYORISTA")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// HU-18 RF-4: pagados y entregados en la fecha del pago; el último día entero entra y el siguiente no
	@Test
	void cuentaPagadosYEntregadosPorFechaDePago() throws Exception {
		pagado(ana, dia(10, 22, 23, 59), 1);
		Pedido entregado = pagado(ana, dia(10, 5, 10, 0), 2);
		entregado.entregar(personal, dia(10, 6, 10, 0));
		pedidoRepository.save(entregado);
		pagado(ana, dia(10, 23, 0, 0), 4);
		pagado(ana, dia(9, 30, 23, 59), 8);
		// Confirmado en septiembre, vencido y pagado tarde en octubre: cuenta en octubre
		Pedido tardio = confirmado(ana, dia(9, 30, 23, 50), pieza, 16);
		tardio.vencer(dia(10, 1, 0, 20));
		tardio.pagar(dia(10, 1, 8, 0));
		pedidoRepository.save(tardio);
		// Pendiente, cancelado y vencido no son ventas
		pedidoRepository.save(confirmado(ana, dia(10, 10, 9, 0), pieza, 32));
		Pedido cancelado = confirmado(ana, dia(10, 11, 9, 0), pieza, 64);
		cancelado.cancelar(dia(10, 11, 9, 5));
		pedidoRepository.save(cancelado);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).hasStatus(HttpStatus.OK);
		// 1 + 2 + 16 piezas
		assertThat(resumen).bodyJson().extractingPath("$.actual.ingresos").isEqualTo(950.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.pedidos").isEqualTo(3);
	}

	// HU-18 RF-4, RF-6: el pago a devolver no suma, va aparte
	@Test
	void pagoADevolverVaAparte() throws Exception {
		pagado(ana, dia(10, 2, 10, 0), 2);
		Pedido aDevolver = confirmado(ana, dia(10, 3, 9, 0), pieza, 1);
		aDevolver.vencer(dia(10, 3, 9, 30));
		aDevolver.marcarPagoADevolver(dia(10, 3, 10, 0));
		pedidoRepository.save(aDevolver);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.actual.ingresos").isEqualTo(100.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.devolverCantidad").isEqualTo(1);
		assertThat(resumen).bodyJson().extractingPath("$.actual.devolverImporte").isEqualTo(50.0);
	}

	// HU-18 RF-6, RF-2: el escenario de la historia, con el período que vuelve
	@Test
	void resumenDelEscenario() throws Exception {
		pagado(ana, dia(10, 2, 10, 0), 2);
		pagado(ana, dia(10, 9, 10, 0), 4);
		pagado(beto, dia(10, 20, 10, 0), 6);
		Pedido aDevolver = confirmado(ana, dia(10, 3, 9, 0), pieza, 1);
		aDevolver.vencer(dia(10, 3, 9, 30));
		aDevolver.marcarPagoADevolver(dia(10, 3, 10, 0));
		pedidoRepository.save(aDevolver);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.periodo.desde").isEqualTo("2025-10-01");
		assertThat(resumen).bodyJson().extractingPath("$.periodo.hasta").isEqualTo("2025-10-22");
		assertThat(resumen).bodyJson().extractingPath("$.periodo.anteriorDesde").isEqualTo("2025-09-09");
		assertThat(resumen).bodyJson().extractingPath("$.periodo.anteriorHasta").isEqualTo("2025-09-30");
		assertThat(resumen).bodyJson().extractingPath("$.periodo.agrupado").isEqualTo("DIA");
		assertThat(resumen).bodyJson().extractingPath("$.actual.ingresos").isEqualTo(600.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.pedidos").isEqualTo(3);
		assertThat(resumen).bodyJson().extractingPath("$.actual.promedio").isEqualTo(200.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.devolverCantidad").isEqualTo(1);
		assertThat(resumen).bodyJson().extractingPath("$.actual.devolverImporte").isEqualTo(50.0);
	}

	// HU-18 RF-7: el período anterior de igual largo
	@Test
	void comparaConElAnterior() throws Exception {
		pagado(ana, dia(10, 2, 10, 0), 2);
		pagado(ana, dia(9, 9, 0, 0), 3);
		pagado(ana, dia(9, 30, 23, 59), 3);
		pagado(ana, dia(9, 8, 23, 59), 50);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.anterior.ingresos").isEqualTo(300.0);
		assertThat(resumen).bodyJson().extractingPath("$.anterior.pedidos").isEqualTo(2);
		assertThat(resumen).bodyJson().extractingPath("$.anterior.promedio").isEqualTo(150.0);
	}

	// HU-18 RF-8, RF-20: el escenario del margen; un costo mayor que el precio da un margen negativo
	@Test
	void margenSoloConCosto() throws Exception {
		Producto martillo = producto("Martillo", "85.50", "60", herramientas);
		Producto pinza = producto("Pinza", "40", null, herramientas);
		pagado(ana, dia(10, 2, 10, 0), martillo, 2, pinza, 1);
		martillo.cambiarCosto(new BigDecimal("70"));
		productoRepository.save(martillo);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.actual.ingresos").isEqualTo(211.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.margen").isEqualTo(51.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.margenPorcentaje").isEqualTo(29.8);
		assertThat(resumen).bodyJson().extractingPath("$.actual.vendidoSinCosto").isEqualTo(40.0);

		Producto caro = producto("Caro", "10", "15", herramientas);
		pagado(ana, dia(9, 20, 10, 0), caro, 3);
		MvcTestResult anterior = ventas(contador, OCTUBRE);
		assertThat(anterior).bodyJson().extractingPath("$.anterior.margen").isEqualTo(-15.0);
		assertThat(anterior).bodyJson().extractingPath("$.anterior.margenPorcentaje").isEqualTo(-50.0);
	}

	// HU-18 RF-6, RF-8, RF-22: sin ventas no se divide por cero
	@Test
	void sinVentasNoDivide() throws Exception {
		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).hasStatus(HttpStatus.OK);
		assertThat(resumen).bodyJson().extractingPath("$.actual.ingresos").isEqualTo(0.0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.pedidos").isEqualTo(0);
		assertThat(resumen).bodyJson().extractingPath("$.actual.promedio").isNull();
		assertThat(resumen).bodyJson().extractingPath("$.actual.margen").isNull();
		assertThat(resumen).bodyJson().extractingPath("$.actual.margenPorcentaje").isNull();
		assertThat(perdidos(OCTUBRE)).bodyJson().extractingPath("$.proporcion").isNull();
	}

	// HU-18 RF-9
	@Test
	void porFechaIncluyeLosDiasSinVentas() throws Exception {
		pagado(ana, dia(10, 2, 10, 0), 2);
		pagado(ana, dia(10, 2, 18, 0), 1);
		pagado(ana, dia(10, 22, 23, 0), 4);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.porFecha.length()").isEqualTo(22);
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[0].fecha").isEqualTo("2025-10-01");
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[0].ingresos").isEqualTo(0.0);
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[1].ingresos").isEqualTo(150.0);
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[1].pedidos").isEqualTo(2);
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[21].fecha").isEqualTo("2025-10-22");
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[21].ingresos").isEqualTo(200.0);
	}

	// HU-18 RF-9: un año entero son 13 meses como máximo
	@Test
	void porMesSiPasaDe62Dias() throws Exception {
		pagado(ana, dia(1, 15, 10, 0), 2);
		pagado(ana, dia(10, 2, 10, 0), 1);

		MvcTestResult resumen = ventas(contador, "?desde=2024-10-23&hasta=2025-10-22");

		assertThat(resumen).bodyJson().extractingPath("$.periodo.agrupado").isEqualTo("MES");
		assertThat(resumen).bodyJson().extractingPath("$.porFecha.length()").isEqualTo(13);
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[0].fecha").isEqualTo("2024-10");
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[3].fecha").isEqualTo("2025-01");
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[3].ingresos").isEqualTo(100.0);
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[12].fecha").isEqualTo("2025-10");
		assertThat(resumen).bodyJson().extractingPath("$.porFecha[12].ingresos").isEqualTo(50.0);
	}

	// HU-18 RF-10: lunes a domingo y las 24 horas, en la hora guardada (la de Bolivia)
	@Test
	void porDiaDeSemanaYHora() throws Exception {
		// Miércoles 1 a las 8 y a las 8:59; sábado 4 a las 23
		pagado(ana, dia(10, 1, 8, 0), 1);
		pagado(ana, dia(10, 1, 8, 59), 2);
		pagado(ana, dia(10, 4, 23, 0), 4);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.porDiaSemana[*].dia").asArray().containsExactly(1, 2, 3, 4, 5,
				6, 7);
		assertThat(numeros(resumen, "$.porDiaSemana[*].ingresos")).containsExactly(0.0, 0.0, 150.0, 0.0, 0.0, 200.0,
				0.0);
		assertThat(resumen).bodyJson().extractingPath("$.porHora.length()").isEqualTo(24);
		assertThat(resumen).bodyJson().extractingPath("$.porHora[8].hora").isEqualTo(8);
		assertThat(resumen).bodyJson().extractingPath("$.porHora[8].pedidos").isEqualTo(2);
		assertThat(resumen).bodyJson().extractingPath("$.porHora[23].ingresos").isEqualTo(200.0);
	}

	// HU-18 RF-14: la categoría actual del producto, también si está inactivo; de mayor a menor
	@Test
	void porCategoriaActual() throws Exception {
		Producto pala = producto("Pala", "100", "60", herramientas);
		Producto manguera = producto("Manguera", "30", null, jardin);
		pagado(ana, dia(10, 2, 10, 0), pala, 1, manguera, 1);
		pagado(ana, dia(10, 3, 10, 0), manguera, 1);
		// Se vendió en Herramientas y después pasó a Jardín, ya inactiva
		pala.editar("Pala", null, null, new BigDecimal("100"), UnidadVenta.UNIDAD, jardin, List.of());
		pala.desactivar();
		productoRepository.save(pala);
		pagado(ana, dia(10, 4, 10, 0), 1);

		MvcTestResult resumen = ventas(contador, OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.porCategoria[*].categoria").asArray()
				.containsExactly("Jardín", "Herramientas");
		assertThat(numeros(resumen, "$.porCategoria[*].ingresos")).containsExactly(160.0, 50.0);
		assertThat(resumen).bodyJson().extractingPath("$.porCategoria[0].pedidos").isEqualTo(2);
		assertThat(resumen).bodyJson().extractingPath("$.porCategoria[0].margen").isEqualTo(40.0);
		assertThat(resumen).bodyJson().extractingPath("$.porCategoria[1].margen").isNull();
	}

	// HU-18 RF-5: el tipo limita el resumen, las fechas, las categorías y los perdidos
	@Test
	void filtraPorTipo() throws Exception {
		pagado(ana, dia(10, 2, 10, 0), 1);
		pagado(beto, dia(10, 3, 10, 0), 2);
		pagado(beto, dia(10, 4, 10, 0), 2);
		Pedido vencido = confirmado(ana, dia(10, 5, 10, 0), pieza, 1);
		vencido.vencer(dia(10, 5, 10, 30));
		pedidoRepository.save(vencido);

		MvcTestResult contratistas = ventas(contador, OCTUBRE + "&tipo=CONTRATISTA");

		assertThat(contratistas).bodyJson().extractingPath("$.actual.ingresos").isEqualTo(200.0);
		assertThat(contratistas).bodyJson().extractingPath("$.actual.pedidos").isEqualTo(2);
		assertThat(contratistas).bodyJson().extractingPath("$.porFecha[1].ingresos").isEqualTo(0.0);
		assertThat(contratistas).bodyJson().extractingPath("$.porCategoria[0].ingresos").isEqualTo(200.0);
		assertThat(perdidos(OCTUBRE + "&tipo=CONTRATISTA")).bodyJson().extractingPath("$.cantidad").isEqualTo(0);
		assertThat(perdidos(OCTUBRE)).bodyJson().extractingPath("$.cantidad").isEqualTo(1);
	}

	// HU-18 RF-15: los cuatro tipos, también sin ventas, sin importar el filtro
	@Test
	void porTipoMuestraLosCuatro() throws Exception {
		pagado(ana, dia(10, 2, 10, 0), 1);
		pagado(beto, dia(10, 3, 10, 0), 2);
		pagado(beto, dia(10, 4, 10, 0), 4);

		MvcTestResult resumen = ventas(contador, OCTUBRE + "&tipo=TALLER");

		assertThat(resumen).bodyJson().extractingPath("$.porTipo[*].tipo").asArray()
				.containsExactly("PUBLICO_GENERAL", "CONTRATISTA", "TALLER", "EMPRESA");
		assertThat(numeros(resumen, "$.porTipo[*].ingresos")).containsExactly(50.0, 300.0, 0.0, 0.0);
		assertThat(numeros(resumen, "$.porTipo[*].promedio")).containsExactly(50.0, 150.0, null, null);
	}

	// HU-18 RF-17: los confirmados del período que vencieron o se cancelaron, y cuántos se vendieron (sin contar
	// los que todavía esperan el pago)
	@Test
	void perdidosYProporcion() throws Exception {
		for (int i = 0; i < 7; i++) {
			pagado(ana, dia(10, 2 + i, 10, 0), 1);
		}
		for (int i = 0; i < 2; i++) {
			Pedido vencido = confirmado(ana, dia(10, 10 + i, 10, 0), pieza, 2);
			vencido.vencer(dia(10, 10 + i, 10, 30));
			pedidoRepository.save(vencido);
		}
		Pedido cancelado = confirmado(ana, dia(10, 12, 10, 0), pieza, 3);
		cancelado.cancelar(dia(10, 12, 10, 5));
		pedidoRepository.save(cancelado);
		pedidoRepository.save(confirmado(ana, dia(10, 22, 23, 50), pieza, 1));
		// Pagado tarde y sin stock: se devuelve fuera del sistema, no cuenta en la proporción (/code-review)
		Pedido aDevolver = confirmado(ana, dia(10, 13, 10, 0), pieza, 1);
		aDevolver.vencer(dia(10, 13, 10, 30));
		aDevolver.marcarPagoADevolver(dia(10, 13, 11, 0));
		pedidoRepository.save(aDevolver);
		// Confirmado en septiembre: no es de este período
		Pedido viejo = confirmado(ana, dia(9, 30, 10, 0), pieza, 1);
		viejo.vencer(dia(9, 30, 10, 30));
		pedidoRepository.save(viejo);

		MvcTestResult resumen = perdidos(OCTUBRE);

		assertThat(resumen).bodyJson().extractingPath("$.cantidad").isEqualTo(3);
		assertThat(resumen).bodyJson().extractingPath("$.importe").isEqualTo(350.0);
		assertThat(resumen).bodyJson().extractingPath("$.confirmados").isEqualTo(10);
		assertThat(resumen).bodyJson().extractingPath("$.vendidos").isEqualTo(7);
		assertThat(resumen).bodyJson().extractingPath("$.proporcion").isEqualTo(70.0);
		// El resumen de ventas ya no los calcula (/code-review, ronda 2)
		assertThat(ventas(contador, OCTUBRE)).bodyJson().doesNotHavePath("$.perdidos");
	}
}
