package bo.edu.uagrm.tienda.service.pago;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// La implementación por defecto en desarrollo y pruebas (decisiones.md → Pago: interfaz propia). Guarda los cobros en
// memoria: tras un reinicio, una referencia desconocida sigue pendiente. No tiene página de pago
@Slf4j
@Component
@ConditionalOnProperty(name = "tienda.pago.proveedor", havingValue = "simulador", matchIfMissing = true)
public class SimuladorDePago implements IPago {

	private final Map<String, ResultadoDelCobro> cobros = new ConcurrentHashMap<>();
	private final Clock clock;

	public SimuladorDePago(Clock clock) {
		this.clock = clock;
	}

	@Override
	public CobroGenerado generarCobro(Long idOperacion, BigDecimal monto, String concepto) {
		String referencia = "SIM-" + UUID.randomUUID();
		cobros.put(referencia, ResultadoDelCobro.pendiente());
		log.info("[Pago simulado] Cobro {} por Bs {} de la operación {}: {}", referencia, monto.toPlainString(), idOperacion,
				concepto);
		return new CobroGenerado(referencia, "SIMULADOR|" + referencia + "|" + monto.toPlainString(), null);
	}

	@Override
	public ResultadoDelCobro consultar(String referencia) {
		return cobros.getOrDefault(referencia, ResultadoDelCobro.pendiente());
	}

	// Lo que en producción haría el cliente desde la app de su banco. Acepta también un cobro que olvidó al reiniciarse:
	// quien lo llame debe tomar la referencia de la base y solo para el dueño del cobro
	public void forzar(String referencia, EstadoDelCobro estado) {
		ResultadoDelCobro resultado = switch (estado) {
			case PAGADO -> new ResultadoDelCobro(EstadoDelCobro.PAGADO, LocalDateTime.now(clock));
			case ERROR -> new ResultadoDelCobro(EstadoDelCobro.ERROR, null);
			case PENDIENTE -> throw new IllegalArgumentException("Solo se fuerza un pago o un error");
		};
		cobros.put(referencia, resultado);
	}
}
