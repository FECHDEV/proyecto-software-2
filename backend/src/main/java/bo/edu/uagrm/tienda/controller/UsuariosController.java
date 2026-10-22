package bo.edu.uagrm.tienda.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.CambioEstadoRequest;
import bo.edu.uagrm.tienda.dto.CambioRolRequest;
import bo.edu.uagrm.tienda.dto.CambioTipoRequest;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.UsuarioResumenResponse;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.service.GestionUsuariosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo el Administrador llega acá (SecurityConfig). Quien opera sale del token; el usuario afectado, de la ruta
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuariosController {

	private final GestionUsuariosService gestionUsuariosService;

	@GetMapping
	public PaginaResponse<UsuarioResumenResponse> listar(@RequestParam(required = false) Rol rol,
			@RequestParam(required = false) EstadoCuenta estado, @RequestParam(required = false) TipoCliente tipo,
			@RequestParam(required = false) String texto, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamano) {
		return PaginaResponse.from(gestionUsuariosService.listar(rol, estado, tipo, texto, pagina, tamano),
				UsuarioResumenResponse::from);
	}

	// HU-18 RF-21
	@PutMapping("/{idUsuario}/tipo")
	public UsuarioResumenResponse cambiarTipo(@PathVariable Long idUsuario,
			@Valid @RequestBody CambioTipoRequest solicitud) {
		return UsuarioResumenResponse.from(gestionUsuariosService.cambiarTipo(idUsuario, solicitud.tipo()));
	}

	@PutMapping("/{idUsuario}/rol")
	public UsuarioResumenResponse cambiarRol(@AuthenticationPrincipal Long idAdministrador,
			@PathVariable Long idUsuario, @Valid @RequestBody CambioRolRequest solicitud) {
		return UsuarioResumenResponse.from(gestionUsuariosService.cambiarRol(idAdministrador, idUsuario, solicitud.rol()));
	}

	@PutMapping("/{idUsuario}/estado")
	public UsuarioResumenResponse cambiarEstado(@AuthenticationPrincipal Long idAdministrador,
			@PathVariable Long idUsuario, @Valid @RequestBody CambioEstadoRequest solicitud) {
		return UsuarioResumenResponse
				.from(gestionUsuariosService.cambiarEstado(idAdministrador, idUsuario, solicitud.estado()));
	}
}
