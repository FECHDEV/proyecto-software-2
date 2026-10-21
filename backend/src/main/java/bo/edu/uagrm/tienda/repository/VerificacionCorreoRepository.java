package bo.edu.uagrm.tienda.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.entity.VerificacionCorreo;
import jakarta.persistence.LockModeType;

public interface VerificacionCorreoRepository extends JpaRepository<VerificacionCorreo, Long> {

	// Sin bloqueo y sin cargar el enlace: verificar bloquea primero la cuenta y recién después el enlace
	// (VerificacionCorreoService)
	@Query("select v.usuario.idUsuario from VerificacionCorreo v where v.hashToken = :hashToken")
	Optional<Long> idUsuarioDelToken(@Param("hashToken") String hashToken);

	// Dos verificaciones simultáneas con el mismo token no pueden usarlo dos veces
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<VerificacionCorreo> findByHashToken(String hashToken);

	List<VerificacionCorreo> findByUsuarioAndUsadoFalse(Usuario usuario);
}
