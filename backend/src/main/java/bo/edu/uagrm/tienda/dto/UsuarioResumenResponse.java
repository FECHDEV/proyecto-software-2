package bo.edu.uagrm.tienda.dto;

import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;

// Lo necesario para identificar la cuenta y decidir, sin teléfono ni fecha de nacimiento (RNF-03)
public record UsuarioResumenResponse(Long idUsuario, String nombre, String apellido, String correo, Rol rol,
		EstadoCuenta estado, LocalDateTime fechaRegistro) {

	public static UsuarioResumenResponse from(Usuario usuario) {
		return new UsuarioResumenResponse(usuario.getIdUsuario(), usuario.getNombre(), usuario.getApellido(),
				usuario.getCorreo(), usuario.getRol(), usuario.getEstado(), usuario.getFechaRegistro());
	}
}
