package bo.edu.uagrm.tienda.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import bo.edu.uagrm.tienda.entity.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

	List<Categoria> findAllByOrderByNombreClave();

	List<Categoria> findByVisibleTrueOrderByNombreClave();

	boolean existsByNombreClave(String nombreClave);

	boolean existsByNombreClaveAndIdCategoriaNot(String nombreClave, Long idCategoria);

	// Serializa los cambios de imagen y el borrado de la misma categoría: el segundo ve la imagen que dejó el primero,
	// y ningún archivo queda sin una fila que lo nombre
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Categoria> findConBloqueoByIdCategoria(Long idCategoria);

	// La única restricción única de la tabla es la del nombre: otro error de integridad no es un duplicado
	static boolean esNombreDuplicado(DataIntegrityViolationException ex) {
		return ViolacionesDeIntegridad.esViolacionUnica(ex);
	}
}
