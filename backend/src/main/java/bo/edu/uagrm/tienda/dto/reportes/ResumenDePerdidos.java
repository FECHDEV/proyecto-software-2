package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

// GET /api/reportes/perdidos/resumen (HU-18 RF-17). confirmados no cuenta los que siguen esperando el pago ni los
// pagos a devolver; proporcion es el % de esos que se vendió, nulo sin confirmados
public record ResumenDePerdidos(long cantidad, BigDecimal importe, long confirmados, long vendidos,
		BigDecimal proporcion) {
}
