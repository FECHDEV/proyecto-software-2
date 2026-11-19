package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

// HU-24 RF-19: lo vendido con descuento en el período (ReportesRepository.descuentos): los pedidos con al menos una
// línea con descuento, el subtotal cobrado de esas líneas y lo que se descontó en ellas
public record DescuentosFila(long pedidos, BigDecimal vendido, BigDecimal descontado) {
}
