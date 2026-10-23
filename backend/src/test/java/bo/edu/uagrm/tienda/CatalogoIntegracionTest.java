package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.FormatoModeloTest;

// HU-09: la API pública del catálogo, sin sesión
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoIntegracionTest {

	private static final String PRODUCTOS = "/api/productos";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	private String admin;
	private Integer herramientas;
	private Integer jardin;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"admin@tienda.test\",\"contrasena\":\"clave-admin-pruebas\"}").exchange();
		admin = JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
		herramientas = id(enviar("/api/gestion/categorias", "{\"nombre\":\"Herramientas\"}"), "$.idCategoria");
		jardin = id(enviar("/api/gestion/categorias", "{\"nombre\":\"Jardín\"}"), "$.idCategoria");
	}

	@AfterEach
	void limpiar() {
		movimientoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
	}

	private MvcTestResult enviar(String uri, String cuerpo, Object... variables) {
		return mvc.post().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private static Integer id(MvcTestResult resultado, String ruta) throws Exception {
		assertThat(resultado.getResponse().getStatus()).isLessThan(300);
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	// Un producto con su stock; con stock 0 queda agotado
	private Integer producto(String nombre, String marca, Integer categoria, String precio, int stock,
			boolean destacado) throws Exception {
		Integer idProducto = id(enviar("/api/gestion/productos", """
				{"nombre":"%s","marca":%s,"idCategoria":%d,"unidad":"UNIDAD","precio":%s,"destacado":%b}"""
				.formatted(nombre, marca == null ? "null" : "\"" + marca + "\"", categoria, precio, destacado)),
				"$.idProducto");
		if (stock > 0) {
			assertThat(enviar("/api/inventario/{id}/entradas", "{\"cantidad\":%d}".formatted(stock), idProducto))
					.hasStatus(HttpStatus.CREATED);
		}
		return idProducto;
	}

	private MvcTestResult get(String uri, Object... variables) {
		return mvc.get().uri(uri, variables).exchange();
	}

	private static List<String> nombres(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	// HU-09 RF-1
	@Test
	void catalogoSinSesionResponde200() throws Exception {
		producto("Pala", null, jardin, "50.00", 3, false);

		MvcTestResult resultado = get(PRODUCTOS);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(nombres(resultado, "$.contenido[*].nombre")).containsExactly("Pala");
		assertThat(get(PRODUCTOS + "/portada")).hasStatus(HttpStatus.OK);
		assertThat(get(PRODUCTOS + "/marcas")).hasStatus(HttpStatus.OK);
	}

	// HU-09 RF-1: un token vencido o basura no corta la tienda
	@Test
	void conTokenVencidoResponde200() {
		assertThat(mvc.get().uri(PRODUCTOS).header(HttpHeaders.AUTHORIZATION, "Bearer token.vencido.basura")
				.exchange()).hasStatus(HttpStatus.OK);
	}

	// HU-09 RF-3, RF-4
	@Test
	void portadaArmaDestacadosRecientesYCategoriasConHastaDoce() throws Exception {
		for (int i = 1; i <= 13; i++) {
			producto("Herramienta %02d".formatted(i), null, herramientas, "10.00", 2, i == 1);
		}
		producto("Pala", null, jardin, "50.00", 3, false);

		MvcTestResult portada = get(PRODUCTOS + "/portada");

		assertThat(portada).hasStatus(HttpStatus.OK);
		assertThat(nombres(portada, "$.destacados[*].nombre")).containsExactly("Herramienta 01");
		assertThat(nombres(portada, "$.recientes[*].nombre")).hasSize(12).startsWith("Pala", "Herramienta 13");
		assertThat(nombres(portada, "$.categorias[*].nombre")).containsExactly("Herramientas", "Jardín");
		assertThat(nombres(portada, "$.categorias[0].productos[*].nombre")).hasSize(12)
				.startsWith("Herramienta 13").doesNotContain("Herramienta 01");
		assertThat(nombres(portada, "$.categorias[1].productos[*].nombre")).containsExactly("Pala");
	}

	// HU-09 RF-3, RF-4
	@Test
	void portadaSinDestacadosNoTraeLaSeccion() throws Exception {
		producto("Pala", null, jardin, "50.00", 3, false);

		assertThat(nombres(get(PRODUCTOS + "/portada"), "$.destacados")).isEmpty();
	}

	// HU-09 RF-3, RF-7
	@Test
	void portadaOmiteCategoriasSinDisponibles() throws Exception {
		producto("Taladro", null, herramientas, "420.00", 0, true);
		producto("Pala", null, jardin, "50.00", 3, false);

		MvcTestResult portada = get(PRODUCTOS + "/portada");

		assertThat(nombres(portada, "$.destacados")).isEmpty();
		assertThat(nombres(portada, "$.recientes[*].nombre")).containsExactly("Pala");
		assertThat(nombres(portada, "$.categorias[*].nombre")).containsExactly("Jardín");
	}

	// HU-09 RF-11, RF-12
	@Test
	void listaDe24ConTotalYPaginaMasAllaVacia() throws Exception {
		for (int i = 1; i <= 25; i++) {
			producto("Producto %02d".formatted(i), null, jardin, "10.00", 1, false);
		}

		MvcTestResult primera = get(PRODUCTOS);
		assertThat(primera).bodyJson().extractingPath("$.contenido").asArray().hasSize(24);
		assertThat(primera).bodyJson().extractingPath("$.totalElementos").isEqualTo(25);
		assertThat(primera).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
		assertThat(get(PRODUCTOS + "?pagina=1")).bodyJson().extractingPath("$.contenido").asArray().hasSize(1);
		assertThat(get(PRODUCTOS + "?pagina=7")).bodyJson().extractingPath("$.contenido").asArray().isEmpty();
	}

	// HU-09 RF-15
	@Test
	void tarjetaNoExponeStockNiDatosDeGestion() throws Exception {
		producto("Pala", "Truper", jardin, "50.00", 3, true);
		producto("Azada", null, jardin, "40.00", 0, false);

		MvcTestResult resultado = get(PRODUCTOS + "?orden=NOMBRE");

		Map<String, Object> tarjeta = JsonPath.read(resultado.getResponse().getContentAsString(), "$.contenido[0]");
		assertThat(tarjeta).containsOnlyKeys("idProducto", "nombre", "marca", "precio", "unidad", "imagenPrincipal",
				"disponible", "conModelo");
		assertThat(tarjeta).containsEntry("nombre", "Pala").containsEntry("disponible", true);
		assertThat(nombres(resultado, "$.contenido[*].disponible")).isEqualTo(List.of(true, false));
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("stock", "activo", "destacado",
				"fechaAlta");
	}

	// HU-09 RF-13, RF-14
	@Test
	void parametrosInvalidosResponden400() {
		for (String consulta : List.of("pagina=abc", "orden=xyz", "categoria=abc", "precioMin=1,2,3", "precioMin=-1",
				"precioMax=100000", "precioMin=1.234", "precioMin=200&precioMax=100", "q=" + "a".repeat(101))) {
			assertThat(get(PRODUCTOS + "?" + consulta)).as(consulta).hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(get(PRODUCTOS + "?pagina=-3")).hasStatus(HttpStatus.OK);
		// Una página tan alta que su desplazamiento no entra en un int: vacía, no 500
		assertThat(get(PRODUCTOS + "?pagina=100000000")).bodyJson().extractingPath("$.contenido").asArray().isEmpty();
		assertThat(get(PRODUCTOS + "?q=" + "a".repeat(100))).hasStatus(HttpStatus.OK);
	}

	// HU-09 RF-2, RF-12
	@Test
	void categoriaInexistenteUOcultaDevuelveVacio() throws Exception {
		producto("Pala", null, jardin, "50.00", 3, false);
		assertThat(mvc.put().uri("/api/gestion/categorias/{id}/visible", jardin)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"visible\":false}").exchange()).hasStatus(HttpStatus.OK);

		for (Object categoria : List.of(jardin, 999_999)) {
			MvcTestResult resultado = get(PRODUCTOS + "?categoria={id}", categoria);
			assertThat(resultado).hasStatus(HttpStatus.OK);
			assertThat(nombres(resultado, "$.contenido")).isEmpty();
		}
	}

	// HU-09 RF-8, RF-9
	@Test
	void buscaSinTildesYFiltraPorMarca() throws Exception {
		producto("Martillo de carpintería", "Truper", herramientas, "85.00", 3, false);
		producto("Regadera", "Tramontina", jardin, "35.00", 3, false);

		assertThat(nombres(get(PRODUCTOS + "?q= MARTÍLLO  de "), "$.contenido[*].nombre"))
				.containsExactly("Martillo de carpintería");
		assertThat(nombres(get(PRODUCTOS + "?marca=TRAMONTINA"), "$.contenido[*].nombre")).containsExactly("Regadera");
		assertThat(nombres(get(PRODUCTOS + "?q=%25"), "$.contenido")).isEmpty();
	}

	// HU-09 RF-9
	@Test
	void marcasDelCatalogo() throws Exception {
		producto("Pala", "Truper", jardin, "50.00", 3, false);
		producto("Regadera", "Tramontina", jardin, "35.00", 0, false);

		assertThat(nombres(get(PRODUCTOS + "/marcas"), "$")).containsExactly("Tramontina", "Truper");
	}

	// --- HU-10: detalle del producto ---

	private void desactivar(Integer idProducto) {
		assertThat(mvc.put().uri("/api/gestion/productos/{id}/activo", idProducto)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}").exchange()).hasStatus(HttpStatus.OK);
	}

	// HU-10 RF-1, RF-2
	@Test
	void detalleSinSesionConTodosLosDatos() throws Exception {
		Integer taladro = id(enviar("/api/gestion/productos", """
				{"nombre":"Taladro percutor 750 W","marca":"Bosch","descripcion":"Para concreto.","idCategoria":%d,
				 "unidad":"UNIDAD","precio":420.00,
				 "ficha":[{"nombre":"Potencia","valor":"750 W"},{"nombre":"Mandril","valor":"13 mm"}]}""".formatted(herramientas)),
				"$.idProducto");

		MvcTestResult resultado = get(PRODUCTOS + "/{id}", taladro);

		assertThat(resultado).hasStatus(HttpStatus.OK);
		String json = resultado.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(json, "$.nombre")).isEqualTo("Taladro percutor 750 W");
		assertThat((String) JsonPath.read(json, "$.marca")).isEqualTo("Bosch");
		assertThat((String) JsonPath.read(json, "$.codigo")).startsWith("HER-");
		assertThat((Integer) JsonPath.read(json, "$.idCategoria")).isEqualTo(herramientas);
		assertThat((String) JsonPath.read(json, "$.categoria")).isEqualTo("Herramientas");
		assertThat((Double) JsonPath.read(json, "$.precio")).isEqualTo(420.0);
		assertThat((String) JsonPath.read(json, "$.unidad")).isEqualTo("UNIDAD");
		assertThat((String) JsonPath.read(json, "$.descripcion")).isEqualTo("Para concreto.");
		assertThat(nombres(resultado, "$.ficha[*].nombre")).containsExactly("Potencia", "Mandril");
		assertThat(nombres(resultado, "$.ficha[*].valor")).containsExactly("750 W", "13 mm");
		assertThat(nombres(resultado, "$.imagenes")).isEmpty();
	}

	// HU-10 RF-4
	@Test
	void detalleNoExponeStockNiDatosDeGestion() throws Exception {
		Integer pala = producto("Pala", "Truper", jardin, "50.00", 3, true);
		producto("Rastrillo", null, jardin, "40.00", 2, true);

		String json = get(PRODUCTOS + "/{id}", pala).getResponse().getContentAsString();

		assertThat(nombres(get(PRODUCTOS + "/{id}", pala), "$.relacionados[*].nombre")).containsExactly("Rastrillo");

		Map<String, Object> detalle = JsonPath.read(json, "$");
		assertThat(detalle).containsOnlyKeys("idProducto", "codigo", "nombre", "marca", "idCategoria", "categoria",
				"precio", "unidad", "descripcion", "ficha", "imagenes", "disponible", "modelo", "relacionados");
		// Del modelo, solo el archivo: su peso es de la gestión (HU-16 RF-1)
		assertThat(json).doesNotContain("stock", "activo", "destacado", "fechaAlta", "peso");
	}

	// HU-10 RF-4
	@Test
	void detalleDiceSiHayStock() throws Exception {
		Integer pala = producto("Pala", null, jardin, "50.00", 3, false);
		Integer azada = producto("Azada", null, jardin, "40.00", 0, false);

		assertThat((Boolean) JsonPath.read(get(PRODUCTOS + "/{id}", pala).getResponse().getContentAsString(),
				"$.disponible")).isTrue();
		MvcTestResult agotada = get(PRODUCTOS + "/{id}", azada);
		assertThat(agotada).hasStatus(HttpStatus.OK);
		assertThat((Boolean) JsonPath.read(agotada.getResponse().getContentAsString(), "$.disponible")).isFalse();
	}

	// HU-10 RF-7
	@Test
	void relacionadosDeLaCategoriaSinElProductoNiAgotados() throws Exception {
		Integer taladro = producto("Taladro", null, herramientas, "420.00", 3, false);
		producto("Martillo", null, herramientas, "85.00", 3, false);
		producto("Sierra", null, herramientas, "120.00", 0, false);
		producto("Pala", null, jardin, "50.00", 3, false);

		assertThat(nombres(get(PRODUCTOS + "/{id}", taladro), "$.relacionados[*].nombre")).containsExactly("Martillo");
	}

	// HU-10 RF-7
	@Test
	void relacionadosHastaDoce() throws Exception {
		Integer taladro = producto("Taladro", null, herramientas, "420.00", 3, false);
		for (int i = 0; i < 13; i++) {
			producto("Llave " + i, null, herramientas, "10.00", 1, false);
		}

		List<String> relacionados = nombres(get(PRODUCTOS + "/{id}", taladro), "$.relacionados[*].nombre");

		assertThat(relacionados).hasSize(12).doesNotContain("Taladro").first().isEqualTo("Llave 12");
	}

	// --- HU-16: el modelo 3D en el catálogo público ---

	private String subirModelo(Integer idProducto) throws Exception {
		MvcTestResult subido = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.multipart(HttpMethod.PUT, "/api/gestion/productos/{id}/modelo", idProducto)
				.file(new MockMultipartFile("modelo", "martillo.glb", "model/gltf-binary", FormatoModeloTest.glb(256)))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
		assertThat(subido).hasStatus(HttpStatus.OK);
		return JsonPath.read(subido.getResponse().getContentAsString(), "$.modelo.archivo");
	}

	// HU-16 RF-1
	@Test
	void detalleTraeElModeloSiTiene() throws Exception {
		Integer martillo = producto("Martillo", null, herramientas, "85.00", 3, false);
		String archivo = subirModelo(martillo);

		MvcTestResult resultado = get(PRODUCTOS + "/{id}", martillo);

		assertThat((String) JsonPath.read(resultado.getResponse().getContentAsString(), "$.modelo")).isEqualTo(archivo);
		assertThat(get(PRODUCTOS + "/{id}/modelo/{archivo}", martillo, archivo)).hasStatus(HttpStatus.OK);
	}

	// HU-16 RF-1
	@Test
	void detalleSinModeloTraeNull() throws Exception {
		Integer pala = producto("Pala", null, jardin, "50.00", 3, false);

		Map<String, Object> detalle = JsonPath.read(get(PRODUCTOS + "/{id}", pala).getResponse().getContentAsString(),
				"$");

		assertThat(detalle).containsEntry("modelo", null);
	}

	// HU-16 RF-2: el catálogo, la portada y «Más de…» del detalle
	@Test
	void tarjetasDicenSiTienenModelo() throws Exception {
		Integer martillo = producto("Martillo", null, herramientas, "85.00", 3, true);
		Integer llave = producto("Llave", null, herramientas, "30.00", 3, true);
		subirModelo(martillo);

		MvcTestResult catalogo = get(PRODUCTOS + "?orden=NOMBRE");
		assertThat(nombres(catalogo, "$.contenido[*].nombre")).containsExactly("Llave", "Martillo");
		assertThat(nombres(catalogo, "$.contenido[*].conModelo")).isEqualTo(List.of(false, true));

		MvcTestResult portada = get(PRODUCTOS + "/portada");
		assertThat(nombres(portada, "$.destacados[?(@.nombre == 'Martillo')].conModelo")).isEqualTo(List.of(true));
		assertThat(nombres(portada, "$.destacados[?(@.nombre == 'Llave')].conModelo")).isEqualTo(List.of(false));

		assertThat(nombres(get(PRODUCTOS + "/{id}", llave), "$.relacionados[*].conModelo")).isEqualTo(List.of(true));
	}

	// HU-10 RF-8: los tres casos responden igual
	@Test
	void inactivoOcultoEInexistenteResponden404() throws Exception {
		Integer rastrillo = producto("Rastrillo", null, jardin, "40.00", 3, false);
		desactivar(rastrillo);
		Integer taladro = producto("Taladro", null, herramientas, "420.00", 3, false);
		assertThat(mvc.put().uri("/api/gestion/categorias/{id}/visible", herramientas)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"visible\":false}").exchange()).hasStatus(HttpStatus.OK);

		for (Object idProducto : List.of(rastrillo, taladro, 999_999)) {
			MvcTestResult resultado = get(PRODUCTOS + "/{id}", idProducto);
			assertThat(resultado).as("producto %s", idProducto).hasStatus(HttpStatus.NOT_FOUND);
			String json = resultado.getResponse().getContentAsString();
			assertThat((String) JsonPath.read(json, "$.codigo")).isEqualTo("PRODUCTO_NO_ENCONTRADO");
			assertThat((String) JsonPath.read(json, "$.detail")).isEqualTo("No existe el producto indicado.");
		}
	}

	// HU-10 RF-9: un id que no es un número no llega a la base
	@Test
	void idQueNoEsNumeroResponde400() {
		for (String id : List.of("abc", "1.5", "99999999999999999999")) {
			assertThat(get(PRODUCTOS + "/" + id)).as(id).hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(get(PRODUCTOS + "/-1")).hasStatus(HttpStatus.NOT_FOUND);
	}
}
