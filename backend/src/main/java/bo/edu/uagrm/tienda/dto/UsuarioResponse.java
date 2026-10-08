package bo.edu.uagrm.tienda.dto;

import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;

public record UsuarioResponse(Long idUsuario, String nombre, String apellido, String correo, String telefono,
		Rol rol, EstadoCuenta estado, LocalDateTime fechaRegistro) {

	public static UsuarioResponse from(Usuario usuario) {
		return new UsuarioResponse(usuario.getIdUsuario(), usuario.getNombre(), usuario.getApellido(),
				usuario.getCorreo(), usuario.getTelefono(), usuario.getRol(),
				usuario.getEstado(), usuario.getFechaRegistro());
	}
}
