package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// HU-18 RF-23: lo que compró una cuenta en el período (ReportesRepository.compradores): compras, total, la primera y
// la última, y cuántas en cada canal
public record CompradorFila(Long idUsuario, Long compras, BigDecimal total, LocalDateTime primera,
		LocalDateTime ultima, Long enLinea, Long enTienda) {
}
