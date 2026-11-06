package bo.edu.uagrm.tienda.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.SimulacionRequest;
import bo.edu.uagrm.tienda.service.VentaTiendaService;
import bo.edu.uagrm.tienda.service.pago.EstadoDelCobro;
import bo.edu.uagrm.tienda.service.pago.SimuladorDePago;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// HU-23: herramienta de pruebas, como SimulacionDePagoController. Solo existe con el simulador; con otro proveedor esta
// ruta no está. Lo que en producción haría el cliente desde la app de su banco frente al mostrador
@RestController
@RequestMapping("/api/ventas" + VentaTiendaController.ID + "/simulacion")
@ConditionalOnProperty(name = "tienda.pago.proveedor", havingValue = "simulador", matchIfMissing = true)
@RequiredArgsConstructor
public class VentaSimulacionController {

	private final VentaTiendaService ventaTiendaService;
	private final SimuladorDePago simuladorDePago;

	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void simular(@PathVariable Long idPedido, @Valid @RequestBody SimulacionRequest solicitud) {
		EstadoDelCobro resultado = solicitud.resultado() == SimulacionRequest.Resultado.PAGADO ? EstadoDelCobro.PAGADO
				: EstadoDelCobro.ERROR;
		simuladorDePago.forzar(ventaTiendaService.referenciaParaSimular(idPedido), resultado);
	}
}
