package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.repository.CobroRepository;
import bo.edu.uagrm.tienda.repository.CobroRepository.CobroAConciliar;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import bo.edu.uagrm.tienda.service.pago.IPago;
import bo.edu.uagrm.tienda.service.pago.ResultadoDelCobro;
import bo.edu.uagrm.tienda.service.pago.SistemaDePagoNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// La única pieza que aplica lo que dice el sistema de pago (HU-13; decisiones.md → Pagos). La usan la consulta de la
// página, el aviso, la tarea de cada minuto y el vencimiento. Consulta sin nada bloqueado y sin transacción abierta,
// así una llamada de 15 s no retiene una conexión; después bloquea en el orden de siempre (cliente, pedido, cobro y,
// si revive, productos por id) y vuelve a mirar el cobro ya bloqueado: si otro lo procesó, no hace nada (RF-11)
@Slf4j
@Component
public class ConciliacionDePagos {

	private final CobroRepository cobroRepository;
	private final PedidoRepository pedidoRepository;
	private final UsuarioRepository usuarioRepository;
	private final PedidoService pedidoService;
	private final IPago iPago;
	private final Clock clock;
	// READ_COMMITTED: lo que se lee sin bloqueo después de bloquear al cliente ve lo último confirmado
	private final TransactionTemplate transaccion;

	public ConciliacionDePagos(CobroRepository cobroRepository, PedidoRepository pedidoRepository,
			UsuarioRepository usuarioRepository, PedidoService pedidoService, IPago iPago, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.cobroRepository = cobroRepository;
		this.pedidoRepository = pedidoRepository;
		this.usuarioRepository = usuarioRepository;
		this.pedidoService = pedidoService;
		this.iPago = iPago;
		this.clock = clock;
		this.transaccion = new TransactionTemplate(transactionManager);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	// Devuelve si el sistema de pago respondió (o si ya estaba procesado): con false, el pedido no vence (RF-7)
	public boolean conciliar(Long idCobro) {
		Optional<CobroAConciliar> datos = cobroRepository.datosParaConciliar(idCobro);
		if (datos.isEmpty()) {
			return true;
		}
		ResultadoDelCobro resultado;
		try {
			// RF-4: nunca por lo que diga o muestre el cliente
			resultado = iPago.consultar(datos.get().referencia());
		} catch (SistemaDePagoNoDisponibleException error) {
			log.warn("No se pudo consultar el cobro {}; se reintenta en la próxima vuelta", idCobro, error);
			return false;
		}
		transaccion.executeWithoutResult(estado -> aplicar(datos.get(), resultado));
		return true;
	}

	private void aplicar(CobroAConciliar datos, ResultadoDelCobro resultado) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		usuarioRepository.findConBloqueoByIdUsuario(datos.idUsuario()).orElseThrow();
		Pedido pedido = pedidoRepository.findConBloqueoByIdPedidoAndUsuario_IdUsuario(datos.idPedido(),
				datos.idUsuario()).orElseThrow();
		Cobro cobro = cobroRepository.findConBloqueoByIdCobro(datos.idCobro()).orElseThrow();
		if (cobro.getEstado() != EstadoCobro.PENDIENTE) {
			return; // RF-11
		}
		switch (resultado.estado()) {
			// RF-7: dicho después de la hora, el pedido ya puede vencer
			case PENDIENTE -> cobro.consultadoSinPago(ahora);
			// RF-6: el pedido sigue igual y se puede generar otro
			case ERROR -> cobro.registrarError();
			case PAGADO -> {
				LocalDateTime fecha = resultado.fechaPago() == null ? ahora : resultado.fechaPago();
				cobro.registrarPago(fecha);
				if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO) {
					// RF-5: la reserva queda como venta, sin otro movimiento. También si ya pasó la hora pero no
					// venció: el pago llegó antes que el vencimiento (RF-7)
					pedido.pagar(fecha);
				} else {
					pedidoService.revivir(pedido, fecha, ahora); // RF-9
				}
				// Al confirmar: si algo falla antes, el cobro sigue pendiente y el log no puede decir que se pagó
				TrasElCommit.ejecutar(() -> log.info("Cobro {} pagado el {} (pedido {})", datos.idCobro(), fecha,
						datos.idPedido()));
			}
		}
	}
}
