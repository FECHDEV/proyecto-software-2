package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.entity.VerificacionCorreo;
import bo.edu.uagrm.tienda.exception.CorreoYaVerificadoException;
import bo.edu.uagrm.tienda.exception.CuentaDesactivadaException;
import bo.edu.uagrm.tienda.exception.TokenVerificacionInvalidoException;
import bo.edu.uagrm.tienda.exception.UsuarioNoEncontradoException;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.repository.VerificacionCorreoRepository;
import lombok.RequiredArgsConstructor;

// El enlace «Confirme su correo» (HU-20; decisiones.md → Verificación del correo). Todo lo que escribe bloquea primero
// la cuenta y después sus enlaces: en el orden contrario, un reenvío y una verificación a la vez se trababan
@Service
@RequiredArgsConstructor
public class VerificacionCorreoService {

	private final UsuarioRepository usuarioRepository;
	private final VerificacionCorreoRepository verificacionRepository;
	private final NotificadorCorreo notificadorCorreo;
	private final LimiteReenviosVerificacion limiteReenvios;
	private final Clock clock;

	// La cuenta llega bloqueada o recién creada. A una desactivada o ya verificada no se le envía nada (RF-11, RF-12)
	@Transactional
	public void iniciar(Usuario usuario) {
		if (!usuario.estaActiva() || usuario.isCorreoVerificado()) {
			return;
		}
		invalidarPendientes(usuario);
		String token = TokenRecuperacion.generar();
		verificacionRepository.save(
				VerificacionCorreo.crear(usuario, TokenRecuperacion.hash(token), LocalDateTime.now(clock)));
		notificadorCorreo.enviarVerificacion(usuario.getCorreo(), usuario.getNombre(), token);
	}

	// Con la cuenta bloqueada, dos reenvíos a la vez se esperan y queda un solo enlace vigente (RF-9). El ya verificado
	// no cuenta para el límite (RF-10, RF-11), ni la cuenta desactivada mientras la sesión seguía abierta (RF-12): sin
	// el error, respondería que el correo salió
	@Transactional
	public Usuario reenviar(Long idUsuario) {
		Usuario usuario = usuarioRepository.findConBloqueoByIdUsuario(idUsuario)
				.orElseThrow(UsuarioNoEncontradoException::new);
		if (!usuario.estaActiva()) {
			throw new CuentaDesactivadaException();
		}
		if (usuario.isCorreoVerificado()) {
			throw new CorreoYaVerificadoException();
		}
		limiteReenvios.registrarIntento(usuario.getCorreo(), LimiteReenviosVerificacion.SIN_IP);
		iniciar(usuario);
		return usuario;
	}

	// El id de la cuenta se lee sin bloqueo y sin cargar el enlace, que se trae recién con la cuenta bloqueada: una
	// entidad ya leída no se vuelve a leer al bloquearla. Dos verificaciones del mismo enlace se esperan en la cuenta
	// y la segunda lo encuentra usado
	@Transactional
	public void verificar(String token) {
		String hash = TokenRecuperacion.hash(token);
		Long idUsuario = verificacionRepository.idUsuarioDelToken(hash)
				.orElseThrow(TokenVerificacionInvalidoException::new);
		Usuario usuario = usuarioRepository.findConBloqueoByIdUsuario(idUsuario)
				.filter(Usuario::estaActiva) // RF-5: el enlace de una cuenta desactivada no sirve
				.orElseThrow(TokenVerificacionInvalidoException::new);
		LocalDateTime ahora = LocalDateTime.now(clock);
		verificacionRepository.findByHashToken(hash)
				.filter(registro -> registro.estaVigente(ahora))
				.orElseThrow(TokenVerificacionInvalidoException::new);
		usuario.marcarCorreoVerificado();
		invalidarPendientes(usuario);
	}

	// Incluye el enlace que se acaba de usar al verificar (RF-3). Desactivar la cuenta también los invalida: como los de
	// la recuperación, no reviven al reactivarla (RF-5)
	@Transactional
	public void invalidarPendientes(Usuario usuario) {
		verificacionRepository.findByUsuarioAndUsadoFalse(usuario).forEach(VerificacionCorreo::marcarUsada);
	}
}
