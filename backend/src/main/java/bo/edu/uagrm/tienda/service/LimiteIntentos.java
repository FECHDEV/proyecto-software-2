package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.exception.ErrorNegocioException;
import bo.edu.uagrm.tienda.exception.IntentosExcedidosException;

// Intentos fallidos de inicio de sesión (decisiones.md → Inicio de sesión)
@Component
public class LimiteIntentos extends LimiteDeFrecuencia {

	public LimiteIntentos(Clock clock) {
		super(clock, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
	}

	@Override
	protected ErrorNegocioException limiteExcedido() {
		return new IntentosExcedidosException();
	}
}
