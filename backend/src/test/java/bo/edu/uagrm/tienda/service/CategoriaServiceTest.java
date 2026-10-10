package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.dto.CategoriaRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.exception.CategoriaExistenteException;
import bo.edu.uagrm.tienda.exception.CategoriaNoEncontradaException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;

// HU-06: gestión de categorías
@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

	private static final String IMAGEN = "3f2b1c4d-0000-4000-8000-000000000001.jpg";

	@Mock
	private CategoriaRepository categoriaRepository;

	@Mock
	private AlmacenImagenes almacen;

	private CategoriaService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new CategoriaService(categoriaRepository, almacen);
	}

	private void guardaComoViene() {
		given(categoriaRepository.saveAndFlush(any(Categoria.class))).willAnswer(invocacion -> invocacion.getArgument(0));
	}

	private Categoria existente(long id, String nombre) {
		Categoria categoria = Categoria.crear(nombre, null);
		ReflectionTestUtils.setField(categoria, "idCategoria", id);
		given(categoriaRepository.findById(id)).willReturn(Optional.of(categoria));
		return categoria;
	}

	// Borrar y cambiar la imagen bloquean la fila (dos a la vez no dejan archivos huérfanos)
	private Categoria bloqueada(long id, String nombre) {
		Categoria categoria = Categoria.crear(nombre, null);
		ReflectionTestUtils.setField(categoria, "idCategoria", id);
		given(categoriaRepository.findConBloqueoByIdCategoria(id)).willReturn(Optional.of(categoria));
		return categoria;
	}

	private static DataIntegrityViolationException duplicado() {
		return new DataIntegrityViolationException("duplicado", new ConstraintViolationException("duplicado",
				new SQLException("Duplicate entry", "23000", 1062), ConstraintViolationException.ConstraintKind.UNIQUE,
				"uk_categoria_nombre_clave"));
	}

	// HU-06 RF-2, RF-3
	@Test
	void creaVisibleConNombreYDescripcionRecortados() {
		guardaComoViene();

		Categoria creada = servicio.crear(new CategoriaRequest("  Herramientas eléctricas ", "  Taladros y amoladoras "));

		assertThat(creada.getNombre()).isEqualTo("Herramientas eléctricas");
		assertThat(creada.getDescripcion()).isEqualTo("Taladros y amoladoras");
		assertThat(creada.isVisible()).isTrue();
	}

	// HU-06 RF-4
	@Test
	void nombreRepetidoSeRechazaSinImportarMayusculas() {
		given(categoriaRepository.existsByNombreClave("jardin")).willReturn(true);

		assertThatThrownBy(() -> servicio.crear(new CategoriaRequest(" JARDÍN ", null)))
				.isInstanceOf(CategoriaExistenteException.class);
		then(categoriaRepository).should(never()).saveAndFlush(any(Categoria.class));
	}

	// HU-06 RF-4: la otra llegó entre la consulta y el guardado
	@Test
	void dosCreacionesSimultaneasTerminanComoCategoriaExistente() {
		given(categoriaRepository.saveAndFlush(any(Categoria.class))).willThrow(duplicado());

		assertThatThrownBy(() -> servicio.crear(new CategoriaRequest("Jardín", null)))
				.isInstanceOf(CategoriaExistenteException.class);
	}

	// HU-06 RF-5
	@Test
	void editarAlMismoNombreConOtrasMayusculasSePermite() {
		Categoria jardin = existente(4, "jardín");
		given(categoriaRepository.existsByNombreClaveAndIdCategoriaNot("jardin", 4L)).willReturn(false);
		guardaComoViene();

		servicio.editar(4L, new CategoriaRequest("Jardín", "Patio y huerta"));

		assertThat(jardin.getNombre()).isEqualTo("Jardín");
		assertThat(jardin.getDescripcion()).isEqualTo("Patio y huerta");
	}

	// HU-06 RF-4, RF-5
	@Test
	void renombrarAlNombreDeOtraSeRechaza() {
		existente(4, "Jardín");
		given(categoriaRepository.existsByNombreClaveAndIdCategoriaNot("pintura", 4L)).willReturn(true);

		assertThatThrownBy(() -> servicio.editar(4L, new CategoriaRequest("Pintura", null)))
				.isInstanceOf(CategoriaExistenteException.class);
	}

	// HU-06 RF-8
	@Test
	void ocultarYMostrar() {
		Categoria jardin = existente(4, "Jardín");

		servicio.cambiarVisibilidad(4L, false);
		assertThat(jardin.isVisible()).isFalse();

		servicio.cambiarVisibilidad(4L, true);
		assertThat(jardin.isVisible()).isTrue();
	}

	// HU-06 RF-9
	@Test
	void borrarQuitaLaFilaYLaImagen() {
		Categoria plomeria = bloqueada(5, "Plomería");
		plomeria.cambiarImagen(IMAGEN);

		servicio.borrar(5L);

		then(categoriaRepository).should().delete(plomeria);
		then(almacen).should().borrar(IMAGEN);
	}

	// HU-06 RF-9: sin imagen no hay archivo que borrar
	@Test
	void borrarSinImagenNoTocaLosArchivos() {
		bloqueada(5, "Plomería");

		servicio.borrar(5L);

		then(almacen).shouldHaveNoInteractions();
	}

	// HU-06 RF-7: un PDF con nombre de foto
	@Test
	void imagenQueNoEsJpgPngNiWebpSeRechazaSinTocarLaCategoria() {
		assertThatThrownBy(() -> servicio.cambiarImagen(6L, "%PDF-1.7 no es una imagen".getBytes()))
				.isInstanceOf(ImagenInvalidaException.class);
		then(categoriaRepository).shouldHaveNoInteractions();
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-06 RF-13
	@Test
	void categoriaInexistente() {
		given(categoriaRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarVisibilidad(99L, false))
				.isInstanceOfSatisfying(CategoriaNoEncontradaException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("CATEGORIA_NO_ENCONTRADA"));
	}
}
