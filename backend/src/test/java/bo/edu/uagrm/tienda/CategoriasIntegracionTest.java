package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

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
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-06: la API de categorías, pública y de gestión, de punta a punta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoriasIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String GESTION = "/api/gestion/categorias";
	// La firma de un PNG: el formato se reconoce por el contenido
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0 };

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ImagenesProperties imagenes;

	private String tokenAdmin;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		categoriaRepository.deleteAll();
		tokenAdmin = token(ADMIN, CLAVE_ADMIN);
	}

	@AfterEach
	void limpiar() {
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

	private MvcTestResult crear(String token, String nombre, String descripcion) {
		String cuerpo = descripcion == null ? "{\"nombre\":\"%s\"}".formatted(nombre)
				: "{\"nombre\":\"%s\",\"descripcion\":\"%s\"}".formatted(nombre, descripcion);
		return mvc.post().uri(GESTION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private Integer idDe(MvcTestResult creada) throws Exception {
		return JsonPath.read(creada.getResponse().getContentAsString(), "$.idCategoria");
	}

	private MvcTestResult subirImagen(Integer id, byte[] contenido, String nombre) {
		return mvc.post().uri(GESTION + "/{id}/imagen", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.multipart().file(new MockMultipartFile("imagen", nombre, "image/jpeg", contenido)).exchange();
	}

	// HU-06 RF-1
	@Test
	void sinSesionLaGestionDevuelve401YUnClienteRecibe403() throws Exception {
		assertThat(mvc.get().uri(GESTION).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(crear(tokenDeCliente(), "Jardín", null)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// HU-06 RF-2, RF-3, RF-11, RF-12
	@Test
	void creaListaParaGestionYMuestraSoloLasVisiblesAlCatalogo() throws Exception {
		MvcTestResult jardin = crear(tokenAdmin, "  Jardín ", "  Patio y huerta ");
		assertThat(jardin).hasStatus(HttpStatus.CREATED);
		assertThat(jardin).bodyJson().extractingPath("$.nombre").isEqualTo("Jardín");
		assertThat(jardin).bodyJson().extractingPath("$.descripcion").isEqualTo("Patio y huerta");
		Integer idPlomeria = idDe(crear(tokenAdmin, "Plomería", null));
		crear(tokenAdmin, "Herramientas eléctricas", null);
		assertThat(mvc.put().uri(GESTION + "/{id}/visible", idPlomeria)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"visible\":false}").exchange()).hasStatus(HttpStatus.OK);

		MvcTestResult gestion = mvc.get().uri(GESTION).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.exchange();
		assertThat(gestion).bodyJson().extractingPath("$[*].nombre")
				.asArray().containsExactly("Herramientas eléctricas", "Jardín", "Plomería");
		assertThat(gestion).bodyJson().extractingPath("$[2].visible").isEqualTo(false);
		assertThat(gestion).bodyJson().extractingPath("$[0].productos").isEqualTo(0);

		MvcTestResult catalogo = mvc.get().uri("/api/categorias").exchange();
		assertThat(catalogo).hasStatus(HttpStatus.OK);
		assertThat(catalogo).bodyJson().extractingPath("$[*].nombre")
				.asArray().containsExactly("Herramientas eléctricas", "Jardín");
		assertThat(catalogo).bodyJson().doesNotHavePath("$[0].visible");
	}

	// HU-06 RF-3, RF-4
	@Test
	void nombreRepetidoVacioOLargoSeRechaza() throws Exception {
		crear(tokenAdmin, "Jardín", null);

		MvcTestResult repetido = crear(tokenAdmin, " JARDÍN ", null);
		assertThat(repetido).hasStatus(HttpStatus.CONFLICT);
		assertThat(repetido).bodyJson().extractingPath("$.codigo").isEqualTo("CATEGORIA_EXISTENTE");
		assertThat(crear(tokenAdmin, "   ", null)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(crear(tokenAdmin, "a".repeat(61), null)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(crear(tokenAdmin, "a".repeat(60), null)).hasStatus(HttpStatus.CREATED);
	}

	// HU-06 RF-4: tildes y mayúsculas no hacen otra categoría, en H2 y en MySQL
	@Test
	void nombreQueSoloDifiereEnTildesSeRechaza() throws Exception {
		crear(tokenAdmin, "Jardín", null);

		MvcTestResult sinTilde = crear(tokenAdmin, "JARDIN", null);

		assertThat(sinTilde).hasStatus(HttpStatus.CONFLICT);
		assertThat(sinTilde).bodyJson().extractingPath("$.codigo").isEqualTo("CATEGORIA_EXISTENTE");
	}

	// HU-06 RF-3: 60 caracteres que crecen al pasar a minúsculas no son un error del servidor
	@Test
	void nombreDe60CaracteresQueCrecenEnMinusculasSeAcepta() {
		assertThat(crear(tokenAdmin, "İ".repeat(60), null)).hasStatus(HttpStatus.CREATED);
	}

	// HU-06 RF-5
	@Test
	void editaNombreYDescripcion() throws Exception {
		Integer id = idDe(crear(tokenAdmin, "jardín", null));

		MvcTestResult editada = mvc.put().uri(GESTION + "/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Jardín\",\"descripcion\":\"Patio\"}").exchange();

		assertThat(editada).hasStatus(HttpStatus.OK);
		assertThat(editada).bodyJson().extractingPath("$.nombre").isEqualTo("Jardín");
	}

	// HU-06 RF-6, RF-11: la imagen se sube, se sirve con su tipo y se quita
	@Test
	void subeSirveYQuitaLaImagen() throws Exception {
		Integer id = idDe(crear(tokenAdmin, "Pintura", null));

		MvcTestResult subida = subirImagen(id, PNG, "foto.jpg.exe");
		assertThat(subida).hasStatus(HttpStatus.OK);
		assertThat(subida).bodyJson().extractingPath("$.tieneImagen").isEqualTo(true);

		MvcTestResult imagen = mvc.get().uri("/api/categorias/{id}/imagen", id).exchange();
		assertThat(imagen).hasStatus(HttpStatus.OK);
		assertThat(imagen).headers().hasValue(HttpHeaders.CONTENT_TYPE, "image/png");
		assertThat(imagen.getResponse().getContentAsByteArray()).isEqualTo(PNG);

		assertThat(mvc.delete().uri(GESTION + "/{id}/imagen", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange()).hasStatus(HttpStatus.OK);
		assertThat(mvc.get().uri("/api/categorias/{id}/imagen", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	// HU-06 RF-6: el archivo anterior se borra recién después de guardar la categoría con el nuevo
	@Test
	void cambiarLaImagenBorraElArchivoAnterior() throws Exception {
		Integer id = idDe(crear(tokenAdmin, "Pintura", null));
		subirImagen(id, PNG, "uno.png");
		String anterior = categoriaRepository.findById(id.longValue()).orElseThrow().getImagen();
		byte[] jpeg = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0 };

		assertThat(subirImagen(id, jpeg, "dos.jpg")).hasStatus(HttpStatus.OK);

		String nueva = categoriaRepository.findById(id.longValue()).orElseThrow().getImagen();
		assertThat(nueva).isNotEqualTo(anterior).endsWith(".jpg");
		assertThat(Files.exists(imagenes.directorio().resolve(anterior))).isFalse();
		assertThat(Files.exists(imagenes.directorio().resolve(nueva))).isTrue();
	}

	// HU-06 RF-7: un PDF con nombre de foto
	@Test
	void imagenQueNoEsJpgPngNiWebpSeRechaza() throws Exception {
		Integer id = idDe(crear(tokenAdmin, "Pintura", null));

		MvcTestResult subida = subirImagen(id, "%PDF-1.7".getBytes(), "foto.jpg");

		assertThat(subida).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(subida).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_INVALIDA");
	}

	// HU-06 RF-9, RF-13
	@Test
	void borraUnaCategoriaYDespuesNoLaEncuentra() throws Exception {
		Integer id = idDe(crear(tokenAdmin, "Plomería", null));
		subirImagen(id, PNG, "foto.png");

		assertThat(mvc.delete().uri(GESTION + "/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.exchange()).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult otraVez = mvc.delete().uri(GESTION + "/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
		assertThat(otraVez).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(otraVez).bodyJson().extractingPath("$.codigo").isEqualTo("CATEGORIA_NO_ENCONTRADA");
	}

	// HU-06 RF-13: un identificador que no es un número no termina en 500
	@Test
	void identificadorBasuraNoEsUnErrorDelServidor() {
		MvcTestResult resultado = mvc.get().uri("/api/categorias/abc/imagen").exchange();

		assertThat(resultado.getResponse().getStatus()).isBetween(400, 499);
	}
}
