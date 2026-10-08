package bo.edu.uagrm.tienda.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.InicioSesionRequest;
import bo.edu.uagrm.tienda.dto.InicioSesionResponse;
import bo.edu.uagrm.tienda.dto.MensajeResponse;
import bo.edu.uagrm.tienda.dto.RegistroClienteRequest;
import bo.edu.uagrm.tienda.dto.RestablecimientoContrasenaRequest;
import bo.edu.uagrm.tienda.dto.SolicitudRecuperacionRequest;
import bo.edu.uagrm.tienda.dto.UsuarioResponse;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import bo.edu.uagrm.tienda.service.RecuperacionContrasenaService;
import bo.edu.uagrm.tienda.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final MensajeResponse RECUPERACION_SOLICITADA = new MensajeResponse(
			"Si el correo está registrado, le enviamos las instrucciones para recuperar la contraseña.");
	private static final MensajeResponse CONTRASENA_RESTABLECIDA = new MensajeResponse(
			"La contraseña se actualizó. Ya puede iniciar sesión con la nueva contraseña.");

	private final UsuarioService usuarioService;
	private final AutenticacionService autenticacionService;
	private final RecuperacionContrasenaService recuperacionContrasenaService;

	@PostMapping("/registro")
	public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroClienteRequest solicitud) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UsuarioResponse.from(usuarioService.registrarCliente(solicitud)));
	}

	// La IP alimenta el límite de intentos fallidos. Detrás de un proxy inverso, la IP real requiere configurar el
	// reenvío de cabeceras al desplegar (decisiones.md → Inicio de sesión)
	@PostMapping("/inicio-sesion")
	public InicioSesionResponse iniciarSesion(@Valid @RequestBody InicioSesionRequest solicitud,
			HttpServletRequest peticion) {
		return InicioSesionResponse.from(autenticacionService.iniciarSesion(solicitud, peticion.getRemoteAddr()));
	}

	// Misma respuesta exista o no la cuenta. La IP alimenta el límite de solicitudes, con la misma salvedad del
	// proxy inverso que el inicio de sesión
	@PostMapping("/recuperacion")
	public ResponseEntity<MensajeResponse> solicitarRecuperacion(
			@Valid @RequestBody SolicitudRecuperacionRequest solicitud, HttpServletRequest peticion) {
		recuperacionContrasenaService.solicitar(solicitud, peticion.getRemoteAddr());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(RECUPERACION_SOLICITADA);
	}

	@PostMapping("/recuperacion/restablecimiento")
	public MensajeResponse restablecerContrasena(@Valid @RequestBody RestablecimientoContrasenaRequest solicitud) {
		recuperacionContrasenaService.restablecer(solicitud);
		return CONTRASENA_RESTABLECIDA;
	}
}
