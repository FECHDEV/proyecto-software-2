package bo.edu.uagrm.tienda.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.PedidoDespachoResponse;
import bo.edu.uagrm.tienda.dto.PedidoDespachoResumenResponse;
import bo.edu.uagrm.tienda.service.DespachoService;
import lombok.RequiredArgsConstructor;

// Los pedidos para el personal (HU-17). Leen el Empleado, el Administrador y el Contador; entregan los dos primeros
// (SecurityConfig, RF-9). Un número de pedido que no es un número no coincide con ninguna ruta: 404 (RF-12)
@RestController
@RequestMapping("/api/despacho/pedidos")
@RequiredArgsConstructor
public class DespachoController {

	private static final String ID = "/{idPedido:\\d{1,18}}";

	private final DespachoService despachoService;

	// Los parámetros llegan como texto: un estado o una página que no sirven muestran lo más cercano, sin 400 (RF-12)
	@GetMapping
	public PaginaResponse<PedidoDespachoResumenResponse> lista(@RequestParam(required = false) String estado,
			@RequestParam(required = false) String texto, @RequestParam(required = false) String pagina) {
		return despachoService.lista(estado, texto, Paginas.numeroDePagina(pagina));
	}

	@GetMapping(ID)
	public PedidoDespachoResponse ficha(@PathVariable Long idPedido) {
		return despachoService.ficha(idPedido);
	}

	@PostMapping(ID + "/entrega")
	public PedidoDespachoResponse entregar(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idPedido) {
		return despachoService.entregar(idPedido, idUsuario);
	}
}
