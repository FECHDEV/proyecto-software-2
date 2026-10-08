package bo.edu.uagrm.tienda.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.CambioContrasenaRequest;
import bo.edu.uagrm.tienda.dto.DatosPersonalesRequest;
import bo.edu.uagrm.tienda.dto.InicioSesionResponse;
import bo.edu.uagrm.tienda.dto.UsuarioResponse;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import bo.edu.uagrm.tienda.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// La cuenta es siempre la de la sesión: el id sale del token (JwtFiltroAutenticacion), nunca de la petición
@RestController
@RequestMapping("/api/cuenta")
@RequiredArgsConstructor
public class CuentaController {

	private final UsuarioService usuarioService;
	private final AutenticacionService autenticacionService;

	@GetMapping("/datos-personales")
	public UsuarioResponse consultarDatos(@AuthenticationPrincipal Long idUsuario) {
		return UsuarioResponse.from(usuarioService.datosPersonales(idUsuario));
	}

	@PutMapping("/datos-personales")
	public UsuarioResponse actualizarDatos(@AuthenticationPrincipal Long idUsuario,
			@Valid @RequestBody DatosPersonalesRequest solicitud) {
		return UsuarioResponse.from(usuarioService.actualizarDatos(idUsuario, solicitud));
	}

	// La IP alimenta el límite de intentos del inicio de sesión, con la misma salvedad del proxy inverso
	@PutMapping("/contrasena")
	public InicioSesionResponse cambiarContrasena(@AuthenticationPrincipal Long idUsuario,
			@Valid @RequestBody CambioContrasenaRequest solicitud, HttpServletRequest peticion) {
		return InicioSesionResponse
				.from(autenticacionService.cambiarContrasena(idUsuario, solicitud, peticion.getRemoteAddr()));
	}
}
