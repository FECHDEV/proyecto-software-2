package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.repository.CobroRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Aplica los pagos aunque el Cliente haya cerrado la página (HU-13 RF-8): cada minuto, los cobros pendientes, también
// los de pedidos vencidos o cancelados, que pueden ser un pago tardío (RF-9)
@Slf4j
@Component
@RequiredArgsConstructor
public class ConciliacionAutomaticaDePagos {

	// La deuda del sistema de pago vence a las 23:59 del día en que vence el pedido: hasta unas 24 h 15 min después de
	// generarla (un cobro de las 23:45 vence a las 23:59 del otro día). Con 24 horas se perdía ese último rato; 48
	// dejan margen (HU-13 RF-8)
	static final Duration VENTANA = Duration.ofHours(48);

	private final CobroRepository cobroRepository;
	private final ConciliacionDePagos conciliacionDePagos;
	private final Clock clock;

	// En las pruebas va apagada: cada prueba concilia cuando lo pide
	@Value("${tienda.pago.conciliacion-programada:true}")
	private boolean programada;

	@Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
	void cadaMinuto() {
		if (programada) {
			conciliarPendientes();
		}
	}

	// Cada cobro en su transacción (ConciliacionDePagos): uno que falla no frena al resto
	public void conciliarPendientes() {
		for (Long idCobro : cobroRepository.idsPorConciliar(LocalDateTime.now(clock).minus(VENTANA))) {
			try {
				conciliacionDePagos.conciliar(idCobro);
			} catch (RuntimeException error) {
				log.warn("No se pudo conciliar el cobro {}", idCobro, error);
			}
		}
	}
}
