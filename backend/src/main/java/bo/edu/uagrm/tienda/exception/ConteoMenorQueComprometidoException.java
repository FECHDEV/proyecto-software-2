package bo.edu.uagrm.tienda.exception;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.HttpStatus;

// Contaron en el estante menos de lo que ya está en pedidos pendientes o pagados sin retirar (HU-08 RF-18): faltan
// productos ya vendidos o reservados, y eso no se arregla con un ajuste. Lleva lo comprometido
public class ConteoMenorQueComprometidoException extends ErrorNegocioException {

	private final BigDecimal comprometido;

	public ConteoMenorQueComprometidoException(BigDecimal comprometido) {
		super("CONTEO_MENOR_QUE_COMPROMETIDO", HttpStatus.CONFLICT,
				"Contaron menos de lo que ya está vendido o reservado. Revise el conteo.");
		this.comprometido = comprometido;
	}

	@Override
	public Map<String, Object> propiedades() {
		return Map.of("comprometido", comprometido);
	}
}
