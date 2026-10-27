package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.LineaCarritoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-08: la API de inventario de punta a punta, con los tres roles que la usan
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventarioIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String INVENTARIO = "/api/inventario";
	private static final List<String> CORREOS = List.of("emp.hu08@mail.com", "con.hu08@mail.com", "cli.hu08@mail.com");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private PedidoRepository pedidoRepository;

	@Autowired
	private LineaCarritoRepository lineaCarritoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	private String admin;
	private String empleado;
	private String contador;
	private String cliente;
	private Integer herramientas;
	private Integer jardin;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		empleado = cuentaCon("emp.hu08@mail.com", "EMPLEADO");
		contador = cuentaCon("con.hu08@mail.com", "CONTADOR");
		cliente = cuentaCon("cli.hu08@mail.com", null);
		herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		jardin = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Jardín\"}"), "$.idCategoria");
	}

	@AfterEach
	void limpiar() {
		movimientoRepository.deleteAll();
		lineaCarritoRepository.deleteAll();
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

	// Se registra como cliente y el Administrador le da el rol; el rol se lee de la base en cada petición
	private String cuentaCon(String correo, String rol) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Eva\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
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

	private Integer producto(String nombre, Integer idCategoria, String unidad) throws Exception {
		MvcTestResult creado = post("/api/gestion/productos", admin, """
				{"nombre":"%s","idCategoria":%d,"unidad":"%s","precio":10}""".formatted(nombre, idCategoria, unidad));
		assertThat(creado).hasStatus(HttpStatus.CREATED);
		return id(creado, "$.idProducto");
	}

	private MvcTestResult entrada(String token, Integer idProducto, String cantidad) {
		return post(INVENTARIO + "/{id}/entradas", token, "{\"cantidad\":%s}".formatted(cantidad), idProducto);
	}

	private MvcTestResult ajuste(String token, Integer idProducto, String contada, String motivo, String nota) {
		String cuerpo = nota == null ? "{\"contada\":%s,\"motivo\":\"%s\"}".formatted(contada, motivo)
				: "{\"contada\":%s,\"motivo\":\"%s\",\"nota\":\"%s\"}".formatted(contada, motivo, nota);
		return post(INVENTARIO + "/{id}/ajustes", token, cuerpo, idProducto);
	}

	private BigDecimal stock(Integer idProducto) throws Exception {
		MvcTestResult leido = get(INVENTARIO + "/{id}", admin, idProducto);
		return new BigDecimal(JsonPath.read(leido.getResponse().getContentAsString(), "$.stock").toString());
	}

	// HU-08 RF-1
	@Test
	void permisosPorRol() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");

		assertThat(mvc.get().uri(INVENTARIO).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(get(INVENTARIO, cliente)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(entrada(cliente, taladro, "1")).hasStatus(HttpStatus.FORBIDDEN);

		assertThat(get(INVENTARIO, contador)).hasStatus(HttpStatus.OK);
		assertThat(get(INVENTARIO + "/categorias", cliente)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(get(INVENTARIO + "/{id}/movimientos", contador, taladro)).hasStatus(HttpStatus.OK);
		assertThat(entrada(contador, taladro, "1")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ajuste(contador, taladro, "1", "CONTEO", null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(put(INVENTARIO + "/{id}/minimo", contador, "{\"minimo\":1}", taladro))
				.hasStatus(HttpStatus.FORBIDDEN);

		assertThat(entrada(empleado, taladro, "1")).hasStatus(HttpStatus.CREATED);
		assertThat(entrada(admin, taladro, "1")).hasStatus(HttpStatus.CREATED);
		assertThat(stock(taladro)).isEqualByComparingTo("2");
	}

	// HU-08 RF-2, RF-3, RF-4, RF-11
	@Test
	void entradaYAjusteActualizanElStockYElHistorial() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		assertThat(stock(taladro)).isEqualByComparingTo("0");

		MvcTestResult conEntrada = post(INVENTARIO + "/{id}/entradas", empleado,
				"{\"cantidad\":10,\"nota\":\" Factura 123 \"}", taladro);
		assertThat(conEntrada).hasStatus(HttpStatus.CREATED);
		assertThat(conEntrada).bodyJson().extractingPath("$.stock").isEqualTo(10.0);
		assertThat(ajuste(empleado, taladro, "8", "MERMA", null)).bodyJson().extractingPath("$.stock")
				.isEqualTo(8.0);

		MvcTestResult historial = get(INVENTARIO + "/{id}/movimientos", contador, taladro);
		assertThat(historial).bodyJson().extractingPath("$.contenido[*].tipo").asArray()
				.containsExactly("AJUSTE", "ENTRADA");
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].cantidad").isEqualTo(-2.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].stockResultante").isEqualTo(8.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].motivo").isEqualTo("MERMA");
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].nota").isEqualTo("Factura 123");
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].usuario").isEqualTo("Eva Paz");
	}

	// HU-08 RF-5, RF-6, RF-7: nada de esto cambia el stock
	@Test
	void cantidadesInvalidasSeRechazanSinCambiarNada() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		Integer manguera = producto("Manguera", jardin, "METRO");
		entrada(empleado, taladro, "5");

		for (String cantidad : List.of("0", "-1", "2.5", "100000", "1.234", "null")) {
			assertThat(entrada(empleado, taladro, cantidad)).as(cantidad).hasStatus(HttpStatus.BAD_REQUEST);
		}
		MvcTestResult decimales = entrada(empleado, taladro, "2.5");
		assertThat(decimales).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("cantidad");
		assertThat(ajuste(empleado, taladro, "-1", "CONTEO", null)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ajuste(empleado, taladro, "4", "OTRO", null)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ajuste(empleado, taladro, "4", "INVENTADO", null)).hasStatus(HttpStatus.BAD_REQUEST);
		MvcTestResult sinCambio = ajuste(empleado, taladro, "5", "CONTEO", null);
		assertThat(sinCambio).hasStatus(HttpStatus.CONFLICT);
		assertThat(sinCambio).bodyJson().extractingPath("$.codigo").isEqualTo("AJUSTE_SIN_CAMBIO");
		assertThat(stock(taladro)).isEqualByComparingTo("5");

		assertThat(entrada(empleado, manguera, "25.5")).hasStatus(HttpStatus.CREATED);
		assertThat(ajuste(empleado, taladro, "4", "OTRO", "Se usó en el taller")).hasStatus(HttpStatus.CREATED);
		assertThat(ajuste(empleado, taladro, "9999999", "CONTEO", null)).hasStatus(HttpStatus.CREATED);
		MvcTestResult demasiado = entrada(empleado, taladro, "1");
		assertThat(demasiado).hasStatus(HttpStatus.CONFLICT);
		assertThat(demasiado).bodyJson().extractingPath("$.codigo").isEqualTo("STOCK_FUERA_DE_RANGO");
	}

	// HU-08 RF-8: con el producto bloqueado, ninguna se pierde
	@Test
	void dosEntradasALaVezNoSePierden() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");

		List<MvcTestResult> resultados = aLaVez(() -> entrada(empleado, taladro, "5"),
				() -> entrada(admin, taladro, "5"));

		assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).hasStatus(HttpStatus.CREATED));
		assertThat(stock(taladro)).isEqualByComparingTo("10");
		assertThat(get(INVENTARIO + "/{id}/movimientos", admin, taladro)).bodyJson()
				.extractingPath("$.totalElementos").isEqualTo(2);
	}

	// HU-08 RF-8: el stock resultante de cada uno es coherente con el orden en que se aplicaron
	@Test
	void entradaYAjusteALaVezSeAplicanEnOrden() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		entrada(empleado, taladro, "10");

		List<MvcTestResult> resultados = aLaVez(() -> entrada(empleado, taladro, "5"),
				() -> ajuste(admin, taladro, "3", "CONTEO", null));

		assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).hasStatus(HttpStatus.CREATED));
		MvcTestResult historial = get(INVENTARIO + "/{id}/movimientos", admin, taladro);
		String cuerpo = historial.getResponse().getContentAsString();
		List<Number> resultantes = JsonPath.read(cuerpo, "$.contenido[*].stockResultante");
		List<Number> cantidades = JsonPath.read(cuerpo, "$.contenido[*].cantidad");
		// Del más antiguo al más reciente, cada stock resultante es el anterior más su cantidad
		BigDecimal anterior = BigDecimal.ZERO;
		for (int i = resultantes.size() - 1; i >= 0; i--) {
			BigDecimal cantidad = new BigDecimal(cantidades.get(i).toString());
			BigDecimal resultante = new BigDecimal(resultantes.get(i).toString());
			assertThat(resultante).isEqualByComparingTo(anterior.add(cantidad));
			anterior = resultante;
		}
		assertThat(stock(taladro)).isEqualByComparingTo(anterior);
	}

	// HU-08 RF-9, RF-10
	@Test
	void minimoYFiltroDeStockBajo() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		producto("Pala", jardin, "UNIDAD");
		entrada(empleado, taladro, "3");

		MvcTestResult conMinimo = put(INVENTARIO + "/{id}/minimo", empleado, "{\"minimo\":5}", taladro);
		assertThat(conMinimo).bodyJson().extractingPath("$.stockBajo").isEqualTo(true);
		assertThat(get(INVENTARIO + "?bajo=true", contador)).bodyJson().extractingPath("$.contenido[*].nombre")
				.asArray().containsExactly("Taladro");

		entrada(empleado, taladro, "10");
		assertThat(get(INVENTARIO + "?bajo=true", contador)).bodyJson().extractingPath("$.totalElementos")
				.isEqualTo(0);
		assertThat(put(INVENTARIO + "/{id}/minimo", empleado, "{\"minimo\":null}", taladro)).bodyJson()
				.extractingPath("$.stockMinimo").isNull();
		assertThat(put(INVENTARIO + "/{id}/minimo", empleado, "{\"minimo\":1.5}", taladro))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// HU-08 RF-10: por nombre, activos e inactivos, con texto, categoría, estado y páginas de 20
	@Test
	void listaPorNombreConFiltrosYPaginas() throws Exception {
		Integer azada = producto("Azada", jardin, "UNIDAD");
		put("/api/gestion/productos/{id}/activo", admin, "{\"activo\":false}", azada);
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		for (int i = 10; i < 29; i++) {
			producto("Clavo " + i, herramientas, "CAJA");
		}

		MvcTestResult primera = get(INVENTARIO, empleado);
		assertThat(primera).bodyJson().extractingPath("$.totalElementos").isEqualTo(21);
		assertThat(primera).bodyJson().extractingPath("$.contenido").asArray().hasSize(20);
		assertThat(primera).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Azada");
		assertThat(primera).bodyJson().extractingPath("$.contenido[0].activo").isEqualTo(false);
		assertThat(get(INVENTARIO + "?pagina=1", empleado)).bodyJson().extractingPath("$.contenido[*].nombre")
				.asArray().containsExactly("Taladro");
		assertThat(get(INVENTARIO + "?categoria=" + jardin + "&activo=false", empleado)).bodyJson()
				.extractingPath("$.contenido[*].nombre").asArray().containsExactly("Azada");
		assertThat(get(INVENTARIO + "?texto=her-%04d".formatted(taladro), empleado)).bodyJson()
				.extractingPath("$.contenido[*].nombre").asArray().containsExactly("Taladro");
	}

	// HU-08 RF-10: el filtro por categoría incluye las ocultas, porque el inventario lista sus productos
	@Test
	void categoriasDelFiltroIncluyenLasOcultas() {
		put("/api/gestion/categorias/{id}/visible", admin, "{\"visible\":false}", jardin);

		assertThat(get(INVENTARIO + "/categorias", contador)).bodyJson().extractingPath("$[*].nombre").asArray()
				.containsExactly("Herramientas", "Jardín");
	}

	// HU-08 RF-12: no hay ruta para cambiar ni borrar un movimiento
	@Test
	void noHayFormaDeEditarNiBorrarUnMovimiento() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		entrada(empleado, taladro, "5");
		Integer idMovimiento = id(get(INVENTARIO + "/{id}/movimientos", admin, taladro), "$.contenido[0].idMovimiento");

		assertThat(put(INVENTARIO + "/{id}/movimientos/{mov}", admin, "{\"cantidad\":1}", taladro, idMovimiento)
				.getResponse().getStatus()).isBetween(400, 499);
		assertThat(mvc.delete().uri(INVENTARIO + "/{id}/movimientos/{mov}", taladro, idMovimiento)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).exchange().getResponse().getStatus())
				.isBetween(400, 499);
		assertThat(stock(taladro)).isEqualByComparingTo("5");
	}

	// HU-08 RF-13
	@Test
	void cambiarAUnidadSinDecimalesConStockDecimalSeRechaza() throws Exception {
		Integer manguera = producto("Manguera", jardin, "METRO");
		entrada(empleado, manguera, "12.5");
		String comoUnidad = """
				{"nombre":"Manguera","idCategoria":%d,"unidad":"UNIDAD","precio":10}""".formatted(jardin);

		MvcTestResult rechazado = put("/api/gestion/productos/{id}", admin, comoUnidad, manguera);
		assertThat(rechazado).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazado).bodyJson().extractingPath("$.codigo").isEqualTo("UNIDAD_INCOMPATIBLE_CON_STOCK");

		ajuste(empleado, manguera, "12", "CONTEO", null);
		assertThat(put("/api/gestion/productos/{id}", admin, comoUnidad, manguera)).hasStatus(HttpStatus.OK);
	}

	// HU-08 RF-19: un pedido abierto con 2,5 m quedaría en «2,5 unidades»; uno entregado ya no cuenta
	@Test
	void cambiarAUnidadSinDecimalesConUnPedidoAbiertoDecimalSeRechaza() throws Exception {
		Integer manguera = producto("Manguera", jardin, "METRO");
		entrada(empleado, manguera, "12");
		pedidoEn(EstadoPedido.PAGADO, manguera, "2.5");
		String comoUnidad = """
				{"nombre":"Manguera","idCategoria":%d,"unidad":"UNIDAD","precio":10}""".formatted(jardin);

		MvcTestResult rechazado = put("/api/gestion/productos/{id}", admin, comoUnidad, manguera);
		assertThat(rechazado).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazado).bodyJson().extractingPath("$.codigo").isEqualTo("UNIDAD_INCOMPATIBLE_CON_STOCK");

		pedidoRepository.deleteAll();
		pedidoEn(EstadoPedido.ENTREGADO, manguera, "2.5");
		assertThat(put("/api/gestion/productos/{id}", admin, comoUnidad, manguera)).hasStatus(HttpStatus.OK);
	}

	// HU-08 RF-14
	@Test
	void productoInexistenteYDatosBasura() {
		MvcTestResult sinProducto = entrada(empleado, 999999, "1");
		assertThat(sinProducto).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(sinProducto).bodyJson().extractingPath("$.codigo").isEqualTo("PRODUCTO_NO_ENCONTRADO");
		assertThat(get(INVENTARIO + "/999999/movimientos", contador)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(INVENTARIO + "/abc", contador).getResponse().getStatus()).isBetween(400, 499);
		assertThat(get(INVENTARIO + "?pagina=abc&categoria=x", contador).getResponse().getStatus())
				.isBetween(400, 499);
	}

	// --- 27/10/2026: el ajuste cuenta el estante (RF-15 a RF-18) ---

	// Un pedido del cliente con este producto, en el estado pedido. Sin mover el stock: solo importa lo comprometido
	private void pedidoEn(EstadoPedido estado, Integer idProducto, String cantidad) {
		Usuario usuario = usuarioRepository.findByCorreo("cli.hu08@mail.com").orElseThrow();
		LocalDateTime ahora = LocalDateTime.now();
		Pedido pedido = Pedido.confirmar(usuario, "Eva Paz", "70000000", null, ahora, ahora.plusMinutes(30));
		pedido.agregarLinea(productoRepository.findById(idProducto.longValue()).orElseThrow(), new BigDecimal(cantidad));
		switch (estado) {
			case PENDIENTE_PAGO -> {
			}
			case PAGADO -> pedido.pagar(ahora);
			case ENTREGADO -> {
				pedido.pagar(ahora);
				pedido.entregar(usuario, ahora);
			}
			case CANCELADO -> pedido.cancelar(ahora);
			case VENCIDO -> pedido.vencer(ahora);
			case PAGO_A_DEVOLVER -> {
				pedido.cancelar(ahora);
				pedido.marcarPagoADevolver(ahora);
			}
		}
		pedidoRepository.save(pedido);
	}

	// El escenario de la historia: disponible 10, 3 en un pedido pendiente y 2 en uno pagado sin retirar
	private Integer conPedidosAbiertos() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		entrada(empleado, taladro, "10");
		pedidoEn(EstadoPedido.PENDIENTE_PAGO, taladro, "3");
		pedidoEn(EstadoPedido.PAGADO, taladro, "2");
		return taladro;
	}

	// HU-08 RF-15: los entregados, cancelados, vencidos y con el pago a devolver ya no están en el estante por un pedido
	@Test
	void loComprometidoSumaPendientesYPagadosSinEntregar() throws Exception {
		Integer taladro = conPedidosAbiertos();
		pedidoEn(EstadoPedido.ENTREGADO, taladro, "4");
		pedidoEn(EstadoPedido.CANCELADO, taladro, "1");
		pedidoEn(EstadoPedido.VENCIDO, taladro, "1");
		pedidoEn(EstadoPedido.PAGO_A_DEVOLVER, taladro, "1");

		MvcTestResult ficha = get(INVENTARIO + "/{id}", empleado, taladro);
		assertThat(ficha).bodyJson().extractingPath("$.stock").isEqualTo(10.0);
		assertThat(ficha).bodyJson().extractingPath("$.comprometido").isEqualTo(5.0);
	}

	// HU-08 RF-16
	@Test
	void laFichaYLaListaMuestranLoComprometido() throws Exception {
		Integer taladro = conPedidosAbiertos();
		producto("Azada", jardin, "UNIDAD");

		MvcTestResult lista = get(INVENTARIO, contador);
		assertThat(lista).bodyJson().extractingPath("$.contenido[*].nombre").asArray()
				.containsExactly("Azada", "Taladro");
		assertThat(lista).bodyJson().extractingPath("$.contenido[0].comprometido").isEqualTo(0.0);
		assertThat(lista).bodyJson().extractingPath("$.contenido[1].comprometido").isEqualTo(5.0);
		assertThat(get(INVENTARIO + "/{id}", contador, taladro)).bodyJson().extractingPath("$.comprometido")
				.isEqualTo(5.0);
	}

	// HU-08 RF-17: en el estante hay 15; contar 14 deja 9 disponibles
	@Test
	void elAjusteDescuentaLoComprometidoDeLoContado() throws Exception {
		Integer taladro = conPedidosAbiertos();

		MvcTestResult ajustado = ajuste(empleado, taladro, "14", "CONTEO", null);

		assertThat(ajustado).hasStatus(HttpStatus.CREATED);
		assertThat(ajustado).bodyJson().extractingPath("$.stock").isEqualTo(9.0);
		assertThat(ajustado).bodyJson().extractingPath("$.comprometido").isEqualTo(5.0);
	}

	// HU-08 RF-17
	@Test
	void elHistorialMuestraLoContadoYLoComprometido() throws Exception {
		Integer taladro = conPedidosAbiertos();
		ajuste(empleado, taladro, "14", "CONTEO", null);

		MvcTestResult historial = get(INVENTARIO + "/{id}/movimientos", contador, taladro);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].cantidad").isEqualTo(-1.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].stockResultante").isEqualTo(9.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].contado").isEqualTo(14.0);
		assertThat(historial).bodyJson().extractingPath("$.contenido[0].comprometido").isEqualTo(5.0);
		// Una entrada no lleva conteo
		assertThat(historial).bodyJson().extractingPath("$.contenido[1].contado").isNull();
	}

	// HU-08 RF-18
	@Test
	void contarMenosDeLoComprometidoSeRechaza() throws Exception {
		Integer taladro = conPedidosAbiertos();

		MvcTestResult rechazado = ajuste(empleado, taladro, "4", "CONTEO", null);

		assertThat(rechazado).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazado).bodyJson().extractingPath("$.codigo").isEqualTo("CONTEO_MENOR_QUE_COMPROMETIDO");
		assertThat(rechazado).bodyJson().extractingPath("$.comprometido").isEqualTo(5.0);
		assertThat(stock(taladro)).isEqualByComparingTo("10");
		assertThat(movimientoRepository.count()).isEqualTo(1);
	}

	// HU-08 RF-6: lo contado es lo que ya hay en el estante
	@Test
	void contarLoMismoQueHayEnElEstanteNoCambiaNada() throws Exception {
		Integer taladro = conPedidosAbiertos();

		MvcTestResult rechazado = ajuste(empleado, taladro, "15", "CONTEO", null);

		assertThat(rechazado).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazado).bodyJson().extractingPath("$.codigo").isEqualTo("AJUSTE_SIN_CAMBIO");
		assertThat(stock(taladro)).isEqualByComparingTo("10");
	}

	// HU-08 RF-17, caso límite
	@Test
	void contarLoMismoQueLoComprometidoDejaElDisponibleEnCero() throws Exception {
		Integer taladro = conPedidosAbiertos();

		assertThat(ajuste(empleado, taladro, "5", "MERMA", null)).bodyJson().extractingPath("$.stock")
				.isEqualTo(0.0);
	}

	// HU-08 RF-4, RF-15, caso límite: sin pedidos abiertos, lo contado es lo disponible
	@Test
	void sinPedidosAbiertosElAjusteEsComoAntes() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		entrada(empleado, taladro, "10");

		MvcTestResult ajustado = ajuste(empleado, taladro, "7", "CONTEO", null);

		assertThat(ajustado).bodyJson().extractingPath("$.stock").isEqualTo(7.0);
		assertThat(ajustado).bodyJson().extractingPath("$.comprometido").isEqualTo(0.0);
	}

	// HU-08 RF-6 (/code-review): lo que hay en el estante tampoco pasa del máximo, así lo contado siempre se puede cargar
	@Test
	void unaEntradaNoDejaElEstantePorEncimaDelMaximo() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		entrada(empleado, taladro, "99999");
		productoRepository.findById(taladro.longValue()).ifPresent(producto -> {
			producto.aplicarMovimiento(new BigDecimal("9999989"));
			productoRepository.save(producto);
		});
		pedidoEn(EstadoPedido.PENDIENTE_PAGO, taladro, "9");

		MvcTestResult rechazada = entrada(empleado, taladro, "5");

		assertThat(rechazada).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazada).bodyJson().extractingPath("$.codigo").isEqualTo("STOCK_FUERA_DE_RANGO");
		assertThat(entrada(empleado, taladro, "0.5").getResponse().getStatus()).isBetween(400, 499);
		assertThat(entrada(empleado, taladro, "1")).hasStatus(HttpStatus.CREATED);
	}

	// HU-08 RF-17, caso límite: el ajuste y la confirmación bloquean el producto; en cualquier orden quedan 9
	@Test
	void unAjusteYUnaConfirmacionALaVezNoPierdenStock() throws Exception {
		Integer taladro = producto("Taladro", herramientas, "UNIDAD");
		entrada(empleado, taladro, "10");
		Usuario usuario = usuarioRepository.findByCorreo("cli.hu08@mail.com").orElseThrow();
		usuario.marcarCorreoVerificado();
		usuarioRepository.save(usuario);
		assertThat(post("/api/carrito/lineas", cliente, "{\"idProducto\":%d,\"cantidad\":3}".formatted(taladro)))
				.hasStatus(HttpStatus.OK);

		List<MvcTestResult> resultados = aLaVez(() -> ajuste(empleado, taladro, "12", "CONTEO", null),
				() -> post("/api/pedidos", cliente,
						"{\"nombreRetira\":\"Eva Paz\",\"telefonoRetira\":\"70000000\",\"nota\":null,\"totalEsperado\":30}"));

		assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).hasStatus(HttpStatus.CREATED));
		assertThat(stock(taladro)).isEqualByComparingTo("9");
		assertThat(get(INVENTARIO + "/{id}", empleado, taladro)).bodyJson().extractingPath("$.comprometido")
				.isEqualTo(3.0);
	}

	// Dos peticiones que arrancan juntas
	@SafeVarargs
	private List<MvcTestResult> aLaVez(Callable<MvcTestResult>... pedidos) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(pedidos.length);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<MvcTestResult>> futuros = java.util.Arrays.stream(pedidos).map(pedido -> hilos.submit(() -> {
				largada.await();
				return pedido.call();
			})).toList();
			largada.countDown();
			List<MvcTestResult> resultados = new java.util.ArrayList<>();
			for (Future<MvcTestResult> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			hilos.shutdown();
		}
	}
}
