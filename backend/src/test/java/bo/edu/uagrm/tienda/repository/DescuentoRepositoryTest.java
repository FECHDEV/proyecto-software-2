package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Descuento;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;

// HU-24: los descuentos vigentes, los que se cruzan y la lista de la gestión
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DescuentoRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 18, 9, 0);
	private static final LocalDate HOY = LocalDate.of(2026, 11, 18);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private DescuentoRepository descuentoRepository;

	private Usuario admin;
	private Categoria herramientas;
	private Producto martillo;
	private Producto alicate;

	@BeforeEach
	void preparar() {
		herramientas = em.persist(Categoria.crear("Herramientas HU-24", null));
		martillo = em.persist(Producto.crear("Martillo", null, null, new BigDecimal("100.00"), UnidadVenta.UNIDAD,
				herramientas, List.of(), AHORA));
		martillo.asignarCodigo("HER-H24-" + martillo.getIdProducto());
		alicate = em.persist(Producto.crear("Alicate", null, null, new BigDecimal("40.00"), UnidadVenta.UNIDAD,
				herramientas, List.of(), AHORA));
		alicate.asignarCodigo("HER-H24-" + alicate.getIdProducto());
		admin = em.persist(Usuario.crearAdministrador("Ada", "Admin", "ada.hu24@mail.com", "$2a$10$hash", AHORA));
		em.flush();
	}

	private Descuento deMartillo(int porcentaje, LocalDate inicio, LocalDate fin) {
		return em.persist(Descuento.deProducto(martillo, porcentaje, inicio, fin, admin, AHORA));
	}

	private Descuento deHerramientas(int porcentaje, LocalDate inicio, LocalDate fin) {
		return em.persist(Descuento.deCategoria(herramientas, porcentaje, inicio, fin, admin, AHORA));
	}

	private void guardar() {
		em.flush();
		em.clear();
	}

	// HU-24 RF-10: el primer y el último día cuentan
	@Test
	void vigentesIncluyeElPrimerYElUltimoDia() {
		Descuento empiezaHoy = deMartillo(10, HOY, HOY.plusDays(5));
		Descuento terminaHoy = deHerramientas(20, HOY.minusDays(5), HOY);
		deMartillo(30, HOY.plusDays(6), HOY.plusDays(8));
		deHerramientas(40, HOY.minusDays(9), HOY.minusDays(6));
		guardar();

		assertThat(descuentoRepository.vigentes(HOY)).extracting(Descuento::getIdDescuento)
				.containsExactlyInAnyOrder(empiezaHoy.getIdDescuento(), terminaHoy.getIdDescuento());
	}

	// HU-24 RF-2: el fin de uno el mismo día que el inicio del otro ya se cruza; el día siguiente, no
	@Test
	void cruzadosEncuentraElQueSeTocaUnDia() {
		Descuento existente = deMartillo(10, HOY, HOY.plusDays(5));
		guardar();

		assertThat(descuentoRepository.cruzados(martillo.getIdProducto(), null, HOY.plusDays(5), HOY.plusDays(9), null))
				.extracting(Descuento::getIdDescuento).containsExactly(existente.getIdDescuento());
		assertThat(descuentoRepository.cruzados(martillo.getIdProducto(), null, HOY.plusDays(6), HOY.plusDays(9), null))
				.isEmpty();
		assertThat(descuentoRepository.cruzados(alicate.getIdProducto(), null, HOY, HOY.plusDays(5), null)).isEmpty();
	}

	// HU-24 RF-2: un descuento del producto no se cruza con uno de su categoría (RF-9 decide cuál gana)
	@Test
	void cruzadosNoMezclaProductoConCategoria() {
		Descuento deProducto = deMartillo(10, HOY, HOY.plusDays(5));
		Descuento deCategoria = deHerramientas(20, HOY, HOY.plusDays(5));
		guardar();

		assertThat(descuentoRepository.cruzados(null, herramientas.getIdCategoria(), HOY, HOY.plusDays(5), null))
				.extracting(Descuento::getIdDescuento).containsExactly(deCategoria.getIdDescuento());
		assertThat(descuentoRepository.cruzados(martillo.getIdProducto(), null, HOY, HOY.plusDays(5), null))
				.extracting(Descuento::getIdDescuento).containsExactly(deProducto.getIdDescuento());
	}

	// HU-24 RF-4: al editar, no se cruza consigo mismo
	@Test
	void cruzadosSinSiMismoAlEditar() {
		Descuento existente = deMartillo(10, HOY.plusDays(1), HOY.plusDays(5));
		guardar();

		assertThat(descuentoRepository.cruzados(martillo.getIdProducto(), null, HOY.plusDays(2), HOY.plusDays(6),
				existente.getIdDescuento())).isEmpty();
	}

	// HU-24 RF-3: primero los vigentes, después los programados y al final los vencidos
	@Test
	void listarVigentesProgramadosYVencidos() {
		Descuento vencido = deHerramientas(40, HOY.minusDays(9), HOY.minusDays(6));
		Descuento programado = deMartillo(30, HOY.plusDays(6), HOY.plusDays(8));
		Descuento vigente = deMartillo(10, HOY, HOY.plusDays(5));
		guardar();

		List<Descuento> lista = descuentoRepository.listar(HOY, PageRequest.of(0, 20)).getContent();

		assertThat(lista).extracting(Descuento::getIdDescuento).containsExactly(vigente.getIdDescuento(),
				programado.getIdDescuento(), vencido.getIdDescuento());
	}
}
