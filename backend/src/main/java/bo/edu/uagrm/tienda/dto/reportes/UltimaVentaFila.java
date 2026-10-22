package bo.edu.uagrm.tienda.dto.reportes;

import java.time.LocalDateTime;

// La última venta de un producto, de todos los tiempos (HU-18 RF-13)
public record UltimaVentaFila(Long idProducto, LocalDateTime ultima) {
}
