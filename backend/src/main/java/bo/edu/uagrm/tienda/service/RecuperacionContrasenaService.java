package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.RestablecimientoContrasenaRequest;
import bo.edu.uagrm.tienda.dto.SolicitudRecuperacionRequest;
import bo.edu.uagrm.tienda.entity.RecuperacionContrasena;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.TokenRecuperacionInvalidoException;
import bo.edu.uagrm.tienda.repository.RecuperacionContrasenaRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecuperacionContrasenaService {

	private final UsuarioRepository usuarioRepository;
	private final RecuperacionContrasenaRepository recuperacionRepository;
	private final PasswordEncoder passwordEncoder;
	private final NotificadorCorreo notificadorCorreo;
	private final LimiteSolicitudesRecuperacion limiteSolicitudes;
	private final LimiteIntentos limiteIntentos;
	private final Clock clock;

	// Termina igual exista o no la cuenta, para no revelar qué correos están registrados
	@Transactional
	public void solicitar(SolicitudRecuperacionRequest solicitud, String ip) {
		String correo = Usuario.normalizarCorreo(solicitud.correo());
		limiteSolicitudes.registrarIntento(correo, ip);
		// Con la cuenta bloqueada, una solicitud simultánea espera y ve el token de esta: solo queda uno vigente.
		// A una cuenta desactivada no se le envía nada: cambiar la contraseña no le serviría para entrar (HU-03 RF-3)
		usuarioRepository.findConBloqueoByCorreo(correo).filter(Usuario::estaActiva)
				.ifPresent(this::iniciarRecuperacion);
	}

	@Transactional
	public void restablecer(RestablecimientoContrasenaRequest solicitud) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		RecuperacionContrasena recuperacion = recuperacionRepository
				.findByHashToken(TokenRecuperacion.hash(solicitud.token()))
				.filter(registro -> registro.estaVigente(ahora))
				.orElseThrow(TokenRecuperacionInvalidoException::new);
		Usuario usuario = recuperacion.getUsuario();
		usuario.cambiarContrasena(passwordEncoder.encode(solicitud.contrasena()));
		invalidarPendientes(usuario);
		// Quien restablece tiene el token que llegó al correo: el bloqueo por intentos fallidos ya no protege la cuenta
		limiteIntentos.reiniciarCorreo(usuario.getCorreo());
	}

	private void iniciarRecuperacion(Usuario usuario) {
		invalidarPendientes(usuario);
		String token = TokenRecuperacion.generar();
		recuperacionRepository.save(
				RecuperacionContrasena.crear(usuario, TokenRecuperacion.hash(token), LocalDateTime.now(clock)));
		notificadorCorreo.enviarRecuperacion(usuario.getCorreo(), usuario.getNombre(), token);
	}

	// Incluye la recuperación que se acaba de usar al restablecer
	private void invalidarPendientes(Usuario usuario) {
		recuperacionRepository.findByUsuarioAndUsadoFalse(usuario).forEach(RecuperacionContrasena::marcarUsada);
	}
}
