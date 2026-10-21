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
import bo.edu.uagrm.tienda.dto.VerificacionCorreoRequest;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import bo.edu.uagrm.tienda.service.RecuperacionContrasenaService;
import bo.edu.uagrm.tienda.service.UsuarioService;
import bo.edu.uagrm.tienda.service.VerificacionCorreoService;
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
	private static final MensajeResponse CORREO_VERIFICADO = new MensajeResponse("Su correo quedó verificado.");

	private final UsuarioService usuarioService;
	private final AutenticacionService autenticacionService;
	private final RecuperacionContrasenaService recuperacionContrasenaService;
	private final VerificacionCorreoService verificacionCorreoService;

	// Responde con la sesión iniciada, como el inicio de sesión. La IP alimenta el límite de registros, con la misma
	// salvedad del proxy inverso
	@PostMapping("/registro")
	public ResponseEntity<InicioSesionResponse> registrar(@Valid @RequestBody RegistroClienteRequest solicitud,
			HttpServletRequest peticion) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(usuarioService.registrarCliente(solicitud, peticion.getRemoteAddr()).respuesta());
	}

	// La IP alimenta el límite de intentos fallidos. Detrás de un proxy inverso, la IP real requiere configurar el
	// reenvío de cabeceras al desplegar (decisiones.md → Inicio de sesión)
	@PostMapping("/inicio-sesion")
	public InicioSesionResponse iniciarSesion(@Valid @RequestBody InicioSesionRequest solicitud,
			HttpServletRequest peticion) {
		return autenticacionService.iniciarSesion(solicitud, peticion.getRemoteAddr()).respuesta();
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

	// Pública: el enlace del correo funciona sin sesión y no la inicia (HU-20 RF-4)
	@PostMapping("/verificacion")
	public MensajeResponse verificarCorreo(@Valid @RequestBody VerificacionCorreoRequest solicitud) {
		verificacionCorreoService.verificar(solicitud.token());
		return CORREO_VERIFICADO;
	}
}
