package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.exception.ErrorNegocioException;
import bo.edu.uagrm.tienda.exception.RegistrosExcedidosException;

// Cuentas creadas por IP (decisiones.md → Registro). LimiteDeFrecuencia cuenta por correo e IP: acá el correo es fijo
@Component
public class LimiteRegistros extends LimiteDeFrecuencia {

	private static final String CUALQUIER_CORREO = "";

	public LimiteRegistros(Clock clock) {
		super(clock, 5, Duration.ofHours(1), Duration.ofHours(1));
	}

	public void registrarIntento(String ip) {
		registrarIntento(CUALQUIER_CORREO, ip);
	}

	public void anularIntento(String ip) {
		anularIntento(CUALQUIER_CORREO, ip);
	}

	@Override
	protected ErrorNegocioException limiteExcedido() {
		return new RegistrosExcedidosException();
	}
}
