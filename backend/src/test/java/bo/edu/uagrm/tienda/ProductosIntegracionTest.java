package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.config.ImagenesProperties;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-07: la API de productos de punta a punta, y lo que cierra de HU-06 (RF-10 y el conteo de RF-12)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductosIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String GESTION = "/api/gestion/productos";
	private static final String CATEGORIAS = "/api/gestion/categorias";
	private static final String FOTOS = "/api/productos/{id}/imagenes/{nombre}";
	private static final byte[] PNG = FotoDePrueba.PNG;

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

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ImagenesProperties imagenes;

	private String tokenAdmin;
	private Integer herramientas;
	private Integer jardin;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		tokenAdmin = token(ADMIN, CLAVE_ADMIN);
		herramientas = idDe(categoria("Herramientas eléctricas"), "$.idCategoria");
		jardin = idDe(categoria("Jardín"), "$.idCategoria");
	}

	@AfterEach
	void limpiar() {
		movimientoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		usuarioRepository.findByCorreo("emp@mail.com").ifPresent(usuarioRepository::delete);
	}

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	private String tokenDeCliente() throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Emi\",\"apellido\":\"Paz\",\"correo\":\"emp@mail.com\",\"contrasena\":\"secreta12\"}")
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private MvcTestResult categoria(String nombre) {
		return mvc.post().uri(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"%s\"}".formatted(nombre)).exchange();
	}

	private static Integer idDe(MvcTestResult resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
	}

	private static String cuerpo(String nombre, String marca, Integer idCategoria, String precio, String ficha) {
		return """
				{"nombre":"%s","marca":"%s","idCategoria":%d,"unidad":"UNIDAD","precio":%s,"ficha":%s}"""
				.formatted(nombre, marca, idCategoria, precio, ficha);
	}

	private MvcTestResult enviar(String token, String cuerpo) {
		return mvc.post().uri(GESTION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private Integer crear(String nombre, String marca, Integer idCategoria) throws Exception {
		MvcTestResult creado = enviar(tokenAdmin, cuerpo(nombre, marca, idCategoria, "10.50", "[]"));
		assertThat(creado).hasStatus(HttpStatus.CREATED);
		return idDe(creado, "$.idProducto");
	}

	private MvcTestResult put(String uri, String cuerpo, Object... variables) {
		return mvc.put().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	// Pares nombre, valor
	private MvcTestResult listar(String... parametros) {
		var pedido = mvc.get().uri(GESTION).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin);
		for (int i = 0; i < parametros.length; i += 2) {
			pedido = pedido.param(parametros[i], parametros[i + 1]);
		}
		return pedido.exchange();
	}

	private MvcTestResult subirImagen(Integer id, byte[] contenido) {
		return mvc.post().uri(GESTION + "/{id}/imagenes", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.multipart().file(new MockMultipartFile("imagen", "foto.png", "image/png", contenido)).exchange();
	}

	// HU-07 RF-1
	@Test
	void sinSesionLaGestionDevuelve401YUnClienteRecibe403() throws Exception {
		assertThat(mvc.get().uri(GESTION).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		String cliente = tokenDeCliente();
		assertThat(enviar(cliente, cuerpo("Pala", "", jardin, "10", "[]"))).hasStatus(HttpStatus.FORBIDDEN);
		Integer id = crear("Pala", "", jardin);
		assertThat(mvc.post().uri(GESTION + "/{id}/imagenes", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente)
				.multipart().file(new MockMultipartFile("imagen", "foto.png", "image/png", PNG)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.put().uri(GESTION + "/{id}/activo", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente)
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}").exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	// HU-07 RF-2, RF-3, RF-4, RF-6, RF-10
	@Test
	void creaConCodigoYFichaYApareceEnLaLista() throws Exception {
		MvcTestResult creado = enviar(tokenAdmin, cuerpo("  Taladro percutor 750 W ", " Bosch ", herramientas,
				"420.00", "[{\"nombre\":\"Potencia\",\"valor\":\"750 W\"},{\"nombre\":\" \",\"valor\":\"\"},"
						+ "{\"nombre\":\"Mandril\",\"valor\":\"13 mm\"}]"));

		assertThat(creado).hasStatus(HttpStatus.CREATED);
		Integer id = idDe(creado, "$.idProducto");
		assertThat(creado).bodyJson().extractingPath("$.codigo").isEqualTo("HER-%04d".formatted(id));
		assertThat(creado).bodyJson().extractingPath("$.nombre").isEqualTo("Taladro percutor 750 W");
		assertThat(creado).bodyJson().extractingPath("$.marca").isEqualTo("Bosch");
		assertThat(creado).bodyJson().extractingPath("$.activo").isEqualTo(true);
		assertThat(creado).bodyJson().extractingPath("$.ficha[*].nombre").asArray()
				.containsExactly("Potencia", "Mandril");

		MvcTestResult lista = listar();
		assertThat(lista).bodyJson().extractingPath("$.contenido[0].codigo").isEqualTo("HER-%04d".formatted(id));
		assertThat(lista).bodyJson().extractingPath("$.contenido[0].categoria").isEqualTo("Herramientas eléctricas");
		assertThat(lista).bodyJson().extractingPath("$.contenido[0].precio").isEqualTo(420.0);
		assertThat(lista).bodyJson().extractingPath("$.contenido[0].imagenPrincipal").isNull();

		MvcTestResult leido = mvc.get().uri(GESTION + "/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
		assertThat(leido).bodyJson().extractingPath("$.ficha[1].valor").isEqualTo("13 mm");
	}

	// HU-07 RF-3: precio mayor que 0, hasta 99 999,99 y con dos decimales como máximo
	@Test
	void precioFueraDeRangoOConTresDecimalesSeRechaza() {
		for (String precio : List.of("0", "-5", "12.345", "100000", "null")) {
			MvcTestResult rechazado = enviar(tokenAdmin, cuerpo("Pala", "", jardin, precio, "[]"));
			assertThat(rechazado).as(precio).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(rechazado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("precio");
		}
		assertThat(enviar(tokenAdmin, cuerpo("Pala", "", jardin, "99999.99", "[]"))).hasStatus(HttpStatus.CREATED);
	}

	private static String conCosto(String cuerpo, String costo) {
		return cuerpo.substring(0, cuerpo.lastIndexOf('}')) + ",\"costo\":" + costo + "}";
	}

	// HU-18 RF-19: el costo se guarda, se lee en la gestión y se cambia al editar
	@Test
	void crearConCostoLoGuarda() throws Exception {
		MvcTestResult creado = enviar(tokenAdmin, conCosto(cuerpo("Pala", "", jardin, "85.50", "[]"), "60.00"));

		assertThat(creado).hasStatus(HttpStatus.CREATED);
		assertThat(creado).bodyJson().extractingPath("$.costo").isEqualTo(60.0);
		Integer id = idDe(creado, "$.idProducto");
		assertThat(put(GESTION + "/{id}", conCosto(cuerpo("Pala", "", jardin, "85.50", "[]"), "70.25"), id))
				.bodyJson().extractingPath("$.costo").isEqualTo(70.25);
		assertThat(mvc.get().uri(GESTION + "/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.exchange()).bodyJson().extractingPath("$.costo").isEqualTo(70.25);
	}

	// HU-18 RF-19: como el precio, mayor que 0, hasta 99 999,99 y con dos decimales como máximo
	@Test
	void costoInvalidoEs400() {
		for (String costo : List.of("0", "-5", "12.345", "100000")) {
			MvcTestResult rechazado = enviar(tokenAdmin, conCosto(cuerpo("Pala", "", jardin, "10", "[]"), costo));
			assertThat(rechazado).as(costo).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(rechazado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("costo");
		}
		assertThat(enviar(tokenAdmin, conCosto(cuerpo("Pala", "", jardin, "10", "[]"), "99999.99")))
				.hasStatus(HttpStatus.CREATED);
	}

	// HU-18 RF-19: es opcional; editar sin costo lo quita
	@Test
	void sinCostoQuedaNulo() throws Exception {
		MvcTestResult creado = enviar(tokenAdmin, cuerpo("Pala", "", jardin, "10", "[]"));
		assertThat(creado).bodyJson().extractingPath("$.costo").isNull();

		Integer id = idDe(creado, "$.idProducto");
		put(GESTION + "/{id}", conCosto(cuerpo("Pala", "", jardin, "10", "[]"), "6"), id);
		assertThat(put(GESTION + "/{id}", cuerpo("Pala", "", jardin, "10", "[]"), id)).bodyJson()
				.extractingPath("$.costo").isNull();
	}

	// HU-07 RF-3: nombre vacío o largo, unidad que no está en la lista
	@Test
	void nombreYUnidadInvalidosSeRechazan() {
		assertThat(enviar(tokenAdmin, cuerpo("   ", "", jardin, "10", "[]"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(enviar(tokenAdmin, cuerpo("a".repeat(121), "", jardin, "10", "[]")))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(enviar(tokenAdmin, cuerpo("a".repeat(120), "", jardin, "10", "[]"))).hasStatus(HttpStatus.CREATED);
		assertThat(enviar(tokenAdmin, cuerpo("Pala", "", jardin, "10", "[]").replace("UNIDAD", "GALON")))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// HU-07 RF-6: 16 pares, un nombre repetido o un valor que falta
	@Test
	void fichaInvalidaSeRechaza() {
		String dieciseis = IntStream.rangeClosed(1, 16)
				.mapToObj(i -> "{\"nombre\":\"Dato %d\",\"valor\":\"%d\"}".formatted(i, i))
				.collect(Collectors.joining(",", "[", "]"));
		assertThat(enviar(tokenAdmin, cuerpo("Pala", "", jardin, "10", dieciseis))).hasStatus(HttpStatus.BAD_REQUEST);

		MvcTestResult repetida = enviar(tokenAdmin, cuerpo("Pala", "", jardin, "10",
				"[{\"nombre\":\"Peso\",\"valor\":\"1 kg\"},{\"nombre\":\"peso\",\"valor\":\"2 kg\"}]"));
		assertThat(repetida).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(repetida).bodyJson().extractingPath("$.codigo").isEqualTo("FICHA_INVALIDA");

		assertThat(enviar(tokenAdmin, cuerpo("Pala", "", jardin, "10", "[{\"nombre\":\"Peso\",\"valor\":\"\"}]")))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// HU-07 RF-11
	@Test
	void categoriaOProductoInexistentesDevuelven404() {
		MvcTestResult sinCategoria = enviar(tokenAdmin, cuerpo("Pala", "", 999999, "10", "[]"));
		assertThat(sinCategoria).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(sinCategoria).bodyJson().extractingPath("$.codigo").isEqualTo("CATEGORIA_NO_ENCONTRADA");

		MvcTestResult sinProducto = mvc.get().uri(GESTION + "/999999")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
		assertThat(sinProducto).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(sinProducto).bodyJson().extractingPath("$.codigo").isEqualTo("PRODUCTO_NO_ENCONTRADO");
	}

	// HU-07 RF-5
	@Test
	void editarLoPasaDeCategoriaSinCambiarElCodigo() throws Exception {
		Integer id = crear("Regadera metálica", "", herramientas);

		MvcTestResult editado = put(GESTION + "/{id}", cuerpo("Regadera metálica 10 L", "Tramontina", jardin,
				"35.90", "[]"), id);

		assertThat(editado).hasStatus(HttpStatus.OK);
		assertThat(editado).bodyJson().extractingPath("$.codigo").isEqualTo("HER-%04d".formatted(id));
		assertThat(editado).bodyJson().extractingPath("$.categoria").isEqualTo("Jardín");
		assertThat(editado).bodyJson().extractingPath("$.precio").isEqualTo(35.9);
	}

	// HU-07 RF-9, RF-10
	@Test
	void desactivarYActivarYFiltrarPorEstado() throws Exception {
		Integer id = crear("Tijera de podar", "", jardin);
		crear("Pala", "", jardin);

		assertThat(put(GESTION + "/{id}/activo", "{\"activo\":false}", id)).bodyJson().extractingPath("$.activo")
				.isEqualTo(false);
		assertThat(listar("activo", "false")).bodyJson().extractingPath("$.contenido[*].nombre").asArray()
				.containsExactly("Tijera de podar");
		assertThat(listar("activo", "true")).bodyJson().extractingPath("$.contenido[*].nombre").asArray()
				.containsExactly("Pala");

		assertThat(put(GESTION + "/{id}/activo", "{\"activo\":true}", id)).bodyJson().extractingPath("$.activo")
				.isEqualTo(true);
	}

	// HU-07 RF-10: categoría y texto en el nombre, el código o la marca; de a 20, del más reciente al más antiguo
	@Test
	void filtraPorCategoriaYTextoYPagina() throws Exception {
		crear("Bordeadora", "Bosch", jardin);
		crear("Taladro", "Bosch", herramientas);
		Integer manguera = crear("Manguera 50% goma", "Tramontina", jardin);

		assertThat(listar("categoria", jardin.toString(), "texto", "BOSCH")).bodyJson().extractingPath("$.contenido[*].nombre")
				.asArray().containsExactly("Bordeadora");
		assertThat(listar("texto", "jar-%04d".formatted(manguera))).bodyJson().extractingPath("$.contenido[*].nombre")
				.asArray().containsExactly("Manguera 50% goma");
		// El % se busca literal, no como comodín
		assertThat(listar("texto", "50%")).bodyJson().extractingPath("$.totalElementos").isEqualTo(1);

		for (int i = 0; i < 18; i++) {
			crear("Clavo " + i, "", herramientas);
		}
		MvcTestResult primera = listar();
		assertThat(primera).bodyJson().extractingPath("$.contenido").asArray().hasSize(20);
		assertThat(primera).bodyJson().extractingPath("$.totalElementos").isEqualTo(21);
		assertThat(primera).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Clavo 17");
		assertThat(listar("pagina", "1")).bodyJson().extractingPath("$.contenido[*].nombre").asArray()
				.containsExactly("Bordeadora");
	}

	// HU-07 RF-7, RF-8: sube, reordena, sirve sin sesión y quita borrando el archivo
	@Test
	void imagenesSeSubenReordenanSirvenYQuitan() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String primera = JsonPath.read(subirImagen(id, PNG).getResponse().getContentAsString(), "$.imagenes[0]");
		MvcTestResult conDos = subirImagen(id, PNG);
		assertThat(conDos).hasStatus(HttpStatus.OK);
		String segunda = JsonPath.read(conDos.getResponse().getContentAsString(), "$.imagenes[1]");

		MvcTestResult reordenado = put(GESTION + "/{id}/imagenes",
				"{\"imagenes\":[\"%s\",\"%s\"]}".formatted(segunda, primera), id);
		assertThat(reordenado).bodyJson().extractingPath("$.imagenes").asArray().containsExactly(segunda, primera);
		assertThat(listar()).bodyJson().extractingPath("$.contenido[0].imagenPrincipal").isEqualTo(segunda);

		MvcTestResult servida = mvc.get().uri("/api/productos/{id}/imagenes/{nombre}", id, segunda).exchange();
		assertThat(servida).hasStatus(HttpStatus.OK);
		assertThat(servida).hasContentType("image/png");

		MvcTestResult sinUna = mvc.delete().uri(GESTION + "/{id}/imagenes/{nombre}", id, segunda)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
		assertThat(sinUna).bodyJson().extractingPath("$.imagenes").asArray().containsExactly(primera);
		assertThat(Files.exists(imagenes.directorio().resolve(segunda))).isFalse();
		assertThat(mvc.get().uri("/api/productos/{id}/imagenes/{nombre}", id, segunda).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Eficiencia (plan 2026-10-28): el nombre de una foto no se reutiliza, así que el navegador la guarda un año
	@Test
	void laFotoSeGuardaUnAno() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String foto = JsonPath.read(subirImagen(id, PNG).getResponse().getContentAsString(), "$.imagenes[0]");

		MvcTestResult servida = mvc.get().uri("/api/productos/{id}/imagenes/{nombre}", id, foto).exchange();

		assertThat(servida).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable");
	}

	// HU-07 RF-7: la sexta y un archivo que no es imagen
	@Test
	void sextaImagenYArchivoQueNoEsImagenSeRechazan() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		for (int i = 0; i < 5; i++) {
			assertThat(subirImagen(id, PNG)).hasStatus(HttpStatus.OK);
		}

		MvcTestResult sexta = subirImagen(id, PNG);
		assertThat(sexta).hasStatus(HttpStatus.CONFLICT);
		assertThat(sexta).bodyJson().extractingPath("$.codigo").isEqualTo("PRODUCTO_LLENO_DE_IMAGENES");

		Integer otro = crear("Serrucho", "", herramientas);
		assertThat(subirImagen(otro, "%PDF-1.7".getBytes())).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// HU-22 RF-1, RF-7: el original y sus dos versiones; cada versión mide su ancho y se guarda un año
	@Test
	void subirUnaFotoGuardaSusVersiones() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String foto = JsonPath.read(subirImagen(id, FotoDePrueba.png(1600, 1000)).getResponse().getContentAsString(),
				"$.imagenes[0]");

		for (int ancho : new int[] { 400, 1200 }) {
			MvcTestResult version = mvc.get().uri(FOTOS + "?ancho={ancho}", id, foto, ancho).exchange();
			assertThat(version).hasStatus(HttpStatus.OK).hasContentType("image/jpeg");
			assertThat(version).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable");
			assertThat(anchoDe(version)).isEqualTo(ancho);
		}
		assertThat(mvc.get().uri(FOTOS, id, foto).exchange()).hasContentType("image/png");
	}

	// HU-22 RF-8: solo 400 o 1200
	@Test
	void unAnchoInvalidoResponde400() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String foto = JsonPath.read(subirImagen(id, PNG).getResponse().getContentAsString(), "$.imagenes[0]");

		for (String ancho : List.of("abc", "-1", "0", "999", "1200.5")) {
			MvcTestResult rechazado = mvc.get().uri(FOTOS + "?ancho={ancho}", id, foto, ancho).exchange();
			assertThat(rechazado).as(ancho).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(rechazado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("ancho");
		}
	}

	// HU-22 RF-8: sin la versión, el original, sin caché: si no, el navegador lo guardaría un año en su lugar
	@Test
	void sinLaVersionSirveElOriginalSinCache() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String foto = JsonPath.read(subirImagen(id, PNG).getResponse().getContentAsString(), "$.imagenes[0]");
		Files.delete(imagenes.directorio().resolve(foto.replaceFirst("\\.png$", "-400.jpg")));

		MvcTestResult original = mvc.get().uri(FOTOS + "?ancho=400", id, foto).exchange();

		assertThat(original).hasStatus(HttpStatus.OK).hasContentType("image/png");
		assertThat(original).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-cache");
	}

	// HU-22 RF-9
	@Test
	void quitarUnaFotoBorraSusVersiones() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String foto = JsonPath.read(subirImagen(id, PNG).getResponse().getContentAsString(), "$.imagenes[0]");
		String uuid = foto.substring(0, foto.indexOf('.'));
		assertThat(archivosQueEmpiezanCon(uuid)).hasSize(3);

		mvc.delete().uri(GESTION + "/{id}/imagenes/{nombre}", id, foto)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();

		assertThat(archivosQueEmpiezanCon(uuid)).isEmpty();
	}

	// HU-22 RF-5: se rechaza antes de abrirla, y la carpeta queda igual
	@Test
	void unaFotoEnormeSeRechazaSinGuardarNada() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		long antes = cantidadDeArchivos();

		MvcTestResult rechazada = subirImagen(id, FotoDePrueba.soloEncabezadoPng(20_000, 20_000));

		assertThat(rechazada).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rechazada).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_CON_DEMASIADOS_PIXELES");
		assertThat(cantidadDeArchivos()).isEqualTo(antes);
		assertThat(mvc.get().uri(GESTION + "/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.exchange()).bodyJson().extractingPath("$.imagenes").asArray().isEmpty();
	}

	// HU-22 RF-6: empieza como PNG pero no se puede abrir
	@Test
	void unaFotoDanadaSeRechaza() throws Exception {
		Integer id = crear("Martillo", "", herramientas);

		MvcTestResult rechazada = subirImagen(id, FotoDePrueba.PNG_DANADO);

		assertThat(rechazada).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rechazada).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_INVALIDA");
	}

	// HU-22, caso límite: la versión es de la foto, y la foto, del producto; un nombre de versión no es una foto
	@Test
	void elAnchoDeOtroProductoDa404() throws Exception {
		Integer martillo = crear("Martillo", "", herramientas);
		Integer pala = crear("Pala", "", jardin);
		String foto = JsonPath.read(subirImagen(martillo, PNG).getResponse().getContentAsString(), "$.imagenes[0]");

		assertThat(mvc.get().uri(FOTOS + "?ancho=400", pala, foto).exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri(FOTOS, martillo, foto.replaceFirst("\\.png$", "-400.jpg")).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	private static int anchoDe(MvcTestResult resultado) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(resultado.getResponse().getContentAsByteArray())).getWidth();
	}

	private List<Path> archivosQueEmpiezanCon(String prefijo) throws Exception {
		try (Stream<Path> archivos = Files.list(imagenes.directorio())) {
			return archivos.filter(archivo -> archivo.getFileName().toString().startsWith(prefijo)).toList();
		}
	}

	private long cantidadDeArchivos() throws Exception {
		try (Stream<Path> archivos = Files.list(imagenes.directorio())) {
			return archivos.count();
		}
	}

	// HU-07 RF-8: una lista que no coincide con las imágenes no cambia nada
	@Test
	void reordenarConOtraListaSeRechaza() throws Exception {
		Integer id = crear("Martillo", "", herramientas);
		String imagen = JsonPath.read(subirImagen(id, PNG).getResponse().getContentAsString(), "$.imagenes[0]");

		MvcTestResult rechazado = put(GESTION + "/{id}/imagenes",
				"{\"imagenes\":[\"%s\",\"%s\"]}".formatted(imagen, imagen), id);

		assertThat(rechazado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rechazado).bodyJson().extractingPath("$.codigo").isEqualTo("ORDEN_DE_IMAGENES_INVALIDO");
	}

	// HU-07 RF-12, RF-13, HU-06 RF-10, RF-12: con productos, aunque estén inactivos, no se borra y muestra el conteo
	@Test
	void categoriaConProductosNoSeBorraYMuestraCuantosTiene() throws Exception {
		crear("Pala", "", jardin);
		Integer inactivo = crear("Rastrillo", "", jardin);
		put(GESTION + "/{id}/activo", "{\"activo\":false}", inactivo);

		MvcTestResult borrado = mvc.delete().uri(CATEGORIAS + "/{id}", jardin)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
		assertThat(borrado).hasStatus(HttpStatus.CONFLICT);
		assertThat(borrado).bodyJson().extractingPath("$.codigo").isEqualTo("CATEGORIA_CON_PRODUCTOS");

		MvcTestResult categorias = mvc.get().uri(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.exchange();
		assertThat(categorias).bodyJson().extractingPath("$[*].productos").asArray().containsExactly(0, 2);
		assertThat(put(CATEGORIAS + "/{id}/visible", "{\"visible\":false}", jardin)).bodyJson()
				.extractingPath("$.productos").isEqualTo(2);
	}
}
