package bo.edu.uagrm.tienda.dto;

import java.time.Instant;

import bo.edu.uagrm.tienda.service.SesionIniciada;

public record InicioSesionResponse(String token, String tipo, Instant expiracion, UsuarioResponse usuario) {

	public static InicioSesionResponse from(SesionIniciada sesion) {
		return new InicioSesionResponse(sesion.token().token(), "Bearer", sesion.token().expiracion(),
				UsuarioResponse.from(sesion.usuario()));
	}
}
