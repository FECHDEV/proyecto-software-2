package bo.edu.uagrm.tienda.dto;

import bo.edu.uagrm.tienda.entity.Usuario;

// La cuenta de Cliente a cuyo nombre queda una venta del mostrador (HU-23 RF-4, RF-15)
public record CompradorResponse(Long idUsuario, String nombre, String correo) {

	public static CompradorResponse from(Usuario usuario) {
		return new CompradorResponse(usuario.getIdUsuario(), usuario.getNombre() + " " + usuario.getApellido(),
				usuario.getCorreo());
	}

	// HU-23 RF-26: la búsqueda por nombre nunca devuelve el correo completo
	public static CompradorResponse conCorreoOculto(Usuario usuario) {
		return new CompradorResponse(usuario.getIdUsuario(), usuario.getNombre() + " " + usuario.getApellido(),
				ocultar(usuario.getCorreo()));
	}

	// Hasta dos caracteres de la parte local, nunca la parte entera, y el dominio completo
	static String ocultar(String correo) {
		int arroba = correo.lastIndexOf('@');
		if (arroba < 1) {
			return "***";
		}
		return correo.substring(0, Math.min(2, arroba - 1)) + "***" + correo.substring(arroba);
	}
}
