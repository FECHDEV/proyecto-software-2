package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.config.TokenJwt;
import bo.edu.uagrm.tienda.dto.DatosPersonalesRequest;
import bo.edu.uagrm.tienda.dto.RegistroClienteRequest;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CuentaExistenteException;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final TokenJwt tokenJwt;
	private final LimiteRegistros limiteRegistros;

	// El registro cuenta para el límite antes de crear la cuenta (una ráfaga no pasa del máximo) y se anula si no se
	// completa: solo cuentan los registros exitosos (decisiones.md → Registro)
	@Transactional
	public SesionIniciada registrarCliente(RegistroClienteRequest solicitud, String ip) {
		limiteRegistros.registrarIntento(ip);
		try {
			Usuario usuario = crearCliente(solicitud);
			return new SesionIniciada(usuario, tokenJwt.emitir(usuario));
		} catch (RuntimeException e) {
			limiteRegistros.anularIntento(ip);
			throw e;
		}
	}

	private Usuario crearCliente(RegistroClienteRequest solicitud) {
		String correo = Usuario.normalizarCorreo(solicitud.correo());
		if (usuarioRepository.existsByCorreo(correo)) {
			throw new CuentaExistenteException();
		}
		Usuario usuario = Usuario.registrarCliente(solicitud.nombre(), solicitud.apellido(), correo,
				passwordEncoder.encode(solicitud.contrasena()), solicitud.telefono(),
				LocalDateTime.now(clock));
		try {
			return usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			if (UsuarioRepository.esCorreoDuplicado(e)) {
				throw new CuentaExistenteException();
			}
			throw e;
		}
	}

	@Transactional(readOnly = true)
	public Usuario datosPersonales(Long idUsuario) {
		return usuarioRepository.findById(idUsuario).orElseThrow();
	}

	@Transactional
	public Usuario actualizarDatos(Long idUsuario, DatosPersonalesRequest solicitud) {
		Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();
		usuario.actualizarDatos(solicitud.nombre(), solicitud.apellido(), solicitud.telefono());
		return usuario;
	}
}
