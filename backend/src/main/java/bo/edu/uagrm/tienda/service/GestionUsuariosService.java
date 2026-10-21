package bo.edu.uagrm.tienda.service;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.RecuperacionContrasena;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.CuentaPropiaNoDesactivableException;
import bo.edu.uagrm.tienda.exception.RolDeCuentaDesactivadaException;
import bo.edu.uagrm.tienda.exception.RolPropioNoModificableException;
import bo.edu.uagrm.tienda.exception.UltimoAdministradorActivoException;
import bo.edu.uagrm.tienda.exception.UsuarioNoEncontradoException;
import bo.edu.uagrm.tienda.repository.RecuperacionContrasenaRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// Solo el Administrador llega acá (SecurityConfig). Reglas en decisiones.md → Gestión de usuarios
@Service
@RequiredArgsConstructor
public class GestionUsuariosService {

	private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaRegistro", "idUsuario");

	private final UsuarioRepository usuarioRepository;
	private final RecuperacionContrasenaRepository recuperacionRepository;
	private final VerificacionCorreoService verificacionCorreo;

	@Transactional(readOnly = true)
	public Page<Usuario> listar(Rol rol, EstadoCuenta estado, String texto, int pagina, int tamano) {
		PageRequest paginacion = Busquedas.pagina(pagina, tamano, ORDEN);
		return usuarioRepository.buscar(rol, estado, Busquedas.patron(texto), paginacion);
	}

	@Transactional
	public Usuario cambiarRol(Long idAdministrador, Long idUsuario, Rol rolNuevo) {
		if (idAdministrador.equals(idUsuario)) {
			throw new RolPropioNoModificableException();
		}
		long administradoresActivos = contarAdministradoresActivosConBloqueo();
		Usuario usuario = bloquearCuenta(idUsuario);
		if (esAdministradorActivo(usuario) && rolNuevo != Rol.ADMINISTRADOR && administradoresActivos <= 1) {
			throw new UltimoAdministradorActivoException();
		}
		if (!usuario.estaActiva()) {
			throw new RolDeCuentaDesactivadaException();
		}
		usuario.asignarRol(rolNuevo);
		return usuario;
	}

	@Transactional
	public Usuario cambiarEstado(Long idAdministrador, Long idUsuario, EstadoCuenta estadoNuevo) {
		if (estadoNuevo == EstadoCuenta.DESACTIVADA && idAdministrador.equals(idUsuario)) {
			throw new CuentaPropiaNoDesactivableException();
		}
		if (estadoNuevo == EstadoCuenta.ACTIVA) {
			// Reactivar nunca reduce la cantidad de administradores activos: no hay nada que contar, y así esta
			// operación no toma el bloqueo que haría esperar a las demás de la gestión de usuarios
			Usuario reactivada = bloquearCuenta(idUsuario);
			reactivada.activar();
			return reactivada;
		}
		long administradoresActivos = contarAdministradoresActivosConBloqueo();
		Usuario usuario = bloquearCuenta(idUsuario);
		if (esAdministradorActivo(usuario) && administradoresActivos <= 1) {
			throw new UltimoAdministradorActivoException();
		}
		usuario.desactivar();
		// Un enlace de recuperación o de verificación pendiente no puede revivir si después se reactiva la cuenta
		// (HU-03 RF-9, HU-20 RF-5)
		recuperacionRepository.findByUsuarioAndUsadoFalse(usuario).forEach(RecuperacionContrasena::marcarUsada);
		verificacionCorreo.invalidarPendientes(usuario);
		return usuario;
	}

	// Primero los administradores y después la cuenta, siempre en ese orden: dos operaciones simultáneas se esperan
	// en vez de bloquearse mutuamente. Se vuelve a mirar cada fila porque, tras esperar el bloqueo, puede llegar una
	// que otra operación acaba de cambiar
	private long contarAdministradoresActivosConBloqueo() {
		return usuarioRepository.findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA)
				.stream().filter(GestionUsuariosService::esAdministradorActivo).count();
	}

	private Usuario bloquearCuenta(Long idUsuario) {
		return usuarioRepository.findConBloqueoByIdUsuario(idUsuario).orElseThrow(UsuarioNoEncontradoException::new);
	}

	private static boolean esAdministradorActivo(Usuario usuario) {
		return usuario.getRol() == Rol.ADMINISTRADOR && usuario.estaActiva();
	}
}
