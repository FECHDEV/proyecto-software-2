package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.MotivoAjuste;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;

// HU-08: historial de movimientos de stock
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MovimientoStockRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 12, 9, 0);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	private Producto manguera;
	private Usuario empleado;

	@BeforeEach
	void preparar() {
		movimientoRepository.deleteAll();
		Categoria jardin = em.persist(Categoria.crear("Jardín HU-08", null));
		manguera = Producto.crear("Manguera", null, null, BigDecimal.TEN, UnidadVenta.METRO, jardin, List.of(), AHORA);
		em.persist(manguera);
		manguera.asignarCodigo("JAR-H08-" + manguera.getIdProducto());
		empleado = em.persist(Usuario.registrarCliente("Eva", "Paz", "eva.hu08@mail.com", "$2a$10$hash", null, AHORA));
		em.flush();
	}

	// HU-08 RF-3, RF-11
	@Test
	void guardaYLeeElMovimientoConSuUsuario() {
		Long id = em.persistAndGetId(MovimientoStock.entrada(manguera, new BigDecimal("25.50"), new BigDecimal("25.50"),
				empleado, AHORA, "Factura 123"), Long.class);
		em.flush();
		em.clear();

		MovimientoStock leido = movimientoRepository.findById(id).orElseThrow();

		assertThat(leido.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(leido.getMotivo()).isNull();
		assertThat(leido.getCantidad()).isEqualByComparingTo("25.5");
		assertThat(leido.getStockResultante()).isEqualByComparingTo("25.5");
		assertThat(leido.getUsuario().getNombre()).isEqualTo("Eva");
		assertThat(leido.getFecha()).isEqualTo(AHORA);
		assertThat(leido.getNota()).isEqualTo("Factura 123");
	}

	// HU-08 RF-11
	@Test
	void historialDelMasRecienteAlMasAntiguo() {
		em.persist(MovimientoStock.entrada(manguera, BigDecimal.TEN, BigDecimal.TEN, empleado, AHORA, null));
		em.persist(MovimientoStock.ajuste(manguera, new BigDecimal("-2"), new BigDecimal("8"), new BigDecimal("8"),
				BigDecimal.ZERO, MotivoAjuste.MERMA, empleado, AHORA.plusHours(1), null));
		em.flush();
		em.clear();

		var pagina = movimientoRepository.findByProducto_IdProducto(manguera.getIdProducto(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fecha", "idMovimiento")));

		assertThat(pagina.getContent()).extracting(MovimientoStock::getTipo)
				.containsExactly(TipoMovimiento.AJUSTE, TipoMovimiento.ENTRADA);
		assertThat(pagina.getContent().getFirst().getMotivo()).isEqualTo(MotivoAjuste.MERMA);
		assertThat(pagina.getContent().getFirst().getCantidad()).isEqualByComparingTo("-2");
	}
}
