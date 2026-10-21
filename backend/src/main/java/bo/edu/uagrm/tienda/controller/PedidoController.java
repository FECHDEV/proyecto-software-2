package bo.edu.uagrm.tienda.controller;

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

import bo.edu.uagrm.tienda.dto.CancelacionResponse;
import bo.edu.uagrm.tienda.dto.ConfirmacionRequest;
import bo.edu.uagrm.tienda.dto.MisPedidosResponse;
import bo.edu.uagrm.tienda.dto.PedidoResponse;
import bo.edu.uagrm.tienda.dto.RetiroResponse;
import bo.edu.uagrm.tienda.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Los pedidos del Cliente que inició sesión (HU-12). Solo el rol Cliente llega acá (SecurityConfig, RF-13), y el
// cliente sale del token. Un número de pedido que no es un número no coincide con ninguna ruta: 404, como uno ajeno
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

	private static final String ID = "/{idPedido:\\d{1,18}}";

	private final PedidoService pedidoService;

	// «Mis pedidos» (HU-14). Una página que no es un número entero es la primera, sin error (RF-8)
	@GetMapping
	public MisPedidosResponse misPedidos(@AuthenticationPrincipal Long idUsuario,
			@RequestParam(required = false) String pagina) {
		return pedidoService.misPedidos(idUsuario, numeroDePagina(pagina));
	}

	@GetMapping("/retiro")
	public RetiroResponse retiro() {
		return pedidoService.retiro();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PedidoResponse confirmar(@AuthenticationPrincipal Long idUsuario,
			@Valid @RequestBody ConfirmacionRequest solicitud) {
		return pedidoService.confirmar(idUsuario, solicitud);
	}

	@GetMapping(ID)
	public PedidoResponse ver(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idPedido) {
		return pedidoService.ver(idUsuario, idPedido);
	}

	@PostMapping(ID + "/cancelacion")
	public CancelacionResponse cancelar(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idPedido) {
		return pedidoService.cancelar(idUsuario, idPedido);
	}

	private static int numeroDePagina(String pagina) {
		try {
			return pagina == null ? 0 : Math.max(Integer.parseInt(pagina.strip()), 0);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
