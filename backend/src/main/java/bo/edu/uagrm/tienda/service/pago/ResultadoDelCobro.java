package bo.edu.uagrm.tienda.service.pago;

import java.time.LocalDateTime;

// El resultado de consultar un cobro. La fecha del pago solo viene con PAGADO
public record ResultadoDelCobro(EstadoDelCobro estado, LocalDateTime fechaPago) {

	public static ResultadoDelCobro pendiente() {
		return new ResultadoDelCobro(EstadoDelCobro.PENDIENTE, null);
	}
}
