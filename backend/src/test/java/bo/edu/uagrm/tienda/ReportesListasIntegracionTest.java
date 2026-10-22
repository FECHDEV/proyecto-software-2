package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;

// HU-18: las listas paginadas de /api/reportes (productos, sin venderse, clientes inactivos y perdidos), de punta a
// punta. Las del período usan fechas de 2025; las que miran hacia atrás desde hoy, la hora real, como el servicio
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesListasIntegracionTest {

	private static final String ADMIN = "admin@tienda.test";
	private static final String CLAVE_ADMIN = "clave-admin-pruebas";
	private static final List<String> CORREOS = List.of("ana.hu18l@mail.com", "beto.hu18l@mail.com",
			"carla.hu18l@mail.com", "dani.hu18l@mail.com", "eli.hu18l@mail.com", "fede.hu18l@mail.com",
			"emp.hu18l@mail.com", "conta.hu18l@mail.com", "eli2.hu18l@mail.com");
	private static final String OCTUBRE = "desde=2025-10-01&hasta=2025-10-22";

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
	private Usuario fede;
	private Categoria herramientas;
	private LocalDateTime ahora;

	@BeforeEach
	void preparar() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		admin = token(ADMIN, CLAVE_ADMIN);
		contador = cuentaCon("conta.hu18l@mail.com", "CONTADOR");
		ana = cliente("ana.hu18l@mail.com", TipoCliente.PUBLICO_GENERAL);
		fede = cliente("fede.hu18l@mail.com", TipoCliente.CONTRATISTA);
		herramientas = categoriaRepository.save(Categoria.crear("Herramientas", null));
		ahora = LocalDateTime.now();
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

	private String cuentaCon(String correo, String rol) throws Exception {
		MvcTestResult registro = mvc.post().uri("/api/auth/registro").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Carla\",\"apellido\":\"Paz\",\"correo\":\"%s\",\"contrasena\":\"secreta12\"}"
						.formatted(correo))
				.exchange();
		assertThat(registro).hasStatus(HttpStatus.CREATED);
		Integer idUsuario = JsonPath.read(registro.getResponse().getContentAsString(), "$.usuario.idUsuario");
		assertThat(mvc.put().uri("/api/usuarios/{id}/rol", idUsuario).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"%s\"}".formatted(rol)).exchange())
				.hasStatus(HttpStatus.OK);
		return JsonPath.read(registro.getResponse().getContentAsString(), "$.token");
	}

	private Usuario cliente(String correo, TipoCliente tipo) {
		Usuario usuario = Usuario.registrarCliente(correo.substring(0, 4), "Paz", correo, "x", "70000000",
				LocalDateTime.of(2025, 1, 1, 9, 0));
		usuario.cambiarTipo(tipo);
		return usuarioRepository.save(usuario);
	}

	private Producto producto(String nombre, String precio, String costo, UnidadVenta unidad, String stock,
			LocalDateTime alta) {
		Producto producto = Producto.crear(nombre, null, null, new BigDecimal(precio), unidad, herramientas, List.of(),
				alta);
		producto.cambiarCosto(costo == null ? null : new BigDecimal(costo));
		producto.aplicarMovimiento(new BigDecimal(stock));
		producto = productoRepository.save(producto);
		producto.asignarCodigo("HER-%04d".formatted(producto.getIdProducto()));
		return productoRepository.save(producto);
	}

	private Producto producto(String nombre, String precio) {
		return producto(nombre, precio, null, UnidadVenta.UNIDAD, "10", LocalDateTime.of(2025, 1, 1, 9, 0));
	}

	// Pares producto, cantidad
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

	private Pedido vencido(Usuario cliente, LocalDateTime cuando, Object... lineas) {
		Pedido pedido = confirmado(cliente, cuando, lineas);
		pedido.vencer(cuando.plusMinutes(30));
		return pedidoRepository.save(pedido);
	}

	private static LocalDateTime octubre(int dia) {
		return LocalDateTime.of(2025, 10, dia, 10, 0);
	}

	private MvcTestResult get(String token, String uri) {
		return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	// HU-18 RF-11: por importe, con los inactivos que se vendieron; cada fila con la unidad de su producto (no se
	// suman unidades distintas)
	@Test
	void masVendidosPorImporteConInactivos() throws Exception {
		Producto llave = producto("Llave", "10");
		Producto taladro = producto("Taladro", "100");
		Producto cable = producto("Cable", "12", null, UnidadVenta.METRO, "100", LocalDateTime.of(2025, 1, 1, 9, 0));
		producto("Sierra", "70");
		pagado(ana, octubre(2), llave, 5, cable, "2.5");
		pagado(ana, octubre(3), taladro, 1);
		taladro.desactivar();
		productoRepository.save(taladro);

		MvcTestResult mas = get(contador, "/api/reportes/productos?" + OCTUBRE);

		assertThat(mas).hasStatus(HttpStatus.OK);
		assertThat(mas).bodyJson().extractingPath("$.contenido[*].nombre").asArray().containsExactly("Taladro", "Llave",
				"Cable");
		assertThat(mas).bodyJson().extractingPath("$.contenido[0].activo").isEqualTo(false);
		assertThat(mas).bodyJson().extractingPath("$.contenido[0].codigo").isEqualTo(taladro.getCodigo());
		assertThat(mas).bodyJson().extractingPath("$.contenido[0].margen").isNull();
		assertThat(mas).bodyJson().extractingPath("$.contenido[2].unidad").isEqualTo("METRO");
		assertThat(mas).bodyJson().extractingPath("$.contenido[2].cantidad").isEqualTo(2.5);
		assertThat(mas).bodyJson().extractingPath("$.contenido[2].importe").isEqualTo(30.0);
		assertThat(mas).bodyJson().extractingPath("$.totalElementos").isEqualTo(3);
	}

	// HU-18 RF-12: los activos, también los que no vendieron, de menor a mayor
	@Test
	void menosVendidosIncluyeLosSinVentaYSoloActivos() throws Exception {
		Producto llave = producto("Llave", "10");
		Producto taladro = producto("Taladro", "100");
		Producto martillo = producto("Martillo", "85.50", "60", UnidadVenta.UNIDAD, "10",
				LocalDateTime.of(2025, 1, 1, 9, 0));
		producto("Sierra", "70");
		producto("Alicate", "30");
		pagado(ana, octubre(2), llave, 5, martillo, 1);
		pagado(ana, octubre(3), taladro, 1);
		taladro.desactivar();
		productoRepository.save(taladro);

		MvcTestResult menos = get(contador, "/api/reportes/productos?orden=MENOS&" + OCTUBRE);

		assertThat(menos).bodyJson().extractingPath("$.contenido[*].nombre").asArray().containsExactly("Alicate",
				"Sierra", "Llave", "Martillo");
		assertThat(menos).bodyJson().extractingPath("$.contenido[0].importe").isEqualTo(0.0);
		assertThat(menos).bodyJson().extractingPath("$.contenido[0].cantidad").isEqualTo(0.0);
		assertThat(menos).bodyJson().extractingPath("$.contenido[3].margen").isEqualTo(25.5);
	}

	// HU-18 RF-13: el escenario de la carretilla; sin costo, inmovilizado a precio; sin ventas, desde el alta
	@Test
	void sinVentaPorDiasConInmovilizado() throws Exception {
		Producto carretilla = producto("Carretilla", "450", "300", UnidadVenta.UNIDAD, "4", ahora.minusDays(200));
		pagado(ana, ahora.minusDays(75), carretilla, 1);
		producto("Rastrillo", "20", null, UnidadVenta.UNIDAD, "3", ahora.minusDays(100));
		Producto reciente = producto("Pala", "50", null, UnidadVenta.UNIDAD, "5", ahora.minusDays(200));
		pagado(ana, ahora.minusDays(10), reciente, 1);
		// 30 días y 5 horas: se muestran 30 días, que no son «más de 30» (/code-review)
		Producto justo = producto("Hacha", "50", null, UnidadVenta.UNIDAD, "5", ahora.minusDays(200));
		pagado(ana, ahora.minusDays(30).minusHours(5), justo, 1);
		producto("Sin stock", "50", null, UnidadVenta.UNIDAD, "0", ahora.minusDays(200));
		Producto inactivo = producto("Inactivo", "50", null, UnidadVenta.UNIDAD, "5", ahora.minusDays(200));
		inactivo.desactivar();
		productoRepository.save(inactivo);

		MvcTestResult sesenta = get(contador, "/api/reportes/sin-venta?dias=60");

		assertThat(sesenta).hasStatus(HttpStatus.OK);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[*].nombre").asArray().containsExactly("Rastrillo",
				"Carretilla");
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[0].diasSinVenta").isEqualTo(100);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[0].ultimaVenta").isNull();
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[0].inmovilizado").isEqualTo(60.0);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[0].inmovilizadoAPrecio").isEqualTo(true);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[1].diasSinVenta").isEqualTo(75);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[1].ultimaVenta").isNotNull();
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[1].stock").isEqualTo(4.0);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[1].costo").isEqualTo(300.0);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[1].inmovilizado").isEqualTo(1200.0);
		assertThat(sesenta).bodyJson().extractingPath("$.contenido[1].inmovilizadoAPrecio").isEqualTo(false);
		assertThat(get(contador, "/api/reportes/sin-venta?dias=90")).bodyJson().extractingPath("$.contenido[*].nombre")
				.asArray().containsExactly("Rastrillo");
		// De entrada, 30 días
		assertThat(get(contador, "/api/reportes/sin-venta")).bodyJson().extractingPath("$.totalElementos")
				.isEqualTo(2);
	}

	// HU-18 RF-16: compraron alguna vez y no desde hace más de los días pedidos; solo clientes activos; del mayor
	// total al menor; con contacto; el filtro por tipo vale (RF-5)
	@Test
	void clientesInactivos() throws Exception {
		Producto pieza = producto("Pieza", "50");
		pagado(ana, ahora.minusDays(60), pieza, 1);
		pagado(ana, ahora.minusDays(45), pieza, 2);
		pagado(fede, ahora.minusDays(40), pieza, 10);
		Usuario beto = cliente("beto.hu18l@mail.com", TipoCliente.PUBLICO_GENERAL);
		pagado(beto, ahora.minusDays(10), pieza, 1);
		cliente("carla.hu18l@mail.com", TipoCliente.PUBLICO_GENERAL);
		// 30 días y 5 horas: no son «más de 30 días», como en «Sin venderse» (/code-review, ronda 2)
		Usuario justo = cliente("eli2.hu18l@mail.com", TipoCliente.PUBLICO_GENERAL);
		pagado(justo, ahora.minusDays(30).minusHours(5), pieza, 1);
		Usuario dani = cliente("dani.hu18l@mail.com", TipoCliente.PUBLICO_GENERAL);
		pagado(dani, ahora.minusDays(60), pieza, 1);
		dani.desactivar();
		usuarioRepository.save(dani);
		Usuario eli = cliente("eli.hu18l@mail.com", TipoCliente.PUBLICO_GENERAL);
		pagado(eli, ahora.minusDays(60), pieza, 1);
		eli.asignarRol(Rol.EMPLEADO);
		usuarioRepository.save(eli);

		MvcTestResult inactivos = get(contador, "/api/reportes/clientes-inactivos?dias=30");

		assertThat(inactivos).hasStatus(HttpStatus.OK);
		assertThat(inactivos).bodyJson().extractingPath("$.contenido[*].correo").asArray()
				.containsExactly("fede.hu18l@mail.com", "ana.hu18l@mail.com");
		assertThat(inactivos).bodyJson().extractingPath("$.contenido[1].telefono").isEqualTo("70000000");
		assertThat(inactivos).bodyJson().extractingPath("$.contenido[1].tipo").isEqualTo("PUBLICO_GENERAL");
		assertThat(inactivos).bodyJson().extractingPath("$.contenido[1].pedidos").isEqualTo(2);
		assertThat(inactivos).bodyJson().extractingPath("$.contenido[1].total").isEqualTo(150.0);
		assertThat(inactivos).bodyJson().extractingPath("$.contenido[1].ultimaCompra").isNotNull();
		assertThat(get(contador, "/api/reportes/clientes-inactivos?dias=60")).bodyJson()
				.extractingPath("$.totalElementos").isEqualTo(0);
		assertThat(get(contador, "/api/reportes/clientes-inactivos?tipo=CONTRATISTA")).bodyJson()
				.extractingPath("$.contenido[*].correo").asArray().containsExactly("fede.hu18l@mail.com");
	}

	// HU-18 RF-18: los productos de los pedidos vencidos o cancelados confirmados en el período, por importe perdido
	@Test
	void productosDePerdidos() throws Exception {
		Producto llave = producto("Llave", "10");
		Producto taladro = producto("Taladro", "100");
		vencido(ana, octubre(2), llave, 2, taladro, 1);
		Pedido cancelado = confirmado(ana, octubre(3), llave, 1);
		cancelado.cancelar(octubre(3).plusMinutes(5));
		pedidoRepository.save(cancelado);
		pagado(ana, octubre(4), llave, 50);
		vencido(ana, LocalDateTime.of(2025, 9, 30, 10, 0), taladro, 9);

		MvcTestResult perdidos = get(contador, "/api/reportes/perdidos?" + OCTUBRE);

		assertThat(perdidos).hasStatus(HttpStatus.OK);
		assertThat(perdidos).bodyJson().extractingPath("$.contenido[*].nombre").asArray().containsExactly("Taladro",
				"Llave");
		assertThat(perdidos).bodyJson().extractingPath("$.contenido[1].cantidad").isEqualTo(3.0);
		assertThat(perdidos).bodyJson().extractingPath("$.contenido[1].importe").isEqualTo(30.0);
	}

	// HU-18 RF-5: el tipo limita los productos vendidos y los perdidos
	@Test
	void productosYPerdidosFiltranPorTipo() throws Exception {
		Producto llave = producto("Llave", "10");
		Producto taladro = producto("Taladro", "100");
		pagado(ana, octubre(2), taladro, 1);
		pagado(fede, octubre(3), llave, 1);
		vencido(ana, octubre(4), taladro, 1);
		vencido(fede, octubre(5), llave, 2);

		String tipo = "&tipo=CONTRATISTA";
		assertThat(get(contador, "/api/reportes/productos?" + OCTUBRE + tipo)).bodyJson()
				.extractingPath("$.contenido[*].nombre").asArray().containsExactly("Llave");
		assertThat(get(contador, "/api/reportes/perdidos?" + OCTUBRE + tipo)).bodyJson()
				.extractingPath("$.contenido[*].importe").asArray().containsExactly(20.0);
	}

	// HU-18 RF-3: los días son 30, 60 o 90
	@Test
	void diasInvalidosEs400() {
		for (String uri : List.of("/api/reportes/sin-venta?dias=7", "/api/reportes/clientes-inactivos?dias=xx")) {
			MvcTestResult rechazado = get(contador, uri);
			assertThat(rechazado).as(uri).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(rechazado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("dias");
		}
		assertThat(get(contador, "/api/reportes/productos?desde=2025-10-10&hasta=2025-10-01")).bodyJson()
				.extractingPath("$.codigo").isEqualTo("PERIODO_INVALIDO");
	}

	// HU-18 RF-3: de a 10; una página después de la última es la última, y una que no es un número, la primera
	@Test
	void paginaFueraDeRangoDaLaUltima() throws Exception {
		for (int i = 1; i <= 12; i++) {
			pagado(ana, octubre(2), producto("Producto %02d".formatted(i), String.valueOf(i)), 1);
		}

		MvcTestResult ultima = get(contador, "/api/reportes/productos?pagina=9&" + OCTUBRE);

		assertThat(ultima).bodyJson().extractingPath("$.pagina").isEqualTo(1);
		assertThat(ultima).bodyJson().extractingPath("$.tamano").isEqualTo(10);
		assertThat(ultima).bodyJson().extractingPath("$.totalElementos").isEqualTo(12);
		assertThat(ultima).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
		assertThat(ultima).bodyJson().extractingPath("$.contenido[*].nombre").asArray().containsExactly("Producto 02",
				"Producto 01");
		assertThat(get(contador, "/api/reportes/productos?pagina=xx&" + OCTUBRE)).bodyJson().extractingPath("$.pagina")
				.isEqualTo(0);
	}

	// HU-18 RF-1
	@Test
	void listasSoloContadorYAdministrador() throws Exception {
		String empleado = cuentaCon("emp.hu18l@mail.com", "EMPLEADO");
		for (String uri : List.of("/api/reportes/productos", "/api/reportes/sin-venta",
				"/api/reportes/clientes-inactivos", "/api/reportes/perdidos")) {
			assertThat(get(empleado, uri)).as(uri).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.get().uri(uri).exchange()).as(uri).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(get(admin, uri)).as(uri).hasStatus(HttpStatus.OK);
		}
	}
}
