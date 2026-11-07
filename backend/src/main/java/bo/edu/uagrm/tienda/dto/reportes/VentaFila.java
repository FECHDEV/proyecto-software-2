package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.Canal;
import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.TipoCliente;

// Un pedido vendido del período, con lo que hace falta para agruparlo por fecha, día, hora, tipo, canal y medio de pago
// (ReportesRepository). medio es nulo en línea, que siempre se paga con QR (HU-23 RF-22)
public record VentaFila(Long idPedido, LocalDateTime pagado, BigDecimal total, TipoCliente tipo, Canal canal,
		MedioPago medio) {

	public MedioPago medioDePago() {
		return medio == null ? MedioPago.QR : medio;
	}
}
