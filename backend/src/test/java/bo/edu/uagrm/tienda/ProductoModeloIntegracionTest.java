package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.config.ImagenesProperties;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.FormatoModeloTest;
import bo.edu.uagrm.tienda.service.ProductoService;

// HU-15: el modelo 3D de un producto, de punta a punta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductoModeloIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final String GESTION = "/api/gestion/productos";
	private static final String CLIENTE = "modelo-cliente@mail.com";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ProductoService productoService;

	@Autowired
	private TransactionTemplate transacciones;

	@Autowired
	private ImagenesProperties imagenes;

	private String tokenAdmin;
	private Integer idProducto;

	@BeforeEach
	void preparar() throws Exception {
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		tokenAdmin = token(ADMIN, CLAVE_ADMIN);
		MvcTestResult categoria = mvc.post().uri("/api/gestion/categorias")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Herramientas\"}").exchange();
		Integer idCategoria = JsonPath.read(categoria.getResponse().getContentAsString(), "$.idCategoria");
		MvcTestResult creado = mvc.post().uri(GESTION).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Martillo de peña","idCategoria":%d,"unidad":"UNIDAD","precio":45,"ficha":[]}"""
						.formatted(idCategoria))
				.exchange();
		idProducto = JsonPath.read(creado.getResponse().getContentAsString(), "$.idProducto");
	}

	@AfterEach
	void limpiar() {
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		usuarioRepository.findByCorreo(CLIENTE).ifPresent(usuarioRepository::delete);
	}

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	// Un cliente nuevo; con otro rol, se lo asigna el Administrador (la sesión lee el rol del momento)
	private String tokenCon(Rol rol) throws Exception {
		usuarioRepository.findByCorreo(CLIENTE).ifPresent(usuarioRepository::delete);
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(CLIENTE))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		if (rol != Rol.CLIENTE) {
			Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
			assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON)
					.content("{\"rol\":\"%s\"}".formatted(rol)).exchange()).hasStatus(HttpStatus.OK);
		}
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private MvcTestResult subir(String token, byte[] contenido) {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.multipart(HttpMethod.PUT, GESTION + "/{id}/modelo", idProducto)
				.file(new MockMultipartFile("modelo", "martillo.glb", "model/gltf-binary", contenido))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private MvcTestResult quitar() {
		return mvc.delete().uri(GESTION + "/{id}/modelo", idProducto)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
	}

	private static String archivo(MvcTestResult resultado) throws Exception {
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$.modelo.archivo");
	}

	private Set<String> modelosEnLaCarpeta() throws IOException {
		try (Stream<Path> archivos = Files.list(imagenes.directorio())) {
			return archivos.map(ruta -> ruta.getFileName().toString()).filter(nombre -> nombre.endsWith(".glb"))
					.collect(Collectors.toSet());
		}
	}

	// HU-15 RF-1, RF-2, RF-5
	@Test
	void elAdministradorSubeYReemplaza() throws Exception {
		MvcTestResult subido = subir(tokenAdmin, FormatoModeloTest.glb(2048));
		assertThat(subido).hasStatus(HttpStatus.OK);
		assertThat(subido).bodyJson().extractingPath("$.modelo.peso").isEqualTo(2048);
		String primero = archivo(subido);

		String segundo = archivo(subir(tokenAdmin, FormatoModeloTest.glb(4096)));

		assertThat(segundo).isNotEqualTo(primero);
		assertThat(Files.exists(imagenes.directorio().resolve(primero))).isFalse();
		assertThat(Files.exists(imagenes.directorio().resolve(segundo))).isTrue();
		assertThat(mvc.get().uri(GESTION).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange())
				.bodyJson().extractingPath("$.contenido[0].conModelo").isEqualTo(true);
	}

	// HU-15 RF-1
	@Test
	void empleadoContadorYClienteReciben403() throws Exception {
		for (Rol rol : List.of(Rol.EMPLEADO, Rol.CONTADOR, Rol.CLIENTE)) {
			String token = tokenCon(rol);
			assertThat(subir(token, FormatoModeloTest.glb(64))).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.delete().uri(GESTION + "/{id}/modelo", idProducto)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	// HU-15 RF-1
	@Test
	void sinSesionRecibe401() {
		assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.multipart(HttpMethod.PUT, GESTION + "/{id}/modelo", idProducto)
				.file(new MockMultipartFile("modelo", "m.glb", "model/gltf-binary", FormatoModeloTest.glb(64)))))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri(GESTION + "/{id}/modelo", idProducto).exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// HU-15 RF-3, RF-4
	@Test
	void unArchivoDe5MbEsModeloDemasiadoGrandeYUnaFotoEsInvalida() throws Exception {
		MvcTestResult grande = subir(tokenAdmin, FormatoModeloTest.glb(5 * 1024 * 1024));
		assertThat(grande).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(grande).bodyJson().extractingPath("$.codigo").isEqualTo("MODELO_DEMASIADO_GRANDE");

		MvcTestResult foto = subir(tokenAdmin, new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' });
		assertThat(foto).bodyJson().extractingPath("$.codigo").isEqualTo("MODELO_INVALIDO");
		assertThat(productoRepository.findById(idProducto.longValue()).orElseThrow().getModelo()).isNull();
	}

	// HU-15 RF-7
	@Test
	void productoInexistenteEs404() {
		MvcTestResult resultado = mvc.delete().uri(GESTION + "/{id}/modelo", 999_999)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin).exchange();
		assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("PRODUCTO_NO_ENCONTRADO");
	}

	// HU-15 RF-8: sin sesión, con su tipo; el reemplazado y uno ajeno dan 404, y una ruta con «../» ni llega (el
	// firewall de Spring Security la rechaza con 400)
	@Test
	void sirveElModeloConSuTipoYSoloElActual() throws Exception {
		byte[] modelo = FormatoModeloTest.glb(512);
		String primero = archivo(subir(tokenAdmin, modelo));

		MvcTestResult servido = mvc.get().uri("/api/productos/{id}/modelo/{archivo}", idProducto, primero).exchange();
		assertThat(servido).hasStatus(HttpStatus.OK).hasContentType("model/gltf-binary");
		// /code-review: el mismo nombre es siempre el mismo archivo
		assertThat(servido.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("max-age=31536000", "immutable");
		assertThat(servido.getResponse().getContentAsByteArray()).isEqualTo(modelo);

		subir(tokenAdmin, FormatoModeloTest.glb(512));
		assertThat(mvc.get().uri("/api/productos/{id}/modelo/{archivo}", idProducto, primero).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/productos/{id}/modelo/{archivo}", idProducto,
				"5f2c1d3e-0000-4000-8000-000000000000.glb").exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/productos/{id}/modelo/..%2F..%2Fsecreto.glb", idProducto).exchange())
				.hasStatus4xxClientError();
	}

	// HU-15 RF-6
	@Test
	void quitarDevuelveElProductoSinModelo() throws Exception {
		String subido = archivo(subir(tokenAdmin, FormatoModeloTest.glb(256)));

		MvcTestResult quitado = quitar();

		assertThat(quitado).hasStatus(HttpStatus.OK);
		assertThat(quitado).bodyJson().extractingPath("$.modelo").isNull();
		assertThat(Files.exists(imagenes.directorio().resolve(subido))).isFalse();
		assertThat(quitar()).hasStatus(HttpStatus.OK);
	}

	// HU-15 RF-5: si la transacción se revierte, el archivo nuevo se borra y el anterior queda
	@Test
	void siLaBaseFallaBorraElNuevoYDejaElAnterior() throws Exception {
		String anterior = archivo(subir(tokenAdmin, FormatoModeloTest.glb(256)));
		Set<String> antes = modelosEnLaCarpeta();

		transacciones.executeWithoutResult(estado -> {
			productoService.subirModelo(idProducto.longValue(), FormatoModeloTest.glb(256));
			estado.setRollbackOnly();
		});

		assertThat(modelosEnLaCarpeta()).isEqualTo(antes);
		assertThat(productoRepository.findById(idProducto.longValue()).orElseThrow().getModelo()).isEqualTo(anterior);
	}

	// HU-15 RF-5: dos reemplazos a la vez dejan un solo modelo, sin archivos sueltos
	@Test
	void dosReemplazosALaVezDejanUnSoloArchivo() throws Exception {
		Set<String> antes = modelosEnLaCarpeta();

		List<MvcTestResult> resultados = aLaVez(() -> subir(tokenAdmin, FormatoModeloTest.glb(256)),
				() -> subir(tokenAdmin, FormatoModeloTest.glb(256)));

		assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).hasStatus(HttpStatus.OK));
		String actual = productoRepository.findById(idProducto.longValue()).orElseThrow().getModelo();
		Set<String> nuevos = modelosEnLaCarpeta();
		nuevos.removeAll(antes);
		assertThat(nuevos).containsExactly(actual);
	}

	@SafeVarargs
	private List<MvcTestResult> aLaVez(Callable<MvcTestResult>... pedidos) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(pedidos.length);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<MvcTestResult>> futuros = Arrays.stream(pedidos).map(pedido -> hilos.submit(() -> {
				largada.await();
				return pedido.call();
			})).toList();
			largada.countDown();
			List<MvcTestResult> resultados = new ArrayList<>();
			for (Future<MvcTestResult> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			hilos.shutdown();
		}
	}
}
