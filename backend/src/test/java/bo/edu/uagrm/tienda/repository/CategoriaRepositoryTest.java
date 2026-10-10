package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.Categoria;

// HU-06: categorías del catálogo
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoriaRepositoryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@BeforeEach
	void vaciar() {
		categoriaRepository.deleteAll();
		em.flush();
	}

	// HU-06 RF-2
	@Test
	void guardaYLeeTodasLasColumnas() {
		Categoria jardin = Categoria.crear("Jardín", "Para el patio y la huerta");
		jardin.cambiarImagen("3f2b1c4d-0000-4000-8000-000000000001.jpg");
		Long id = em.persistAndGetId(jardin, Long.class);
		em.flush();
		em.clear();

		Categoria leida = categoriaRepository.findById(id).orElseThrow();

		assertThat(leida.getNombre()).isEqualTo("Jardín");
		assertThat(leida.getNombreClave()).isEqualTo("jardin");
		assertThat(leida.getDescripcion()).isEqualTo("Para el patio y la huerta");
		assertThat(leida.getImagen()).isEqualTo("3f2b1c4d-0000-4000-8000-000000000001.jpg");
		assertThat(leida.isVisible()).isTrue();
	}

	// HU-06 RF-4: sin importar mayúsculas ni tildes; la restricción única cubre también dos altas simultáneas
	@Test
	void nombreRepetidoSinImportarMayusculasSeReconoceComoDuplicado() {
		em.persist(Categoria.crear("Jardín", null));
		em.flush();

		assertThat(categoriaRepository.existsByNombreClave("jardin")).isTrue();
		assertThatThrownBy(() -> categoriaRepository.saveAndFlush(Categoria.crear("  JARDÍN ", null)))
				.isInstanceOfSatisfying(DataIntegrityViolationException.class,
						ex -> assertThat(CategoriaRepository.esNombreDuplicado(ex)).isTrue());
	}

	// HU-06 RF-12
	@Test
	void listaTodasEnOrdenAlfabetico() {
		em.persist(Categoria.crear("Pintura", null));
		Categoria plomeria = Categoria.crear("Plomería", null);
		plomeria.ocultar();
		em.persist(plomeria);
		em.persist(Categoria.crear("Herramientas eléctricas", null));
		em.flush();
		em.clear();

		assertThat(categoriaRepository.findAllByOrderByNombreClave()).extracting(Categoria::getNombre)
				.containsExactly("Herramientas eléctricas", "Pintura", "Plomería");
	}

	// HU-06 RF-11: el catálogo solo ve las visibles
	@Test
	void soloVisiblesEnOrdenAlfabetico() {
		em.persist(Categoria.crear("Pintura", null));
		Categoria plomeria = Categoria.crear("Plomería", null);
		plomeria.ocultar();
		em.persist(plomeria);
		em.persist(Categoria.crear("Jardín", null));
		em.flush();
		em.clear();

		assertThat(categoriaRepository.findByVisibleTrueOrderByNombreClave()).extracting(Categoria::getNombre)
				.containsExactly("Jardín", "Pintura");
	}
}
