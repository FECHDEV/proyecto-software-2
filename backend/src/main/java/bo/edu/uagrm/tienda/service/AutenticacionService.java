package bo.edu.uagrm.tienda.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.config.TokenJwt;
import bo.edu.uagrm.tienda.dto.CambioContrasenaRequest;
import bo.edu.uagrm.tienda.dto.InicioSesionRequest;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.ContrasenaActualIncorrectaException;
import bo.edu.uagrm.tienda.exception.CredencialesIncorrectasException;
import bo.edu.uagrm.tienda.exception.CuentaDesactivadaException;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

@Service
public class AutenticacionService {

	// BCrypt ignora lo que pasa de 72 bytes: una contraseña más larga podría coincidir sin ser la registrada
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenJwt tokenJwt;
	private final LimiteIntentos limiteIntentos;
	// Se compara cuando el correo no existe, para que la demora no revele qué correos tienen cuenta
	private final String hashParaCorreoInexistente;

	public AutenticacionService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			TokenJwt tokenJwt, LimiteIntentos limiteIntentos) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenJwt = tokenJwt;
		this.limiteIntentos = limiteIntentos;
		this.hashParaCorreoInexistente = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(readOnly = true)
	public SesionIniciada iniciarSesion(InicioSesionRequest solicitud, String ip) {
		String correo = Usuario.normalizarCorreo(solicitud.correo());
		// Se cuenta antes de la comparación, que tarda: si no, una ráfaga simultánea superaría el límite
		limiteIntentos.registrarIntento(correo, ip);
		Optional<Usuario> cuenta = usuarioRepository.findByCorreo(correo);
		boolean coincide = passwordEncoder.matches(solicitud.contrasena(),
				cuenta.map(Usuario::getContrasena).orElse(hashParaCorreoInexistente));
		boolean largoAdmitido = tieneLargoAdmitido(solicitud.contrasena());
		if (cuenta.isEmpty() || !coincide || !largoAdmitido) {
			throw new CredencialesIncorrectasException();
		}
		Usuario usuario = cuenta.get();
		if (!usuario.estaActiva()) {
			limiteIntentos.anularIntento(correo, ip);
			throw new CuentaDesactivadaException();
		}
		limiteIntentos.registrarExito(correo, ip);
		return new SesionIniciada(usuario, tokenJwt.emitir(usuario));
	}

	@Transactional
	public SesionIniciada cambiarContrasena(Long idUsuario, CambioContrasenaRequest solicitud, String ip) {
		Usuario usuario = usuarioRepository.findConBloqueoByIdUsuario(idUsuario).orElseThrow();
		// Mismo límite que el inicio de sesión: adivinar la contraseña actual por acá no da más intentos
		limiteIntentos.registrarIntento(usuario.getCorreo(), ip);
		if (!passwordEncoder.matches(solicitud.contrasenaActual(), usuario.getContrasena())
				|| !tieneLargoAdmitido(solicitud.contrasenaActual())) {
			throw new ContrasenaActualIncorrectaException();
		}
		usuario.cambiarContrasena(passwordEncoder.encode(solicitud.contrasenaNueva()));
		limiteIntentos.registrarExito(usuario.getCorreo(), ip);
		// El token se emite con el hash nuevo: los anteriores dejan de corresponder a la cuenta
		return new SesionIniciada(usuario, tokenJwt.emitir(usuario));
	}

	private static boolean tieneLargoAdmitido(String contrasena) {
		return contrasena.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_CONTRASENA;
	}

	@Transactional(readOnly = true)
	public Optional<Usuario> usuarioActivo(Long idUsuario) {
		return usuarioRepository.findById(idUsuario).filter(Usuario::estaActiva);
	}
}
