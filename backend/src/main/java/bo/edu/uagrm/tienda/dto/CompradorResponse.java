package bo.edu.uagrm.tienda.dto;

import bo.edu.uagrm.tienda.entity.Usuario;

// La cuenta de Cliente a cuyo nombre queda una venta del mostrador (HU-23 RF-4, RF-15)
public record CompradorResponse(Long idUsuario, String nombre, String correo) {

	public static CompradorResponse from(Usuario usuario) {
		return new CompradorResponse(usuario.getIdUsuario(), usuario.getNombre() + " " + usuario.getApellido(),
				usuario.getCorreo());
	}
}
