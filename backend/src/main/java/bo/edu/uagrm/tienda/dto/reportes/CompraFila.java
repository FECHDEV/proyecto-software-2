package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Lo que compró un cliente, de todos los tiempos (HU-18 RF-16)
public record CompraFila(Long idUsuario, LocalDateTime ultima, Long pedidos, BigDecimal total) {
}
