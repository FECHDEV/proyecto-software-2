package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

// Lo vendido de un producto en el período (ReportesRepository): el margen suma solo las líneas con costo, y sinCosto
// es el importe de las que no lo tienen (HU-18 RF-8)
public record LineasFila(Long idProducto, BigDecimal cantidad, BigDecimal importe, BigDecimal margen,
		BigDecimal sinCosto) {
}
