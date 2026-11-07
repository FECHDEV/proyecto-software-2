package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.entity.Canal;
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository.PedidoAVencer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Vence los pedidos sin pagar a los 30 minutos, y las ventas del mostrador con QR a los 10, aunque nadie los mire
// (HU-12 RF-10; HU-23 RF-10). Es otro bean que PedidoService:
// así cada pedido vence en su propia transacción y uno que falla no frena a los demás. Uno con cobro se consulta antes,
// sin nada bloqueado: si está pagado queda pagado, y si la consulta falla no vence en esta vuelta (HU-13 RF-7). Se
// consulta siempre, aunque la conciliación ya lo haya hecho: un pago entre las dos consultas no se pierde
@Slf4j
@Component
@RequiredArgsConstructor
public class VencimientoPedidos {

	private final PedidoRepository pedidoRepository;
	private final PedidoService pedidoService;
	private final VentaTiendaService ventaTiendaService;
	private final ConciliacionDePagos conciliacionDePagos;
	private final Clock clock;

	// En las pruebas va apagada: cada prueba vence cuando lo pide, con su reloj
	@Value("${tienda.pedidos.vencimiento-programado:true}")
	private boolean programado;

	@Scheduled(fixedDelayString = "PT1M")
	void cadaMinuto() {
		if (programado) {
			vencer();
		}
	}

	public void vencer() {
		for (PedidoAVencer pedido : pedidoRepository.findByEstadoAndVenceLessThanEqual(EstadoPedido.PENDIENTE_PAGO,
				LocalDateTime.now(clock))) {
			try {
				boolean respondio = pedidoService.cobroPendiente(pedido.idPedido()).map(Cobro::getIdCobro)
						.map(conciliacionDePagos::conciliar).orElse(true);
				if (respondio && pedido.canal() == Canal.TIENDA) {
					ventaTiendaService.vencer(pedido.idPedido()); // HU-23 RF-10: a los 10 minutos, sin carrito
				} else if (respondio) {
					pedidoService.vencer(pedido.idUsuario(), pedido.idPedido());
				}
			} catch (RuntimeException e) {
				log.warn("No se pudo vencer el pedido {}", pedido.idPedido(), e);
			}
		}
	}
}
