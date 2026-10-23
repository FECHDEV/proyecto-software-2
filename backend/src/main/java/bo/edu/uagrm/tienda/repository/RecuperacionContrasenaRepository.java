package bo.edu.uagrm.tienda.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.RecuperacionContrasena;
import bo.edu.uagrm.tienda.entity.Usuario;
import jakarta.persistence.LockModeType;

public interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasena, Long> {

	// Sin bloqueo y sin cargar el enlace: restablecer bloquea primero la cuenta y recién después el enlace
	// (RecuperacionContrasenaService)
	@Query("select r.usuario.idUsuario from RecuperacionContrasena r where r.hashToken = :hashToken")
	Optional<Long> idUsuarioDelToken(@Param("hashToken") String hashToken);

	// Dos restablecimientos simultáneos con el mismo token no pueden usarlo dos veces
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RecuperacionContrasena> findByHashToken(String hashToken);

	List<RecuperacionContrasena> findByUsuarioAndUsadoFalse(Usuario usuario);
}
