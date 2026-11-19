package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import bo.edu.uagrm.tienda.entity.Descuento;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.DescuentoRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-24 RF-1 a RF-7: la gestión de descuentos, solo del Administrador
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DescuentosIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String DESCUENTOS = "/api/gestion/descuentos";
	private static final List<String> CORREOS = List.of("emp.hu24@mail.com", "con.hu24@mail.com",
			"cli.hu24@mail.com");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private DescuentoRepository descuentoRepository;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private Clock clock;

	private String admin;
	private Integer herramientas;
	private Integer martillo;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, "clave-admin-pruebas");
		herramientas = id(enviar("/api/gestion/categorias", admin, "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		martillo = producto("Martillo", "100", null);
	}

	@AfterEach
	void limpiar() {
		descuentoRepository.deleteAll();
		movimientoRepository.deleteAll();
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
		if (rol == null) {
			return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
		}
		Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
		assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"%s\"}".formatted(rol)).exchange())
				.hasStatus(HttpStatus.OK);
		return token(correo, "secreta12");
	}

	private Integer producto(String nombre, String precio, String costo) throws Exception {
		return id(enviar("/api/gestion/productos", admin, """
				{"nombre":"%s","idCategoria":%d,"unidad":"UNIDAD","precio":%s,"costo":%s}"""
				.formatted(nombre, herramientas, precio, costo)), "$.idProducto");
	}

	private MvcTestResult enviar(String uri, String token, String cuerpo, Object... variables) {
		return mvc.post().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult cambiar(Object idDescuento, String cuerpo) {
		return mvc.put().uri(DESCUENTOS + "/{id}", idDescuento).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult borrar(Object idDescuento) {
		return mvc.delete().uri(DESCUENTOS + "/{id}", idDescuento)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).exchange();
	}

	private MvcTestResult terminar(Object idDescuento) {
		return enviar(DESCUENTOS + "/{id}/terminar", admin, "", idDescuento);
	}

	private MvcTestResult listar(String token) {
		return mvc.get().uri(DESCUENTOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		assertThat(resultado.getResponse().getStatus()).isLessThan(300);
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	private LocalDate hoy() {
		return LocalDate.now(clock);
	}

	private static String deProducto(Integer idProducto, Object porcentaje, Object inicio, Object fin) {
		return "{\"idProducto\":%s,\"porcentaje\":%s,\"inicio\":%s,\"fin\":%s}".formatted(idProducto, porcentaje,
				json(inicio), json(fin));
	}

	private static String deCategoria(Integer idCategoria, Object porcentaje, Object inicio, Object fin) {
		return "{\"idCategoria\":%s,\"porcentaje\":%s,\"inicio\":%s,\"fin\":%s}".formatted(idCategoria, porcentaje,
				json(inicio), json(fin));
	}

	private static String json(Object valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}

	private MvcTestResult crear(String cuerpo) {
		return enviar(DESCUENTOS, admin, cuerpo);
	}

	// Un vigente que empezó antes de hoy: la API no deja crear uno con inicio pasado
	private Long vigenteDesdeAyer(int porcentaje) {
		return descuentoRepository.save(Descuento.deProducto(
				productoRepository.findById(martillo.longValue()).orElseThrow(), porcentaje, hoy().minusDays(1),
				hoy().plusDays(3), usuarioRepository.findByCorreo(ADMIN).orElseThrow(), LocalDateTime.now(clock)))
				.getIdDescuento();
	}

	private Long vencido() {
		return descuentoRepository.save(Descuento.deProducto(
				productoRepository.findById(martillo.longValue()).orElseThrow(), 10, hoy().minusDays(9),
				hoy().minusDays(5), usuarioRepository.findByCorreo(ADMIN).orElseThrow(), LocalDateTime.now(clock)))
				.getIdDescuento();
	}

	private Double precioPublico(Integer idProducto) throws Exception {
		return JsonPath.read(mvc.get().uri("/api/productos/{id}", idProducto).exchange().getResponse()
				.getContentAsString(), "$.precio");
	}

	// --- RF-1 ---

	// HU-24 RF-1
	@Test
	void creaUnoDeProductoYOtroDeCategoria() throws Exception {
		MvcTestResult deMartillo = crear(deProducto(martillo, 15, hoy(), hoy().plusDays(5)));
		MvcTestResult deHerramientas = crear(deCategoria(herramientas, 10, hoy().plusDays(1), hoy().plusDays(5)));

		assertThat(deMartillo).hasStatus(HttpStatus.CREATED);
		assertThat(deMartillo).bodyJson().extractingPath("$.descuento.producto").isEqualTo("Martillo");
		assertThat(deMartillo).bodyJson().extractingPath("$.descuento.categoria").isNull();
		assertThat(deMartillo).bodyJson().extractingPath("$.descuento.porcentaje").isEqualTo(15);
		assertThat(deMartillo).bodyJson().extractingPath("$.descuento.estado").isEqualTo("VIGENTE");
		assertThat(deMartillo).bodyJson().extractingPath("$.bajoElCosto").asArray().isEmpty();
		assertThat(deHerramientas).hasStatus(HttpStatus.CREATED);
		assertThat(deHerramientas).bodyJson().extractingPath("$.descuento.categoria").isEqualTo("Herramientas");
		assertThat(deHerramientas).bodyJson().extractingPath("$.descuento.estado").isEqualTo("PROGRAMADO");
		assertThat(descuentoRepository.count()).isEqualTo(2);
		assertThat(precioPublico(martillo)).isEqualTo(85.0);
	}

	// HU-24 RF-1: entero de 1 a 90
	@Test
	void rechazaPorcentajeFueraDeRangoODecimal() {
		for (String porcentaje : List.of("0", "91", "-5", "12.5", "\"quince\"", "null")) {
			MvcTestResult rechazo = crear(deProducto(martillo, porcentaje, hoy(), hoy().plusDays(5)));
			assertThat(rechazo).as(porcentaje).hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(descuentoRepository.count()).isZero();
	}

	// HU-24 RF-1: desde hoy, fin desde el inicio y hasta 366 días después
	@Test
	void rechazaFechasInvalidas() {
		assertThat(crear(deProducto(martillo, 15, hoy().minusDays(1), hoy().plusDays(5))))
				.bodyJson().extractingPath("$.errores[0].campo").isEqualTo("inicio");
		assertThat(crear(deProducto(martillo, 15, hoy().plusDays(3), hoy().plusDays(2))))
				.bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fin");
		assertThat(crear(deProducto(martillo, 15, hoy(), hoy().plusDays(367))))
				.bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fin");
		assertThat(crear(deProducto(martillo, 15, "mañana", hoy().plusDays(2)))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(crear(deProducto(martillo, 15, null, hoy().plusDays(2)))).hasStatus(HttpStatus.BAD_REQUEST);
		// /code-review: un año imposible es un dato basura, no un 500
		assertThat(crear(deProducto(martillo, 15, "+10000-01-01", "+10000-01-02"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(crear(deProducto(martillo, 15, hoy(), hoy().plusDays(366)))).hasStatus(HttpStatus.CREATED);
		assertThat(descuentoRepository.count()).isEqualTo(1);
	}

	// HU-24 RF-1: uno solo de producto o categoría
	@Test
	void rechazaProductoYCategoriaANingunoOLosDos() {
		String ninguno = "{\"porcentaje\":15,\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(hoy(), hoy().plusDays(2));
		String losDos = "{\"idProducto\":%d,\"idCategoria\":%d,\"porcentaje\":15,\"inicio\":\"%s\",\"fin\":\"%s\"}"
				.formatted(martillo, herramientas, hoy(), hoy().plusDays(2));

		assertThat(crear(ninguno)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(crear(losDos)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(descuentoRepository.count()).isZero();
	}

	// HU-24 RF-1
	@Test
	void rechazaUnProductoOCategoriaQueNoExiste() {
		assertThat(crear(deProducto(999999, 15, hoy(), hoy().plusDays(2)))).bodyJson()
				.extractingPath("$.errores[0].campo").isEqualTo("idProducto");
		assertThat(crear(deCategoria(999999, 15, hoy(), hoy().plusDays(2)))).bodyJson()
				.extractingPath("$.errores[0].campo").isEqualTo("idCategoria");
	}

	// --- RF-2 ---

	// HU-24 RF-2: el que se cruza un día no se guarda, y dice con cuál
	@Test
	void rechazaElQueSeCruzaYDiceConCual() {
		crear(deProducto(martillo, 15, hoy().plusDays(1), hoy().plusDays(5)));

		MvcTestResult rechazo = crear(deProducto(martillo, 20, hoy().plusDays(5), hoy().plusDays(9)));

		assertThat(rechazo).hasStatus(HttpStatus.CONFLICT);
		assertThat(rechazo).bodyJson().extractingPath("$.codigo").isEqualTo("DESCUENTO_SOLAPADO");
		assertThat(rechazo).bodyJson().extractingPath("$.porcentaje").isEqualTo(15);
		assertThat(rechazo).bodyJson().extractingPath("$.inicio").isEqualTo(hoy().plusDays(1).toString());
		assertThat(rechazo).bodyJson().extractingPath("$.fin").isEqualTo(hoy().plusDays(5).toString());
		assertThat(crear(deProducto(martillo, 20, hoy().plusDays(6), hoy().plusDays(9))))
				.hasStatus(HttpStatus.CREATED);
	}

	// HU-24 RF-2, RF-9: uno del producto y otro de su categoría no se cruzan; se aplica el mayor
	@Test
	void productoYCategoriaNoSeCruzan() throws Exception {
		assertThat(crear(deProducto(martillo, 10, hoy(), hoy().plusDays(5)))).hasStatus(HttpStatus.CREATED);
		assertThat(crear(deCategoria(herramientas, 30, hoy(), hoy().plusDays(5)))).hasStatus(HttpStatus.CREATED);

		assertThat(precioPublico(martillo)).isEqualTo(70.0);
	}

	// --- RF-3 ---

	// HU-24 RF-3
	@Test
	void listaVigentesProgramadosYVencidosDeA20() throws Exception {
		Long viejo = vencido();
		crear(deProducto(martillo, 30, hoy().plusDays(10), hoy().plusDays(12)));
		crear(deProducto(martillo, 15, hoy(), hoy().plusDays(5)));

		MvcTestResult lista = listar(admin);

		assertThat(lista).hasStatus(HttpStatus.OK);
		assertThat(lista).bodyJson().extractingPath("$.contenido[*].estado").asArray().containsExactly("VIGENTE",
				"PROGRAMADO", "VENCIDO");
		assertThat(lista).bodyJson().extractingPath("$.contenido[2].idDescuento").isEqualTo(viejo.intValue());
		assertThat(lista).bodyJson().extractingPath("$.tamano").isEqualTo(20);
	}

	// --- RF-4, RF-5 ---

	// HU-24 RF-4
	@Test
	void programadoSeEditaYSeBorra() throws Exception {
		Integer programado = id(crear(deProducto(martillo, 15, hoy().plusDays(2), hoy().plusDays(5))),
				"$.descuento.idDescuento");

		MvcTestResult cambiado = cambiar(programado, "{\"porcentaje\":25,\"inicio\":\"%s\",\"fin\":\"%s\"}"
				.formatted(hoy().plusDays(3), hoy().plusDays(7)));

		assertThat(cambiado).hasStatus(HttpStatus.OK);
		assertThat(cambiado).bodyJson().extractingPath("$.descuento.porcentaje").isEqualTo(25);
		assertThat(cambiado).bodyJson().extractingPath("$.descuento.fin").isEqualTo(hoy().plusDays(7).toString());
		assertThat(borrar(programado)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(descuentoRepository.count()).isZero();
	}

	// HU-24 RF-4: el producto o la categoría no cambian, aunque se manden
	@Test
	void editarNoCambiaElProductoNiLaCategoria() throws Exception {
		Integer otro = producto("Alicate", "40", null);
		Integer programado = id(crear(deProducto(martillo, 15, hoy().plusDays(2), hoy().plusDays(5))),
				"$.descuento.idDescuento");

		MvcTestResult cambiado = cambiar(programado, deProducto(otro, 20, hoy().plusDays(2), hoy().plusDays(5)));

		assertThat(cambiado).hasStatus(HttpStatus.OK);
		assertThat(cambiado).bodyJson().extractingPath("$.descuento.idProducto").isEqualTo(martillo);
	}

	// HU-24 RF-5: terminar un vigente lo deja de aplicar en el acto
	@Test
	void vigenteSeTerminaYDejaDeAplicarse() throws Exception {
		Long vigente = vigenteDesdeAyer(20);
		assertThat(precioPublico(martillo)).isEqualTo(80.0);

		assertThat(terminar(vigente)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(descuentoRepository.findById(vigente).orElseThrow().getFin()).isEqualTo(hoy().minusDays(1));
		assertThat(precioPublico(martillo)).isEqualTo(100.0);
	}

	// HU-24 RF-5: si empezó hoy, terminarlo lo borra (el fin no puede quedar antes del inicio)
	@Test
	void vigenteQueEmpezoHoySeBorraAlTerminar() throws Exception {
		Integer hoyMismo = id(crear(deProducto(martillo, 15, hoy(), hoy().plusDays(5))), "$.descuento.idDescuento");

		assertThat(terminar(hoyMismo)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(descuentoRepository.count()).isZero();
		assertThat(precioPublico(martillo)).isEqualTo(100.0);
	}

	// HU-24 RF-5: un vigente no se edita ni se borra; un vencido no se toca; un programado no se termina
	@Test
	void vigenteNoSeEditaNiSeBorraYVencidoNoSeToca() throws Exception {
		Long vigente = vigenteDesdeAyer(20);
		Long viejo = vencido();
		Integer programado = id(crear(deProducto(martillo, 15, hoy().plusDays(10), hoy().plusDays(12))),
				"$.descuento.idDescuento");
		String cambio = "{\"porcentaje\":25,\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(hoy().plusDays(1),
				hoy().plusDays(2));

		for (Object id : List.of(vigente, viejo)) {
			assertThat(cambiar(id, cambio)).bodyJson().extractingPath("$.codigo").isEqualTo("DESCUENTO_NO_EDITABLE");
			assertThat(borrar(id)).hasStatus(HttpStatus.CONFLICT);
		}
		assertThat(terminar(viejo)).hasStatus(HttpStatus.CONFLICT);
		assertThat(terminar(programado)).hasStatus(HttpStatus.CONFLICT);
		assertThat(descuentoRepository.count()).isEqualTo(3);
	}

	// --- RF-6 ---

	// HU-24 RF-6: se guarda igual y dice qué productos quedan por debajo de su costo, y cuántos son
	@Test
	void avisaLosProductosQueQuedanBajoElCosto() throws Exception {
		producto("Taladro", "200", "190");
		producto("Llave", "50", "10");

		MvcTestResult guardado = crear(deCategoria(herramientas, 10, hoy(), hoy().plusDays(5)));

		assertThat(guardado).hasStatus(HttpStatus.CREATED);
		assertThat(guardado).bodyJson().extractingPath("$.bajoElCosto").asArray().containsExactly("Taladro");
		assertThat(guardado).bodyJson().extractingPath("$.totalBajoElCosto").isEqualTo(1);
	}

	// /code-review: un producto que no se vende no avisa
	@Test
	void unProductoInactivoNoAvisaBajoElCosto() throws Exception {
		Integer taladro = producto("Taladro", "200", "190");
		assertThat(mvc.put().uri("/api/gestion/productos/{id}/activo", taladro)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}").exchange()).hasStatus(HttpStatus.OK);

		assertThat(crear(deProducto(taladro, 10, hoy(), hoy().plusDays(5)))).bodyJson()
				.extractingPath("$.totalBajoElCosto").isEqualTo(0);
	}

	// /code-review: borrar una categoría sin productos se lleva sus descuentos, sin un 500
	@Test
	void borrarUnaCategoriaSinProductosBorraSusDescuentos() {
		Integer ofertas = id2(enviar("/api/gestion/categorias", admin, "{\"nombre\":\"Ofertas\"}"));
		assertThat(crear(deCategoria(ofertas, 10, hoy(), hoy().plusDays(5)))).hasStatus(HttpStatus.CREATED);

		assertThat(mvc.delete().uri("/api/gestion/categorias/{id}", ofertas)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).exchange()).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(descuentoRepository.count()).isZero();
	}

	private static Integer id2(MvcTestResult resultado) {
		try {
			return id(resultado, "$.idCategoria");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// --- Datos basura y RF-7 ---

	@Test
	void idQueNoEsUnNumeroDa400() {
		assertThat(borrar("abc")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(terminar("abc")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void idQueNoExisteDa404() {
		assertThat(borrar(999999)).bodyJson().extractingPath("$.codigo").isEqualTo("DESCUENTO_NO_ENCONTRADO");
		assertThat(terminar(999999)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(cambiar(999999, "{\"porcentaje\":25,\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(hoy(),
				hoy().plusDays(1)))).hasStatus(HttpStatus.NOT_FOUND);
	}

	// HU-24 RF-7
	@Test
	void empleadoContadorYClienteReciben403YSinSesion401() throws Exception {
		String cuerpo = deProducto(martillo, 15, hoy(), hoy().plusDays(5));
		for (String token : List.of(cuentaCon("emp.hu24@mail.com", "EMPLEADO"),
				cuentaCon("con.hu24@mail.com", "CONTADOR"), cuentaCon("cli.hu24@mail.com", null))) {
			assertThat(listar(token)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(enviar(DESCUENTOS, token, cuerpo)).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(mvc.get().uri(DESCUENTOS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(descuentoRepository.count()).isZero();
	}
}
