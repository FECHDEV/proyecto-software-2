package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.dto.OrdenCatalogo;
import bo.edu.uagrm.tienda.entity.Caracteristica;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;

// HU-07: productos
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductoRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 10, 9, 0);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	private Categoria herramientas;
	private Categoria jardin;

	@BeforeEach
	void preparar() {
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
		herramientas = em.persist(Categoria.crear("Herramientas", null));
		jardin = em.persist(Categoria.crear("Jardín", null));
		em.flush();
	}

	private Producto producto(String nombre, String marca, Categoria categoria, String codigo, int minutos) {
		Producto producto = Producto.crear(nombre, marca, null, new BigDecimal("10.00"), UnidadVenta.UNIDAD, categoria,
				List.of(), AHORA.plusMinutes(minutos));
		em.persist(producto);
		producto.asignarCodigo(codigo);
		return producto;
	}

	// HU-07 RF-2, RF-6, RF-8
	@Test
	void guardaYLeeTodasLasColumnasConFichaEImagenesEnOrden() {
		Producto taladro = Producto.crear("Taladro percutor 750 W", "Bosch", "Para concreto y madera",
				new BigDecimal("420.00"), UnidadVenta.UNIDAD, herramientas,
				List.of(new Caracteristica("Potencia", "750 W"), new Caracteristica("Mandril", "13 mm")), AHORA);
		taladro.agregarImagen("3f2b1c4d-0000-4000-8000-000000000001.jpg");
		taladro.agregarImagen("3f2b1c4d-0000-4000-8000-000000000002.png");
		Long id = em.persistAndGetId(taladro, Long.class);
		taladro.asignarCodigo("HER-0001");
		em.flush();
		em.clear();

		Producto leido = productoRepository.findById(id).orElseThrow();

		assertThat(leido.getCodigo()).isEqualTo("HER-0001");
		assertThat(leido.getNombre()).isEqualTo("Taladro percutor 750 W");
		assertThat(leido.getMarca()).isEqualTo("Bosch");
		assertThat(leido.getPrecio()).isEqualByComparingTo("420.00");
		assertThat(leido.getUnidad()).isEqualTo(UnidadVenta.UNIDAD);
		assertThat(leido.getCategoria().getNombre()).isEqualTo("Herramientas");
		assertThat(leido.isActivo()).isTrue();
		assertThat(leido.getFicha()).extracting(Caracteristica::getNombre).containsExactly("Potencia", "Mandril");
		assertThat(leido.getImagenes()).containsExactly("3f2b1c4d-0000-4000-8000-000000000001.jpg",
				"3f2b1c4d-0000-4000-8000-000000000002.png");
	}

	// HU-07 RF-10
	@Test
	void buscarCombinaCategoriaEstadoYTextoEnNombreCodigoYMarca() {
		producto("Taladro", "Bosch", herramientas, "HER-0001", 0);
		producto("Bordeadora", "Bosch", jardin, "JAR-0002", 1);
		Producto inactivo = producto("Tijera de podar", "Bosch", jardin, "JAR-0003", 2);
		inactivo.desactivar();
		producto("Manguera", "Tramontina", jardin, "JAR-0004", 3);
		em.flush();
		em.clear();
		PageRequest pagina = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fechaAlta", "idProducto"));

		assertThat(productoRepository.buscar(jardin.getIdCategoria(), true, "%bosch%", pagina))
				.extracting(Producto::getNombre).containsExactly("Bordeadora");
		assertThat(productoRepository.buscar(null, null, "%jar-000%", pagina))
				.extracting(Producto::getNombre).containsExactly("Manguera", "Tijera de podar", "Bordeadora");
		assertThat(productoRepository.buscar(null, null, null, pagina)).hasSize(4);
	}

	// HU-07 RF-12, RF-13
	@Test
	void contarPorCategoriaCuentaActivosEInactivos() {
		producto("Pala", null, jardin, "JAR-0001", 0);
		producto("Rastrillo", null, jardin, "JAR-0002", 1).desactivar();
		em.flush();

		Map<Long, Long> conteo = productoRepository.contarPorCategoria().stream()
				.collect(Collectors.toMap(ProductoRepository.ConteoPorCategoria::idCategoria,
						ProductoRepository.ConteoPorCategoria::productos));

		assertThat(conteo).containsEntry(jardin.getIdCategoria(), 2L).doesNotContainKey(herramientas.getIdCategoria());
		assertThat(productoRepository.existsByCategoria_IdCategoria(jardin.getIdCategoria())).isTrue();
		assertThat(productoRepository.existsByCategoria_IdCategoria(herramientas.getIdCategoria())).isFalse();
	}

	// HU-08 RF-2
	@Test
	void productoNuevoEmpiezaConStockCeroYSinMinimo() {
		Long id = producto("Pala", null, jardin, "JAR-0001", 0).getIdProducto();
		em.flush();
		em.clear();

		Producto leido = productoRepository.findById(id).orElseThrow();

		assertThat(leido.getStock()).isEqualByComparingTo("0");
		assertThat(leido.getStockMinimo()).isNull();
		assertThat(leido.isStockBajo()).isFalse();
	}

	// HU-08 RF-9, RF-10: por nombre, con texto en nombre o código, categoría, estado y solo stock bajo
	@Test
	void inventarioFiltraPorTextoCategoriaEstadoYStockBajo() {
		Producto taladro = producto("Taladro", "Bosch", herramientas, "HER-0001", 0);
		taladro.aplicarMovimiento(new BigDecimal("3"));
		taladro.cambiarMinimo(new BigDecimal("5"));
		Producto pala = producto("Pala", null, jardin, "JAR-0002", 1);
		pala.aplicarMovimiento(new BigDecimal("5"));
		pala.cambiarMinimo(new BigDecimal("5"));
		Producto manguera = producto("Manguera", null, jardin, "JAR-0003", 2);
		manguera.aplicarMovimiento(new BigDecimal("6"));
		manguera.cambiarMinimo(new BigDecimal("5"));
		producto("Azada", null, jardin, "JAR-0004", 3).desactivar();
		em.flush();
		em.clear();
		PageRequest pagina = PageRequest.of(0, 20, Sort.by("nombre", "idProducto"));

		assertThat(productoRepository.inventario(null, null, null, false, pagina)).extracting(Producto::getNombre)
				.containsExactly("Azada", "Manguera", "Pala", "Taladro");
		assertThat(productoRepository.inventario(null, null, null, true, pagina)).extracting(Producto::getNombre)
				.containsExactly("Pala", "Taladro");
		assertThat(productoRepository.inventario(null, jardin.getIdCategoria(), true, false, pagina))
				.extracting(Producto::getNombre).containsExactly("Manguera", "Pala");
		assertThat(productoRepository.inventario("%jar-0004%", null, false, false, pagina))
				.extracting(Producto::getNombre).containsExactly("Azada");
		assertThat(productoRepository.inventario("%tala%", null, null, false, pagina))
				.extracting(Producto::getNombre).containsExactly("Taladro");
	}

	// HU-07 RF-4: el código se exige único en la base, aunque lo asigne el servicio
	@Test
	void codigoRepetidoSeRechaza() {
		producto("Pala", null, jardin, "JAR-0001", 0);
		em.flush();
		Producto otro = Producto.crear("Rastrillo", null, null, BigDecimal.ONE, UnidadVenta.UNIDAD, jardin, List.of(),
				AHORA);
		otro.asignarCodigo("JAR-0001");

		assertThatThrownBy(() -> productoRepository.saveAndFlush(otro))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- HU-09: catálogo público ---

	private Producto enVenta(String nombre, String marca, Categoria categoria, String precio, String stock,
			int minutos) {
		Producto producto = Producto.crear(nombre, marca, null, new BigDecimal(precio), UnidadVenta.UNIDAD, categoria,
				List.of(), AHORA.plusMinutes(minutos));
		em.persist(producto);
		producto.asignarCodigo(categoria.getNombre().substring(0, 3).toUpperCase() + "-" + (1000 + minutos));
		producto.aplicarMovimiento(new BigDecimal(stock));
		return producto;
	}

	private List<String> catalogo(String texto, Long idCategoria, String marca, String precioMin, String precioMax,
			boolean soloDisponibles, OrdenCatalogo orden) {
		return productoRepository
				.catalogo(texto, idCategoria, marca, precioMin == null ? null : new BigDecimal(precioMin),
						precioMax == null ? null : new BigDecimal(precioMax), soloDisponibles, LocalDate.of(2026, 1, 1),
						orden.porPrecio(), PageRequest.of(0, 24, orden.orden()))
				.map(Producto::getNombre).getContent();
	}

	private List<String> todo() {
		return catalogo(null, null, null, null, null, false, OrdenCatalogo.RECIENTES);
	}

	// HU-09 RF-2, HU-06 RF-8
	@Test
	void catalogoSoloListaActivosDeCategoriasVisibles() {
		enVenta("Pala", null, jardin, "50.00", "3", 0);
		enVenta("Azada", null, jardin, "40.00", "3", 1).desactivar();
		enVenta("Taladro", null, herramientas, "420.00", "3", 2);
		herramientas.ocultar();
		em.flush();
		em.clear();

		assertThat(todo()).containsExactly("Pala");
	}

	// HU-09 RF-2, HU-06 RF-8
	@Test
	void catalogoReapareceAlMostrarLaCategoria() {
		enVenta("Taladro", null, herramientas, "420.00", "3", 0);
		herramientas.ocultar();
		em.flush();
		assertThat(todo()).isEmpty();

		categoriaRepository.findById(herramientas.getIdCategoria()).orElseThrow().mostrar();
		em.flush();
		em.clear();

		assertThat(todo()).containsExactly("Taladro");
	}

	// HU-09 RF-8: el patrón llega ya sin tildes, en minúsculas y entre '%'
	@Test
	void catalogoBuscaEnNombreMarcaYCodigoSinTildes() {
		enVenta("Martillo de carpintería", "Truper", herramientas, "85.00", "3", 0);
		enVenta("Regadera", "Tramontina", jardin, "35.00", "3", 1);
		em.flush();
		em.clear();

		assertThat(catalogo("%carpinteria%", null, null, null, null, false, OrdenCatalogo.RECIENTES))
				.containsExactly("Martillo de carpintería");
		assertThat(catalogo("%tramontina%", null, null, null, null, false, OrdenCatalogo.RECIENTES))
				.containsExactly("Regadera");
		assertThat(catalogo("%her-1000%", null, null, null, null, false, OrdenCatalogo.RECIENTES))
				.containsExactly("Martillo de carpintería");
	}

	// HU-09 RF-8: % y _ son texto
	@Test
	void catalogoTomaPorcentajeYGuionBajoComoTexto() {
		enVenta("Descuento 50% pintura", null, jardin, "10.00", "3", 0);
		enVenta("Pintura blanca", null, jardin, "10.00", "3", 1);
		em.flush();
		em.clear();

		assertThat(catalogo("%50!%%", null, null, null, null, false, OrdenCatalogo.RECIENTES))
				.containsExactly("Descuento 50% pintura");
		assertThat(catalogo("%!_%", null, null, null, null, false, OrdenCatalogo.RECIENTES)).isEmpty();
	}

	// HU-09 RF-8
	@Test
	void busquedaVaciaListaTodo() {
		enVenta("Pala", null, jardin, "50.00", "3", 0);
		enVenta("Taladro", null, herramientas, "420.00", "3", 1);
		em.flush();
		em.clear();

		assertThat(todo()).containsExactly("Taladro", "Pala");
	}

	// HU-09 RF-9: la marca llega en minúsculas
	@Test
	void catalogoCombinaCategoriaMarcaPrecioYDisponibles() {
		enVenta("Pala", "Truper", jardin, "50.00", "3", 0);
		enVenta("Rastrillo", "Truper", jardin, "120.00", "3", 1);
		enVenta("Azada", "Truper", jardin, "40.00", "0", 2);
		enVenta("Manguera", "Tramontina", jardin, "60.00", "3", 3);
		enVenta("Martillo", "Truper", herramientas, "85.00", "3", 4);
		em.flush();
		em.clear();

		assertThat(catalogo(null, jardin.getIdCategoria(), "truper", null, "100.00", true, OrdenCatalogo.NOMBRE))
				.containsExactly("Pala");
		assertThat(catalogo(null, jardin.getIdCategoria(), "truper", "45.00", null, false, OrdenCatalogo.NOMBRE))
				.containsExactly("Pala", "Rastrillo");
		assertThat(catalogo(null, null, null, "60.00", "85.00", false, OrdenCatalogo.NOMBRE))
				.containsExactly("Manguera", "Martillo");
	}

	// HU-09 RF-10
	@Test
	void catalogoOrdenaPorRecientesPrecioYNombre() {
		enVenta("Pala", null, jardin, "50.00", "3", 0);
		enVenta("Azada", null, jardin, "40.00", "3", 1);
		enVenta("Manguera", null, jardin, "60.00", "3", 2);
		em.flush();
		em.clear();

		assertThat(catalogo(null, null, null, null, null, false, OrdenCatalogo.RECIENTES))
				.containsExactly("Manguera", "Azada", "Pala");
		assertThat(catalogo(null, null, null, null, null, false, OrdenCatalogo.PRECIO_ASC))
				.containsExactly("Azada", "Pala", "Manguera");
		assertThat(catalogo(null, null, null, null, null, false, OrdenCatalogo.PRECIO_DESC))
				.containsExactly("Manguera", "Pala", "Azada");
		assertThat(catalogo(null, null, null, null, null, false, OrdenCatalogo.NOMBRE))
				.containsExactly("Azada", "Manguera", "Pala");
	}

	// HU-09 RF-10
	@Test
	void catalogoPoneLosAgotadosAlFinalEnCadaOrden() {
		enVenta("Azada", null, jardin, "10.00", "0", 2);
		enVenta("Pala", null, jardin, "50.00", "3", 0);
		enVenta("Manguera", null, jardin, "60.00", "3", 1);
		em.flush();
		em.clear();

		for (OrdenCatalogo orden : OrdenCatalogo.values()) {
			assertThat(catalogo(null, null, null, null, null, false, orden)).as(orden.name()).last()
					.isEqualTo("Azada");
		}
		assertThat(catalogo(null, null, null, null, null, true, OrdenCatalogo.RECIENTES))
				.containsExactly("Manguera", "Pala");
	}

	// HU-09 RF-3, RF-7
	@Test
	void vitrinaDejaAfueraAgotadosEInactivos() {
		enVenta("Pala", null, jardin, "50.00", "3", 0).marcarDestacado(true);
		enVenta("Azada", null, jardin, "40.00", "0", 1).marcarDestacado(true);
		enVenta("Rastrillo", null, jardin, "40.00", "3", 2).desactivar();
		enVenta("Manguera", null, jardin, "60.00", "3", 3);
		enVenta("Taladro", null, herramientas, "420.00", "3", 4);
		em.flush();
		em.clear();
		PageRequest doce = PageRequest.of(0, 12, OrdenCatalogo.RECIENTES.orden());

		assertThat(productoRepository.vitrina(null, false, doce)).extracting(Producto::getNombre)
				.containsExactly("Taladro", "Manguera", "Pala");
		assertThat(productoRepository.vitrina(null, true, doce)).extracting(Producto::getNombre)
				.containsExactly("Pala");
		assertThat(productoRepository.vitrina(jardin.getIdCategoria(), false, doce)).extracting(Producto::getNombre)
				.containsExactly("Manguera", "Pala");
	}

	// HU-10 RF-8: inactivo, de categoría oculta o inexistente, lo mismo; agotado sí
	@Test
	void publicoSoloDevuelveActivosDeCategoriasVisibles() {
		Producto pala = enVenta("Pala", null, jardin, "50.00", "3", 0);
		Producto azada = enVenta("Azada", null, jardin, "40.00", "0", 1);
		Producto rastrillo = enVenta("Rastrillo", null, jardin, "40.00", "3", 2);
		rastrillo.desactivar();
		Producto taladro = enVenta("Taladro", null, herramientas, "420.00", "3", 3);
		herramientas.ocultar();
		em.flush();
		em.clear();

		assertThat(productoRepository.publico(pala.getIdProducto())).map(Producto::getNombre).contains("Pala");
		assertThat(productoRepository.publico(azada.getIdProducto())).isPresent();
		assertThat(productoRepository.publico(rastrillo.getIdProducto())).isEmpty();
		assertThat(productoRepository.publico(taladro.getIdProducto())).isEmpty();
		assertThat(productoRepository.publico(999_999L)).isEmpty();
	}

	// HU-09 RF-9
	@Test
	void marcasSinRepetirNiNulasEnOrden() {
		enVenta("Pala", "Truper", jardin, "50.00", "3", 0);
		enVenta("Martillo", "Truper", herramientas, "85.00", "0", 1);
		enVenta("Regadera", "Tramontina", jardin, "35.00", "3", 2);
		enVenta("Azada", null, jardin, "40.00", "3", 3);
		enVenta("Taladro", "Bosch", jardin, "420.00", "3", 4).desactivar();
		em.flush();
		em.clear();

		assertThat(productoRepository.marcasDelCatalogo()).containsExactly("Tramontina", "Truper");
	}
}
