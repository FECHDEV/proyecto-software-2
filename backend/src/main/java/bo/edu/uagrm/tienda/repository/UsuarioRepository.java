package bo.edu.uagrm.tienda.repository;

import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	boolean existsByCorreo(String correo);

	Optional<Usuario> findByCorreo(String correo);

	// Serializa las operaciones sobre la misma cuenta, como dos solicitudes de recuperación simultáneas
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Usuario> findConBloqueoByCorreo(String correo);

	// Dos cambios de contraseña simultáneos: el segundo espera y compara contra el hash que dejó el primero
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Usuario> findConBloqueoByIdUsuario(Long idUsuario);

	// Siempre en el mismo orden: dos operaciones simultáneas de la gestión de usuarios se esperan, y la segunda cuenta los
	// administradores que dejó la primera (decisiones.md → Gestión de usuarios)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<Usuario> findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol rol, EstadoCuenta estado);

	// Un null no filtra. El texto llega como patrón LIKE en minúsculas con '!' como escape: la barra invertida también
	// escapa dentro de los literales de MySQL
	@Query("""
			select u from Usuario u
			where (:rol is null or u.rol = :rol)
				and (:estado is null or u.estado = :estado)
				and (:texto is null or lower(u.correo) like :texto escape '!' or lower(u.nombre) like :texto escape '!'
					or lower(u.apellido) like :texto escape '!')
			""")
	Page<Usuario> buscar(@Param("rol") Rol rol, @Param("estado") EstadoCuenta estado, @Param("texto") String texto,
			Pageable paginacion);

	boolean existsByRol(Rol rol);

	// correo es la única restricción UNIQUE de la tabla usuario
	static boolean esCorreoDuplicado(DataIntegrityViolationException ex) {
		for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacion) {
				return violacion.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
			}
		}
		return false;
	}
}
