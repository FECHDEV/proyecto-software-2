package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static bo.edu.uagrm.tienda.PedidoIntegracionTest.aLaVez;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import bo.edu.uagrm.tienda.entity.EstadoPedido;
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
import bo.edu.uagrm.tienda.service.PedidoService;
import bo.edu.uagrm.tienda.service.pago.EstadoDelCobro;
import bo.edu.uagrm.tienda.service.pago.SimuladorDePago;

// HU-17: el personal ve los pedidos y entrega los pagados, de punta a punta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DespachoIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String PEDIDOS = "/api/pedidos";
	private static final String DESPACHO = "/api/despacho/pedidos";
	private static final List<String> CORREOS = List.of("ana.hu17@mail.com", "beto.hu17@mail.com",
			"emp.hu17@mail.com", "emp2.hu17@mail.com", "conta.hu17@mail.com");

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
	private PedidoService pedidoService;

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
	private String empleado;
	private String contador;
	private Integer herramientas;
	private Integer martillo;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		ana = cuentaCon("ana.hu17@mail.com", "Ana", null);
		empleado = cuentaCon("emp.hu17@mail.com", "Eva", "EMPLEADO");
		contador = cuentaCon("conta.hu17@mail.com", "Carlos", "CONTADOR");
		herramientas = id(post("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		martillo = id(post("/api/gestion/productos", admin, """
				{"nombre":"Martillo","idCategoria":%d,"unidad":"UNIDAD","precio":85.50}""".formatted(herramientas)),
				"$.idProducto");
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

	// Con rol, se vuelve a iniciar sesión: el token del registro es de Cliente
	private String cuentaCon(String correo, String nombre, String rol) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"%s\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(nombre, correo))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		verificarCorreo(correo);
		if (rol == null) {
			return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
		}
		Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
		assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"%s\"}".formatted(rol)).exchange()).hasStatus(HttpStatus.OK);
		return token(correo, "secreta12");
	}

	// Para confirmar hace falta el correo verificado (HU-20 RF-6); el enlace se prueba en VerificacionCorreoIntegracionTest
	private void verificarCorreo(String correo) {
		Usuario usuario = usuarioRepository.findByCorreo(correo).orElseThrow();
		usuario.marcarCorreoVerificado();
		usuarioRepository.save(usuario);
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

	// Un pedido de 2 martillos (Bs 171) a nombre de quien se indica; un minuto después del anterior
	private Integer pedido(String token, String nombreRetira) throws Exception {
		reloj.avanzar(Duration.ofMinutes(1));
		assertThat(post("/api/carrito/lineas", token, "{\"idProducto\":%d,\"cantidad\":2}".formatted(martillo)))
				.hasStatus(HttpStatus.OK);
		MvcTestResult confirmado = post(PEDIDOS, token, """
				{"nombreRetira":"%s","telefonoRetira":"70000000","nota":"Paso a la tarde","totalEsperado":171}"""
				.formatted(nombreRetira));
		assertThat(confirmado).hasStatus(HttpStatus.CREATED);
		return id(confirmado, "$.idPedido");
	}

	private Integer pagado(String token, String nombreRetira) throws Exception {
		Integer idPedido = pedido(token, nombreRetira);
		assertThat(post(PEDIDOS + "/{id}/cobro", token, "", idPedido)).hasStatus(HttpStatus.OK);
		var cobro = cobroRepository.findAll().stream()
				.filter(c -> c.getPedido().getIdPedido().equals(Long.valueOf(idPedido))).findFirst().orElseThrow();
		simulador.forzar(cobro.getReferencia(), EstadoDelCobro.PAGADO);
		conciliacion.conciliar(cobro.getIdCobro());
		return idPedido;
	}

	private Integer cancelado(String token) throws Exception {
		Integer idPedido = pedido(token, "Ana Paz");
		assertThat(post(PEDIDOS + "/{id}/cancelacion", token, "", idPedido)).hasStatus(HttpStatus.OK);
		lineaRepository.deleteAll();
		return idPedido;
	}

	private MvcTestResult entregar(String token, Integer idPedido) {
		return post(DESPACHO + "/{id}/entrega", token, "", idPedido);
	}

	private BigDecimal stockDelMartillo() {
		return productoRepository.findById(Long.valueOf(martillo)).orElseThrow().getStock();
	}

	private EstadoPedido estado(Integer idPedido) {
		return pedidoRepository.findById(Long.valueOf(idPedido)).orElseThrow().getEstado();
	}

	// --- La entrega ---

	// HU-17 RF-6: queda entregado con quién y cuándo, y el stock no se mueve (la venta se hizo al pagar)
	@Test
	void entregarDejaElPedidoEntregadoConQuienYCuandoSinMoverElStock() throws Exception {
		Integer idPedido = pagado(ana, "Ana Paz");
		BigDecimal stock = stockDelMartillo();
		long movimientos = movimientoRepository.count();
		reloj.avanzar(Duration.ofHours(2));

		MvcTestResult resultado = entregar(empleado, idPedido);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ENTREGADO");
		assertThat(resultado).bodyJson().extractingPath("$.entregado").isNotNull();
		assertThat(resultado).bodyJson().extractingPath("$.entregadoPor").isEqualTo("Eva Paz");
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(stockDelMartillo()).isEqualByComparingTo(stock);
		assertThat(movimientoRepository.count()).isEqualTo(movimientos);
		// El Administrador también entrega
		assertThat(entregar(admin, pagado(ana, "Ana Paz"))).hasStatus(HttpStatus.OK);
	}

	// HU-17 RF-7, RF-8: pendiente, cancelado, vencido, con pago a devolver o ya entregado: 409 sin cambios
	@Test
	void soloSeEntregaUnPedidoPagado() throws Exception {
		Integer cancelado = cancelado(ana);
		Integer vencido = pedido(ana, "Ana Paz");
		reloj.avanzar(Duration.ofMinutes(31));
		pedidoService.vencer(usuarioRepository.findByCorreo("ana.hu17@mail.com").orElseThrow().getIdUsuario(),
				Long.valueOf(vencido));
		lineaRepository.deleteAll();
		Integer entregado = pagado(ana, "Ana Paz");
		assertThat(entregar(empleado, entregado)).hasStatus(HttpStatus.OK);
		Integer pendiente = pedido(ana, "Ana Paz");
		// Un pago tardío sin stock: «Pago a devolver»
		String beto = cuentaCon("beto.hu17@mail.com", "Beto", null);
		Integer aDevolver = pedido(beto, "Beto Paz");
		assertThat(post(PEDIDOS + "/{id}/cobro", beto, "", aDevolver)).hasStatus(HttpStatus.OK);
		var cobro = cobroRepository.findAll().stream()
				.filter(c -> c.getPedido().getIdPedido().equals(Long.valueOf(aDevolver))).findFirst().orElseThrow();
		assertThat(post(PEDIDOS + "/{id}/cancelacion", beto, "", aDevolver)).hasStatus(HttpStatus.OK);
		assertThat(post("/api/inventario/{id}/ajustes", admin, "{\"contada\":0,\"motivo\":\"CONTEO\"}", martillo))
				.hasStatus(HttpStatus.CREATED);
		simulador.forzar(cobro.getReferencia(), EstadoDelCobro.PAGADO);
		conciliacion.conciliar(cobro.getIdCobro());
		assertThat(estado(aDevolver)).isEqualTo(EstadoPedido.PAGO_A_DEVOLVER);

		for (Integer idPedido : List.of(cancelado, vencido, entregado, pendiente, aDevolver)) {
			EstadoPedido antes = estado(idPedido);
			MvcTestResult resultado = entregar(empleado, idPedido);
			assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
			assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("PEDIDO_NO_ENTREGABLE");
			assertThat(estado(idPedido)).isEqualTo(antes);
		}
	}

	// HU-17 RF-7: dos entregas a la vez del mismo pedido, una sola entra
	@Test
	void dosEntregasALaVezDejanUnaSola() throws Exception {
		Integer idPedido = pagado(ana, "Ana Paz");
		String otroEmpleado = cuentaCon("emp2.hu17@mail.com", "Otto", "EMPLEADO");

		List<Object> estados = aLaVez(() -> entregar(empleado, idPedido).getResponse().getStatus(),
				() -> entregar(otroEmpleado, idPedido).getResponse().getStatus());

		assertThat(estados).containsExactlyInAnyOrder(200, 409);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.ENTREGADO);
	}

	// HU-17 RF-6: la entrega y el vencimiento de otro pedido del mismo cliente se esperan por el cliente, sin trabarse
	@Test
	void entregarMientrasVenceOtroPedidoDelClienteNoSeTraba() throws Exception {
		Integer pagado = pagado(ana, "Ana Paz");
		Integer pendiente = pedido(ana, "Ana Paz");
		reloj.avanzar(Duration.ofMinutes(31));
		Long idAna = usuarioRepository.findByCorreo("ana.hu17@mail.com").orElseThrow().getIdUsuario();

		aLaVez(() -> entregar(empleado, pagado).getResponse().getStatus(), () -> {
			pedidoService.vencer(idAna, Long.valueOf(pendiente));
			return null;
		});

		assertThat(estado(pagado)).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(estado(pendiente)).isEqualTo(EstadoPedido.VENCIDO);
	}

	// --- La lista ---

	private MvcTestResult lista(String token, String consulta) {
		return get(DESPACHO + consulta, token);
	}

	// El texto como parámetro: en la dirección, MockMvc volvería a codificar un % escrito
	private MvcTestResult buscar(String token, String texto) {
		return mvc.get().uri(DESPACHO).param("texto", texto).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange();
	}

	private static List<Integer> ids(MvcTestResult resultado) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$.contenido[*].idPedido");
	}

	// HU-17 RF-1, RF-2: sin filtro, solo los pagados, el pagado hace más tiempo primero, con su resumen
	@Test
	void sinFiltroListaLosPagadosElMasViejoPrimero() throws Exception {
		Integer primero = pagado(ana, "Ana Paz");
		String beto = cuentaCon("beto.hu17@mail.com", "Beto", null);
		Integer segundo = pagado(beto, "Beto Rojas");
		Integer entregado = pagado(ana, "Ana Paz");
		assertThat(entregar(empleado, entregado)).hasStatus(HttpStatus.OK);
		cancelado(ana);

		MvcTestResult resultado = lista(empleado, "");

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(ids(resultado)).containsExactly(primero, segundo);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].nombreRetira").isEqualTo("Beto Rojas");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].estado").isEqualTo("PAGADO");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].cantidadProductos").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].total").isEqualTo(171.0);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].confirmado").isNotNull();
		assertThat(resultado).bodyJson().extractingPath("$.contenido[1].pagado").isNotNull();
		assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(2);
	}

	// HU-17 RF-2: con otro estado o con TODOS, el más nuevo primero. La lista no vence: un pendiente con la hora
	// pasada sigue pendiente hasta que lo vence la tarea de cada minuto
	@Test
	void conOtroEstadoOTodosElMasNuevoPrimero() throws Exception {
		Integer cancelado = cancelado(ana);
		Integer pagado = pagado(ana, "Ana Paz");
		Integer pendiente = pedido(ana, "Ana Paz");
		reloj.avanzar(Duration.ofMinutes(31));

		assertThat(ids(lista(empleado, "?estado=TODOS"))).containsExactly(pendiente, pagado, cancelado);
		assertThat(ids(lista(empleado, "?estado=CANCELADO"))).containsExactly(cancelado);
		assertThat(ids(lista(contador, "?estado=PENDIENTE_PAGO"))).containsExactly(pendiente);
		assertThat(estado(pendiente)).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
	}

	// HU-17 RF-3: por número, o por una parte del nombre de quien retira sin tildes ni mayúsculas, dentro del estado
	@Test
	void buscaPorNumeroOPorNombreSinTildesNiMayusculas() throws Exception {
		Integer jose = pagado(ana, "José Pérez");
		String beto = cuentaCon("beto.hu17@mail.com", "Beto", null);
		Integer maria = pagado(beto, "MARÍA Gómez");
		Integer porciento = pagado(ana, "Ana 100% Paz");

		assertThat(ids(lista(empleado, "?texto=jose"))).containsExactly(jose);
		assertThat(ids(lista(empleado, "?texto=  PEREZ "))).containsExactly(jose);
		assertThat(ids(lista(empleado, "?texto=maria gomez"))).containsExactly(maria);
		assertThat(ids(lista(empleado, "?texto=" + maria))).containsExactly(maria);
		// Un comodín escrito es un texto, no un comodín
		assertThat(ids(buscar(empleado, "%"))).containsExactly(porciento);
		assertThat(ids(lista(empleado, "?texto=pedro"))).isEmpty();
		// Dentro del estado elegido
		assertThat(entregar(empleado, jose)).hasStatus(HttpStatus.OK);
		assertThat(ids(lista(empleado, "?texto=jose"))).isEmpty();
		assertThat(ids(lista(empleado, "?texto=jose&estado=ENTREGADO"))).containsExactly(jose);
	}

	// HU-17 RF-3: una búsqueda vacía no filtra; una de más de 100 caracteres se recorta
	@Test
	void unaBusquedaVaciaNoFiltraYUnaLargaSeRecorta() throws Exception {
		Integer idPedido = pagado(ana, "Ana Paz");

		assertThat(ids(buscar(empleado, "  "))).containsExactly(idPedido);
		assertThat(lista(empleado, "?texto=" + "a".repeat(5000))).hasStatus(HttpStatus.OK);
		// Revisión de HU-17 (/code-review): el recorte no parte un emoji del borde
		assertThat(buscar(empleado, "a".repeat(99) + "😀")).hasStatus(HttpStatus.OK);
		// Lo que pasa de 100 no cuenta: «ana paz», 93 espacios y una «q» busca «ana paz»
		assertThat(ids(buscar(empleado, "ana paz" + " ".repeat(93) + "q"))).containsExactly(idPedido);
		// Revisión de HU-17 (/code-review, ronda 2): los espacios de adelante no cuentan para el límite
		pagado(ana, "Beto Rojas");
		assertThat(ids(buscar(empleado, " ".repeat(100) + "ana"))).containsExactly(idPedido);
	}

	// HU-17 RF-1, RF-12: de a 10; un estado que no existe es «Pagado»; una página fuera de rango, la última, y una
	// negativa o basura, la primera
	@Test
	void unEstadoOUnaPaginaQueNoSirvenMuestranLoMasCercano() throws Exception {
		for (int i = 0; i < 11; i++) {
			pagado(ana, "Ana Paz");
		}
		cancelado(ana);

		assertThat(ids(lista(empleado, "?estado=xx"))).hasSize(10);
		assertThat(lista(empleado, "?estado=xx")).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
		assertThat(lista(empleado, "?estado=pagado")).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
		assertThat(ids(lista(empleado, "?pagina=1"))).hasSize(1);
		assertThat(lista(empleado, "?pagina=9")).bodyJson().extractingPath("$.pagina").isEqualTo(1);
		assertThat(lista(empleado, "?pagina=99999999999999999999")).bodyJson().extractingPath("$.pagina")
				.isEqualTo(1);
		for (String basura : List.of("-3", "abc", "1.5")) {
			MvcTestResult resultado = lista(empleado, "?pagina=" + basura);
			assertThat(resultado).hasStatus(HttpStatus.OK);
			assertThat(resultado).bodyJson().extractingPath("$.pagina").isEqualTo(0);
		}
	}

	// HU-17 RF-9: el Contador ve la lista; el Cliente no
	@Test
	void elContadorVeLaListaYElClienteNo() throws Exception {
		pagado(ana, "Ana Paz");

		assertThat(lista(contador, "")).hasStatus(HttpStatus.OK);
		assertThat(lista(admin, "")).hasStatus(HttpStatus.OK);
		assertThat(lista(ana, "")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(DESPACHO).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- La ficha ---

	// HU-17 RF-4: los datos del pedido, de quien retira y de la cuenta; la línea conserva el nombre de cuando se pidió
	@Test
	void laFichaTraeLasLineasElClienteYQuienEntrego() throws Exception {
		Integer idPedido = pagado(ana, "José Pérez");
		assertThat(mvc.put().uri("/api/gestion/productos/{id}", martillo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Martillo de uña","idCategoria":%d,"unidad":"UNIDAD","precio":90}"""
						.formatted(herramientas))
				.exchange()).hasStatus(HttpStatus.OK);

		MvcTestResult ficha = get(DESPACHO + "/{id}", empleado, idPedido);

		assertThat(ficha).hasStatus(HttpStatus.OK);
		assertThat(ficha).bodyJson().extractingPath("$.estado").isEqualTo("PAGADO");
		assertThat(ficha).bodyJson().extractingPath("$.nombreRetira").isEqualTo("José Pérez");
		assertThat(ficha).bodyJson().extractingPath("$.telefonoRetira").isEqualTo("70000000");
		assertThat(ficha).bodyJson().extractingPath("$.nota").isEqualTo("Paso a la tarde");
		assertThat(ficha).bodyJson().extractingPath("$.clienteNombre").isEqualTo("Ana Paz");
		assertThat(ficha).bodyJson().extractingPath("$.clienteCorreo").isEqualTo("ana.hu17@mail.com");
		assertThat(ficha).bodyJson().extractingPath("$.confirmado").isNotNull();
		assertThat(ficha).bodyJson().extractingPath("$.pagado").isNotNull();
		assertThat(ficha).bodyJson().extractingPath("$.entregado").isNull();
		assertThat(ficha).bodyJson().extractingPath("$.entregadoPor").isNull();
		assertThat(ficha).bodyJson().extractingPath("$.total").isEqualTo(171.0);
		assertThat(ficha).bodyJson().extractingPath("$.lineas[0].nombre").isEqualTo("Martillo");
		assertThat(ficha).bodyJson().extractingPath("$.lineas[0].precio").isEqualTo(85.5);
		assertThat(ficha).bodyJson().extractingPath("$.lineas[0].cantidad").isEqualTo(2.0);
		assertThat(ficha).bodyJson().extractingPath("$.lineas[0].subtotal").isEqualTo(171.0);

		assertThat(entregar(empleado, idPedido)).hasStatus(HttpStatus.OK);
		MvcTestResult entregada = get(DESPACHO + "/{id}", contador, idPedido);
		assertThat(entregada).bodyJson().extractingPath("$.entregadoPor").isEqualTo("Eva Paz");
		assertThat(entregada).bodyJson().extractingPath("$.entregado").isNotNull();
	}

	// HU-17 RF-10: el Cliente ve la fecha de la entrega en la página de su pedido
	@Test
	void elClienteVeLaFechaDeEntregaEnSuPedido() throws Exception {
		Integer idPedido = pagado(ana, "Ana Paz");
		assertThat(get(PEDIDOS + "/{id}", ana, idPedido)).bodyJson().extractingPath("$.entregado").isNull();

		assertThat(entregar(empleado, idPedido)).hasStatus(HttpStatus.OK);

		MvcTestResult suPedido = get(PEDIDOS + "/{id}", ana, idPedido);
		assertThat(suPedido).bodyJson().extractingPath("$.estado").isEqualTo("ENTREGADO");
		assertThat(suPedido).bodyJson().extractingPath("$.entregado").isNotNull();
		assertThat(get(PEDIDOS, ana)).bodyJson().extractingPath("$.pagina.contenido[0].estado")
				.isEqualTo("ENTREGADO");
	}

	// --- Permisos y datos basura ---

	// HU-17 RF-9: el Contador ve, pero no entrega
	@Test
	void elContadorVeLaFichaPeroNoEntrega() throws Exception {
		Integer idPedido = pagado(ana, "Ana Paz");

		assertThat(get(DESPACHO + "/{id}", contador, idPedido)).hasStatus(HttpStatus.OK);
		assertThat(entregar(contador, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
	}

	// HU-17 RF-9: sin sesión 401; el Cliente, ni siquiera el dueño, ve la ficha ni entrega
	@Test
	void sinSesion401YClienteRecibe403() throws Exception {
		Integer idPedido = pagado(ana, "Ana Paz");

		assertThat(mvc.get().uri(DESPACHO + "/{id}", idPedido).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(DESPACHO + "/{id}/entrega", idPedido).exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(get(DESPACHO + "/{id}", ana, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(entregar(ana, idPedido)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(estado(idPedido)).isEqualTo(EstadoPedido.PAGADO);
	}

	// HU-17 RF-12: un pedido que no existe, o un número que no es un número
	@Test
	void unPedidoQueNoExisteResponde404() {
		for (String id : List.of("999999", "abc", "99999999999999999999")) {
			assertThat(get(DESPACHO + "/" + id, empleado)).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(post(DESPACHO + "/" + id + "/entrega", empleado, "")).hasStatus(HttpStatus.NOT_FOUND);
		}
		assertThat(get(DESPACHO + "/999999", empleado)).bodyJson().extractingPath("$.codigo")
				.isEqualTo("PEDIDO_NO_ENCONTRADO");
	}
}
