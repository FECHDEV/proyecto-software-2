package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.exception.ErrorNegocioException;
import bo.edu.uagrm.tienda.exception.ReenviosExcedidosException;

// Como la recuperación: cada reenvío dispara un correo real (decisiones.md → Verificación del correo). Cuenta por
// cuenta, no por IP: para reenviar hay sesión, así que se registra con el correo de la cuenta y una IP fija
@Component
public class LimiteReenviosVerificacion extends LimiteDeFrecuencia {

	public static final String SIN_IP = "";

	public LimiteReenviosVerificacion(Clock clock) {
		super(clock, 3, Duration.ofMinutes(15), Duration.ofMinutes(15));
	}

	@Override
	protected ErrorNegocioException limiteExcedido() {
		return new ReenviosExcedidosException();
	}
}
