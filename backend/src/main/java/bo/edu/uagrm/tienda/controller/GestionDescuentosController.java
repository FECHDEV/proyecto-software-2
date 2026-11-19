package bo.edu.uagrm.tienda.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.DescuentoCambioRequest;
import bo.edu.uagrm.tienda.dto.DescuentoGuardadoResponse;
import bo.edu.uagrm.tienda.dto.DescuentoRequest;
import bo.edu.uagrm.tienda.dto.DescuentoResponse;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.service.DescuentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Los descuentos (HU-24 RF-1 a RF-6). Solo el Administrador llega acá (SecurityConfig → /api/gestion/**, RF-7)
@RestController
@RequestMapping("/api/gestion/descuentos")
@RequiredArgsConstructor
public class GestionDescuentosController {

	private final DescuentoService descuentoService;

	@GetMapping
	public PaginaResponse<DescuentoResponse> listar(@RequestParam(defaultValue = "0") int pagina) {
		return descuentoService.listar(pagina);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DescuentoGuardadoResponse crear(@AuthenticationPrincipal Long idAdministrador,
			@Valid @RequestBody DescuentoRequest solicitud) {
		return descuentoService.crear(solicitud, idAdministrador);
	}

	@PutMapping("/{idDescuento}")
	public DescuentoGuardadoResponse cambiar(@PathVariable Long idDescuento,
			@Valid @RequestBody DescuentoCambioRequest solicitud) {
		return descuentoService.cambiar(idDescuento, solicitud);
	}

	@DeleteMapping("/{idDescuento}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrar(@PathVariable Long idDescuento) {
		descuentoService.borrar(idDescuento);
	}

	@PostMapping("/{idDescuento}/terminar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void terminar(@PathVariable Long idDescuento) {
		descuentoService.terminar(idDescuento);
	}
}
