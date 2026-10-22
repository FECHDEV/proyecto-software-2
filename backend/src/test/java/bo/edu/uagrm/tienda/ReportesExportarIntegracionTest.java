package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

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

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
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
import bo.edu.uagrm.tienda.service.ReporteCompleto;
import bo.edu.uagrm.tienda.service.ReportesService;

// HU-19: el reporte completo que se exporta, sin paginar, y GET /api/reportes/exportar de punta a punta. Las ventas
// usan fechas de 2025; los clientes inactivos y los sin venderse, el reloj de la aplicación
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesExportarIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final List<String> CORREOS = List.of("ana.hu19@mail.com", "fede.hu19@mail.com",
			"emp.hu19@mail.com", "conta.hu19@mail.com", "cli.hu19@mail.com");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private ReportesService reportesService;

	@Autowired
	private PedidoRepository pedidoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private Clock clock;

	private Usuario ana;
	private Usuario fede;
	private Categoria herramientas;
	private LocalDateTime ahora;

	@BeforeEach
	void preparar() {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		ana = cliente("ana.hu19@mail.com", TipoCliente.PUBLICO_GENERAL);
		fede = cliente("fede.hu19@mail.com", TipoCliente.CONTRATISTA);
		herramientas = categoriaRepository.save(Categoria.crear("Herramientas", null));
		ahora = LocalDateTime.now(clock);
	}

	@AfterEach
	void limpiar() {
		pedidoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		CORREOS.forEach(correo -> usuarioRepository.findByCorreo(correo).ifPresent(usuarioRepository::delete));
	}

	private Usuario cliente(String correo, TipoCliente tipo) {
		Usuario usuario = Usuario.registrarCliente(correo.substring(0, 4), "Paz", correo, "x", "70000000",
				LocalDateTime.of(2025, 1, 1, 9, 0));
		usuario.cambiarTipo(tipo);
		return usuarioRepository.save(usuario);
	}

	private Producto producto(String nombre, String precio, LocalDateTime alta) {
		Producto producto = Producto.crear(nombre, null, null, new BigDecimal(precio), UnidadVenta.UNIDAD,
				herramientas, List.of(), alta);
		producto.aplicarMovimiento(BigDecimal.TEN);
		producto = productoRepository.save(producto);
		producto.asignarCodigo("HER-%04d".formatted(producto.getIdProducto()));
		return productoRepository.save(producto);
	}

	private Producto producto(String nombre, String precio) {
		return producto(nombre, precio, LocalDateTime.of(2025, 1, 1, 9, 0));
	}

	// Pares producto, cantidad
	private Pedido pagado(Usuario cliente, LocalDateTime pago, Object... lineas) {
		Pedido pedido = Pedido.confirmar(cliente, "Ana Paz", "70000000", null, pago.minusMinutes(5),
				pago.plusMinutes(25));
		for (int i = 0; i < lineas.length; i += 2) {
			pedido.agregarLinea((Producto) lineas[i], new BigDecimal(lineas[i + 1].toString()));
		}
		pedido.pagar(pago);
		return pedidoRepository.save(pedido);
	}

	private static LocalDateTime octubre(int dia) {
		return LocalDateTime.of(2025, 10, dia, 10, 0);
	}

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	// Se registra como «Carla Paz» y, si hace falta, el Administrador le da otro rol
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
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ADMIN, CLAVE_ADMIN))
					.contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"%s\"}".formatted(rol)).exchange())
					.hasStatus(HttpStatus.OK);
		}
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private MvcTestResult exportar(String token, String consulta) {
		return mvc.get().uri("/api/reportes/exportar?" + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange();
	}

	private Long idAdmin() {
		return usuarioRepository.findByCorreo(ADMIN).orElseThrow().getIdUsuario();
	}

	// HU-19 RF-7: las listas van enteras; la pantalla las ve de a 10
	@Test
	void completoTraeLasListasSinPaginar() {
		List<Producto> productos = IntStream.rangeClosed(1, 12)
				.mapToObj(i -> producto("Producto %02d".formatted(i), String.valueOf(10 + i))).toList();
		productos.forEach(producto -> pagado(ana, octubre(2), producto, 1));

		ReporteCompleto reporte = reportesService.completo("2025-10-01", "2025-10-22", null, null, idAdmin());

		assertThat(reporte.masVendidos()).hasSize(12);
		assertThat(reporte.menosVendidos()).hasSize(12);
		assertThat(reportesService.productos("2025-10-01", "2025-10-22", null, false, 0).contenido()).hasSize(10);
	}

	// HU-19 RF-6: las mismas cifras que los endpoints de la pantalla
	@Test
	void completoTieneLasMismasCifrasQueLaPantalla() {
		Producto llave = producto("Llave", "10");
		Producto taladro = producto("Taladro", "100");
		pagado(ana, octubre(2), llave, 5);
		pagado(fede, octubre(3), taladro, 1);

		ReporteCompleto reporte = reportesService.completo("2025-10-01", "2025-10-22", null, null, idAdmin());

		assertThat(reporte.ventas()).isEqualTo(reportesService.ventas("2025-10-01", "2025-10-22", null));
		assertThat(reporte.perdidos()).isEqualTo(reportesService.resumenDePerdidos("2025-10-01", "2025-10-22", null));
		assertThat(reporte.masVendidos())
				.isEqualTo(reportesService.productos("2025-10-01", "2025-10-22", null, false, 0).contenido());
		assertThat(reporte.masVendidos()).extracting(ProductoVendido::nombre).containsExactly("Taladro", "Llave");
		assertThat(reporte.autor()).isNotBlank();
		assertThat(reporte.generado()).isNotNull();
	}

	// HU-19 RF-2: los días y el tipo de la pantalla
	@Test
	void completoUsaLosDiasYElTipo() {
		Producto viejo = producto("Rastrillo", "20", ahora.minusDays(100));
		Producto medio = producto("Pala", "50", ahora.minusDays(200));
		pagado(ana, ahora.minusDays(45), medio, 1);
		pagado(fede, ahora.minusDays(70), viejo, 1);

		ReporteCompleto reporte = reportesService.completo(null, null, TipoCliente.CONTRATISTA, "60", idAdmin());

		assertThat(reporte.dias()).isEqualTo(60);
		assertThat(reporte.tipo()).isEqualTo(TipoCliente.CONTRATISTA);
		assertThat(reporte.sinVenta()).extracting("nombre").containsExactly("Rastrillo");
		assertThat(reporte.clientesInactivos()).extracting("correo").containsExactly("fede.hu19@mail.com");
	}

	// HU-19 RF-1, RF-4, RF-5: el Excel como descarga, sin guardarse en cachés, con quien lo pidió
	@Test
	void exportarExcelComoContador() throws Exception {
		pagado(ana, octubre(2), producto("Llave", "10"), 5);
		String contador = cuentaCon("conta.hu19@mail.com", "CONTADOR");

		MvcTestResult archivo = exportar(contador, "formato=xlsx&desde=2025-10-01&hasta=2025-10-22");

		assertThat(archivo).hasStatus(HttpStatus.OK);
		assertThat(archivo).hasContentType(
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		assertThat(archivo).hasHeader(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"reporte-ventas-2025-10-01-a-2025-10-22.xlsx\"");
		assertThat(archivo).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.getResponse().getContentAsByteArray()))) {
			DataFormatter formateador = new DataFormatter();
			List<String> textos = new java.util.ArrayList<>();
			for (Row fila : libro.getSheet("Resumen")) {
				fila.forEach(celda -> textos.add(formateador.formatCellValue(celda)));
			}
			assertThat(textos).contains("Carla Paz", "01/10/2025 al 22/10/2025");
			assertThat(libro.getSheet("Más vendidos").getRow(2).getCell(1).getStringCellValue()).isEqualTo("Llave");
		}
	}

	// HU-19 RF-1, RF-4, RF-5: el formato se lee sin importar mayúsculas
	@Test
	void exportarPdfComoAdministrador() throws Exception {
		MvcTestResult archivo = exportar(token(ADMIN, CLAVE_ADMIN), "formato=PDF&desde=2025-10-01&hasta=2025-10-22");

		assertThat(archivo).hasStatus(HttpStatus.OK);
		assertThat(archivo).hasContentType(MediaType.APPLICATION_PDF);
		assertThat(archivo).hasHeader(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"reporte-ventas-2025-10-01-a-2025-10-22.pdf\"");
		Usuario admin = usuarioRepository.findByCorreo(ADMIN).orElseThrow();
		try (PdfReader lector = new PdfReader(archivo.getResponse().getContentAsByteArray())) {
			assertThat(new PdfTextExtractor(lector).getTextFromPage(1))
					.contains(admin.getNombre() + " " + admin.getApellido());
		}
	}

	// HU-19 RF-1
	@Test
	void empleadoYClienteReciben403() throws Exception {
		assertThat(exportar(cuentaCon("emp.hu19@mail.com", "EMPLEADO"), "formato=xlsx"))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(exportar(cuentaCon("cli.hu19@mail.com", null), "formato=pdf")).hasStatus(HttpStatus.FORBIDDEN);
	}

	// HU-19 RF-1
	@Test
	void sinSesionRecibe401() {
		assertThat(mvc.get().uri("/api/reportes/exportar?formato=xlsx").exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// HU-19 RF-3
	@Test
	void formatoInvalidoEs400() throws Exception {
		String admin = token(ADMIN, CLAVE_ADMIN);
		for (String consulta : List.of("formato=doc", "formato=", "desde=2025-10-01&hasta=2025-10-22")) {
			MvcTestResult respuesta = exportar(admin, consulta);
			assertThat(respuesta).as(consulta).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(respuesta).bodyJson().extractingPath("$.codigo").isEqualTo("FORMATO_INVALIDO");
		}
	}

	// HU-19 RF-3
	@Test
	void periodoInvalidoEs400() throws Exception {
		MvcTestResult respuesta = exportar(token(ADMIN, CLAVE_ADMIN), "formato=xlsx&desde=xx&hasta=2025-10-22");

		assertThat(respuesta).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(respuesta).bodyJson().extractingPath("$.codigo").isEqualTo("PERIODO_INVALIDO");
	}

	// HU-19 RF-3: como en HU-18
	@Test
	void tipoODiasInvalidosEs400() throws Exception {
		String admin = token(ADMIN, CLAVE_ADMIN);
		assertThat(exportar(admin, "formato=xlsx&tipo=zz")).hasStatus(HttpStatus.BAD_REQUEST);
		MvcTestResult dias = exportar(admin, "formato=xlsx&dias=7");
		assertThat(dias).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(dias).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
	}

	// HU-19 RF-2: sin período, este mes
	@Test
	void sinPeriodoUsaEsteMes() throws Exception {
		java.time.LocalDate hoy = ahora.toLocalDate();

		MvcTestResult archivo = exportar(token(ADMIN, CLAVE_ADMIN), "formato=xlsx");

		assertThat(archivo).hasHeader(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"reporte-ventas-%s-a-%s.xlsx\"".formatted(hoy.withDayOfMonth(1), hoy));
	}
}
