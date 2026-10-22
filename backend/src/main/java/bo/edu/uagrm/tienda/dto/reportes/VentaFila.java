package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.TipoCliente;

// Un pedido vendido del período, con lo que hace falta para agruparlo por fecha, día, hora y tipo (ReportesRepository)
public record VentaFila(Long idPedido, LocalDateTime pagado, BigDecimal total, TipoCliente tipo) {
}
