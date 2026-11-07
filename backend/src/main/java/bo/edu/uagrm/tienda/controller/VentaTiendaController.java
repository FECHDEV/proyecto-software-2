package bo.edu.uagrm.tienda.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.AnulacionRequest;
import bo.edu.uagrm.tienda.dto.CompradorResponse;
import bo.edu.uagrm.tienda.dto.ProductoVentaResponse;
import bo.edu.uagrm.tienda.dto.VentaRequest;
import bo.edu.uagrm.tienda.dto.VentaResponse;
import bo.edu.uagrm.tienda.service.VentaTiendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// La venta del mostrador (HU-23). Quién puede cada cosa lo decide SecurityConfig: leer, el personal; registrar,
// consultar y cancelar, el Empleado y el Administrador; anular, solo el Administrador (RF-23). El id solo con dígitos:
// lo demás no coincide con ninguna ruta y responde 404
@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaTiendaController {

	static final String ID = "/{idPedido:\\d{1,18}}";

	private final VentaTiendaService ventaTiendaService;

	@GetMapping("/productos")
	public List<ProductoVentaResponse> productos(@RequestParam(required = false) String texto) {
		return ventaTiendaService.buscarProductos(texto);
	}

	@GetMapping("/clientes")
	public CompradorResponse cliente(@RequestParam(required = false) String correo) {
		return ventaTiendaService.buscarCliente(correo);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public VentaResponse registrar(@AuthenticationPrincipal Long idVendedor, @Valid @RequestBody VentaRequest venta) {
		return ventaTiendaService.crear(venta, idVendedor);
	}

	@GetMapping(ID)
	public VentaResponse ver(@PathVariable Long idPedido) {
		return ventaTiendaService.ver(idPedido);
	}

	@PostMapping(ID + "/consulta")
	public VentaResponse consultar(@PathVariable Long idPedido) {
		return ventaTiendaService.consultar(idPedido);
	}

	@PostMapping(ID + "/cancelacion")
	public VentaResponse cancelar(@PathVariable Long idPedido) {
		return ventaTiendaService.cancelar(idPedido);
	}

	@PostMapping(ID + "/anulacion")
	public VentaResponse anular(@AuthenticationPrincipal Long idAdministrador, @PathVariable Long idPedido,
			@Valid @RequestBody AnulacionRequest anulacion) {
		return ventaTiendaService.anular(idPedido, idAdministrador, anulacion.motivo());
	}
}
