package bo.edu.uagrm.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import bo.edu.uagrm.tienda.config.AdministradorInicialProperties;
import bo.edu.uagrm.tienda.config.CatalogoDemoProperties;
import bo.edu.uagrm.tienda.config.ImagenesProperties;
import bo.edu.uagrm.tienda.dto.EntradaRequest;
import bo.edu.uagrm.tienda.dto.ProductoResponse;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.AdministradorInicial;
import bo.edu.uagrm.tienda.service.AlmacenImagenes;
import bo.edu.uagrm.tienda.service.CatalogoDemo;
import bo.edu.uagrm.tienda.service.CategoriaService;
import bo.edu.uagrm.tienda.service.InventarioService;
import bo.edu.uagrm.tienda.service.ProductoService;
import jakarta.validation.Validator;

// Issue #25: la carga del catálogo de demostración desde una carpeta con catalogo.csv y sus imágenes
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CatalogoDemoIntegracionTest {

	private static final String ENCABEZADO =
			"categoria,nombre,descripcion,precio_bs,stock_inicial,imagenes,modelo_3d,destacado,unidad\n";
	private static final String ADMIN = "admin@tienda.test";

	@Autowired
	private CatalogoDemo catalogoDemoDelContexto;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private AdministradorInicialProperties administrador;

	@Autowired
	private CategoriaService categoriaService;

	@Autowired
	private ProductoService productoService;

	@Autowired
	private InventarioService inventarioService;

	@Autowired
	private AlmacenImagenes almacen;

	@Autowired
	private ImagenesProperties imagenes;

	@Autowired
	private Validator validator;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@TempDir
	private Path carpeta;

	@BeforeEach
	void preparar() throws IOException {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
		limpiar();
		for (String archivo : new String[] { "catalogo.csv", "martillo.png", "pala.png", "pala-detalle.png",
				"martillo.glb" }) {
			try (InputStream origen = getClass().getResourceAsStream("/demo/" + archivo)) {
				Files.copy(origen, carpeta.resolve(archivo));
			}
		}
	}

	// También las imágenes y los modelos que guardó la carga: la carpeta de las pruebas es compartida
	@AfterEach
	void limpiar() {
		transaccion.execute(estado -> productoRepository.findAll().stream()
				.flatMap(producto -> Stream.concat(producto.getImagenes().stream(), Stream.ofNullable(producto.getModelo())))
				.toList()).forEach(almacen::borrar);
		movimientoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
	}

	private void cargar(Path directorio) {
		cargar(directorio, administrador, inventarioService);
	}

	private void cargar(Path directorio, AdministradorInicialProperties admin, InventarioService inventario) {
		new CatalogoDemo(new CatalogoDemoProperties(directorio), admin, categoriaService, productoService, inventario,
				productoRepository, categoriaRepository, usuarioRepository, validator, transaccion)
				.run(new DefaultApplicationArguments());
	}

	private long imagenesGuardadas() throws IOException {
		try (Stream<Path> archivos = Files.list(imagenes.directorio())) {
			return archivos.count();
		}
	}

	private List<Long> usuariosDeLosMovimientos() {
		return transaccion.execute(estado -> movimientoRepository.findAll().stream()
				.map(m -> m.getUsuario().getIdUsuario()).distinct().toList());
	}

	private void escribirCatalogo(String filas) throws IOException {
		Files.writeString(carpeta.resolve("catalogo.csv"), ENCABEZADO + filas, StandardCharsets.UTF_8);
	}

	private Map<String, ProductoResponse> productosPorNombre() {
		return productoRepository.findAll().stream().map(p -> productoService.obtener(p.getIdProducto()))
				.collect(Collectors.toMap(ProductoResponse::nombre, Function.identity()));
	}

	@Test
	void sinLaPropiedadNoCargaNada() {
		catalogoDemoDelContexto.run(new DefaultApplicationArguments());

		assertThat(productoRepository.count()).isZero();
		assertThat(categoriaRepository.count()).isZero();
	}

	@Test
	void cargaLasCategoriasQueFaltanLosProductosSusImagenesYSuStock() {
		Categoria jardin = categoriaRepository.save(Categoria.crear("Jardín", null));

		cargar(carpeta);

		// «jardin» del CSV es la categoría «Jardín» que ya existía
		assertThat(categoriaRepository.findAll()).extracting(Categoria::getNombre)
				.containsExactlyInAnyOrder("Jardín", "Herramientas manuales");
		Map<String, ProductoResponse> productos = productosPorNombre();
		assertThat(productos).hasSize(3);

		ProductoResponse martillo = productos.get("Martillo de uña 16 oz");
		assertThat(martillo.categoria()).isEqualTo("Herramientas manuales");
		assertThat(martillo.codigo()).startsWith("HER-");
		assertThat(martillo.descripcion()).isEqualTo("Mango de fibra de vidrio y cabeza forjada.");
		assertThat(martillo.precio()).isEqualByComparingTo("85.50");
		assertThat(martillo.unidad()).isEqualTo(UnidadVenta.UNIDAD);
		assertThat(martillo.marca()).isNull();
		assertThat(martillo.ficha()).isEmpty();
		assertThat(martillo.destacado()).isTrue();
		assertThat(martillo.activo()).isTrue();
		assertThat(martillo.imagenes()).hasSize(1);
		assertThat(almacen.leer(martillo.imagenes().getFirst())).isPresent();
		// HU-15 RF-13: el modelo de la columna modelo_3d
		assertThat(martillo.modelo().peso()).isEqualTo(48);
		assertThat(almacen.leer(martillo.modelo().archivo())).isPresent();

		ProductoResponse pala = productos.get("Pala de punta");
		assertThat(pala.idCategoria()).isEqualTo(jardin.getIdCategoria());
		assertThat(pala.imagenes()).hasSize(2);

		assertThat(pala.destacado()).isFalse();
		assertThat(pala.modelo()).isNull();

		ProductoResponse cinta = productos.get("Cinta métrica 5 m");
		assertThat(cinta.imagenes()).isEmpty();

		assertThat(inventarioService.obtener(martillo.idProducto()).stock()).isEqualByComparingTo("12");
		assertThat(inventarioService.obtener(pala.idProducto()).stock()).isEqualByComparingTo("4");
		assertThat(inventarioService.obtener(cinta.idProducto()).stock()).isEqualByComparingTo("0");
		// El stock inicial entra como un movimiento, a nombre del administrador inicial; el de 0 no registra nada
		Long idAdmin = usuarioRepository.findByCorreo(ADMIN).orElseThrow().getIdUsuario();
		List<String> movimientos = transaccion.execute(estado -> movimientoRepository.findAll().stream()
				.map(m -> m.getTipo() + " " + m.getCantidad().stripTrailingZeros().toPlainString() + " "
						+ m.getUsuario().getIdUsuario())
				.toList());
		assertThat(movimientos).containsExactlyInAnyOrder("ENTRADA 12 " + idAdmin, "ENTRADA 4 " + idAdmin);
	}

	@Test
	void unaSegundaCorridaNoDuplicaNada(CapturedOutput salida) {
		cargar(carpeta);
		cargar(carpeta);

		assertThat(productoRepository.count()).isEqualTo(3);
		assertThat(categoriaRepository.count()).isEqualTo(2);
		assertThat(movimientoRepository.findAll()).extracting(MovimientoStock::getTipo)
				.containsOnly(TipoMovimiento.ENTRADA).hasSize(2);
		assertThat(salida.getOut()).contains("ya hay productos");
	}

	@Test
	void unaFilaInvalidaNoCargaNadaYElLogDiceLaFilaYElMotivo(CapturedOutput salida) throws IOException {
		byte[] grande = new byte[(int) DataSize.ofMegabytes(2).toBytes() + 1];
		System.arraycopy(Files.readAllBytes(carpeta.resolve("pala.png")), 0, grande, 0, 8);
		Files.write(carpeta.resolve("grande.png"), grande);
		Files.writeString(carpeta.resolve("texto.png"), "no es una imagen");
		escribirCatalogo("""
				Herramientas manuales,Martillo,Bueno.,85.50,12,martillo.png,,,
				Herramientas manuales,Serrucho,Bueno.,doce,3,,,,
				Herramientas manuales,Taladro,Bueno.,450,2.5,,,,
				Herramientas manuales,Alicate,Bueno.,60,3,no-existe.png,,,
				Herramientas manuales,Llave,Bueno.,60,3,grande.png,,,
				Herramientas manuales,Nivel,Bueno.,60,3,texto.png,,,
				Herramientas manuales,Lima,Bueno.,60,3,../catalogo.csv,,,
				Herramientas manuales,Escuadra,Con, una coma.,60,3,,,,
				Herramientas manuales,Pinza,Bueno.,0,3,,,,
				Herramientas manuales,Pala,Bueno.,60,3,pala.png|pala.png,,,
				""");

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(categoriaRepository.count()).isZero();
		assertThat(salida.getOut())
				.contains("Fila 3: precio_bs")
				.contains("Fila 4: stock_inicial")
				.contains("Fila 5: imagenes: no-existe.png")
				.contains("Fila 6: imagenes: grande.png")
				.contains("Fila 7: imagenes: texto.png")
				.contains("Fila 8: imagenes: ../catalogo.csv no es un archivo de la carpeta")
				.contains("Fila 9: tiene 10 columnas")
				.contains("Fila 10: precio_bs")
				.contains("Fila 11: imagenes: pala.png está repetida")
				.doesNotContain("Fila 2:")
				.contains("No se cargó el catálogo de demostración");
	}

	// HU-15 RF-13: un modelo que no está, que no es glb o que pasa de 3 MB invalida su fila, como una imagen
	@Test
	void unModeloInvalidoNoCargaNada(CapturedOutput salida) throws IOException {
		byte[] glb = Files.readAllBytes(carpeta.resolve("martillo.glb"));
		byte[] grande = new byte[(int) DataSize.ofMegabytes(3).toBytes() + 1];
		System.arraycopy(glb, 0, grande, 0, glb.length);
		Files.write(carpeta.resolve("grande.glb"), grande);
		Files.copy(carpeta.resolve("pala.png"), carpeta.resolve("pala.glb"));
		escribirCatalogo("""
				Herramientas manuales,Martillo,Bueno.,85.50,12,,martillo.glb,,
				Herramientas manuales,Alicate,Bueno.,60,3,,no-existe.glb,,
				Herramientas manuales,Llave,Bueno.,60,3,,grande.glb,,
				Herramientas manuales,Pala,Bueno.,60,3,,pala.glb,,
				Herramientas manuales,Lima,Bueno.,60,3,,../catalogo.csv,,
				""");

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(salida.getOut())
				.contains("Fila 3: modelo_3d: no-existe.glb no existe en la carpeta")
				.contains("Fila 4: modelo_3d: grande.glb pasa de 3 MB")
				.contains("Fila 5: modelo_3d: pala.glb no es un modelo .glb")
				.contains("Fila 6: modelo_3d: ../catalogo.csv no es un archivo de la carpeta")
				.doesNotContain("Fila 2:");
	}

	@Test
	void unaCarpetaSinCatalogoNoArranca() {
		assertThatThrownBy(() -> cargar(carpeta.resolve("no-existe")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TIENDA_DEMO_DIRECTORIO");
		assertThat(productoRepository.count()).isZero();
	}

	@Test
	void conElEncabezadoEquivocadoNoCargaNada(CapturedOutput salida) throws IOException {
		Files.writeString(carpeta.resolve("catalogo.csv"), "nombre,precio\nMartillo,85\n", StandardCharsets.UTF_8);

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(salida.getOut()).contains("Fila 1:");
	}

	@Test
	void destacadoYUnidadSonOpcionales() throws IOException {
		escribirCatalogo("""
				Electricidad,Cable 2.5 mm,Cobre flexible.,8.50,120.5,,,,metro
				Electricidad,Tornillos 1 pulgada,Cien por caja.,25,10,,,SI,Caja
				Electricidad,Foco LED,Luz cálida.,15,8,,,,
				""");

		cargar(carpeta);

		Map<String, ProductoResponse> productos = productosPorNombre();
		ProductoResponse cable = productos.get("Cable 2.5 mm");
		assertThat(cable.unidad()).isEqualTo(UnidadVenta.METRO);
		assertThat(cable.destacado()).isFalse();
		assertThat(inventarioService.obtener(cable.idProducto()).stock()).isEqualByComparingTo("120.5");
		ProductoResponse tornillos = productos.get("Tornillos 1 pulgada");
		assertThat(tornillos.unidad()).isEqualTo(UnidadVenta.CAJA);
		assertThat(tornillos.destacado()).isTrue();
		assertThat(productos.get("Foco LED").unidad()).isEqualTo(UnidadVenta.UNIDAD);
	}

	@Test
	void unDestacadoOUnaUnidadQueNoSeReconocenNoCarganNada(CapturedOutput salida) throws IOException {
		escribirCatalogo("""
				Electricidad,Cable,Cobre.,8.50,12,,,talvez,
				Electricidad,Pintura,Látex.,80,3,,,,galon
				Electricidad,Foco,Luz.,15,2.5,,,,caja
				""");

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(salida.getOut())
				.contains("Fila 2: destacado: «talvez»")
				.contains("Fila 3: unidad: «galon»")
				.contains("Fila 4: stock_inicial: tiene que ser un número entero");
	}

	@Test
	void elPrecioAdmiteHastaDosDecimales(CapturedOutput salida) throws IOException {
		escribirCatalogo("Herramientas manuales,Martillo,Bueno.,85.505,1,,,,\n");

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(salida.getOut()).contains("Fila 2: precio_bs: admite hasta dos decimales");
	}

	@Test
	void unFalloDuranteLaCargaSeRevierteEntero(CapturedOutput salida) throws IOException {
		// Falla el último paso de la primera fila, con su categoría, su producto y su imagen ya guardados
		InventarioService inventarioQueFalla = mock(InventarioService.class);
		given(inventarioQueFalla.registrarEntrada(any(), any(), any(EntradaRequest.class)))
				.willThrow(new IllegalStateException("falla de prueba"));
		long antes = imagenesGuardadas();

		cargar(carpeta, administrador, inventarioQueFalla);

		assertThat(productoRepository.count()).isZero();
		assertThat(categoriaRepository.count()).isZero();
		assertThat(imagenesGuardadas()).isEqualTo(antes);
		assertThat(salida.getOut()).contains("No se cargó el catálogo de demostración")
				.contains("Fila 2: falla de prueba");
	}

	@Test
	void sinLaCuentaDeTiendaAdminCorreoElStockVaAOtroAdministradorActivo() {
		cargar(carpeta, new AdministradorInicialProperties("ya-no-existe@tienda.test", "clave-admin-pruebas", null, null),
				inventarioService);

		assertThat(productoRepository.count()).isEqualTo(3);
		assertThat(usuariosDeLosMovimientos())
				.containsExactly(usuarioRepository.findByCorreo(ADMIN).orElseThrow().getIdUsuario());
	}

	@Test
	void elStockNuncaQuedaANombreDeUnCliente() {
		Usuario cliente = usuarioRepository.save(Usuario.registrarCliente("Ana", "Pérez", "ana-demo@mail.com",
				"hash", null, LocalDateTime.of(2026, 10, 17, 9, 0)));
		try {
			cargar(carpeta, new AdministradorInicialProperties("ana-demo@mail.com", "clave-admin-pruebas", null, null),
					inventarioService);

			assertThat(usuariosDeLosMovimientos())
					.containsExactly(usuarioRepository.findByCorreo(ADMIN).orElseThrow().getIdUsuario());
		} finally {
			limpiar();
			usuarioRepository.delete(cliente);
		}
	}

	@Test
	void unPrecioInvalidoNoOcultaLosDemasErroresDeLaFila(CapturedOutput salida) throws IOException {
		escribirCatalogo(",,%s,doce,3,,,,\n".formatted("a".repeat(1001)));

		cargar(carpeta);

		assertThat(salida.getOut())
				.contains("Fila 2: categoria: es obligatorio")
				.contains("Fila 2: nombre: es obligatorio")
				.contains("Fila 2: descripcion: admite hasta 1000 caracteres")
				.contains("Fila 2: precio_bs: «doce» no es un número");
	}

	@Test
	void unEnlaceSimbolicoHaciaAfueraDeLaCarpetaSeRechaza(CapturedOutput salida, @TempDir Path afuera)
			throws IOException {
		Path foto = Files.copy(carpeta.resolve("pala.png"), afuera.resolve("privada.png"));
		try {
			Files.createSymbolicLink(carpeta.resolve("enlace.png"), foto);
		} catch (IOException | UnsupportedOperationException e) {
			// Windows no deja crear enlaces sin el modo de desarrollador: ahí la prueba no aplica
			Assumptions.abort("El sistema no permite crear enlaces simbólicos: " + e.getMessage());
		}
		escribirCatalogo("Herramientas manuales,Martillo,Bueno.,85.50,12,enlace.png,,,\n");

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(salida.getOut()).contains("Fila 2: imagenes: enlace.png no es un archivo de la carpeta");
	}

	@Test
	void conProductosYaCargadosUnaCarpetaMalConfiguradaIgualNoArranca() {
		cargar(carpeta);

		assertThatThrownBy(() -> cargar(carpeta.resolve("no-existe")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TIENDA_DEMO_DIRECTORIO");
	}

	@Test
	void unaCategoriaOcultaNoRecibeProductos(CapturedOutput salida) {
		Categoria jardin = Categoria.crear("Jardín", null);
		jardin.ocultar();
		categoriaRepository.save(jardin);

		cargar(carpeta);

		assertThat(productoRepository.count()).isZero();
		assertThat(salida.getOut()).contains("Fila 3: categoria: «jardin» es una categoría oculta");
	}
}
