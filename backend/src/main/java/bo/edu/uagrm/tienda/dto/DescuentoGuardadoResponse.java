package bo.edu.uagrm.tienda.dto;

import java.util.List;

// Lo guardado y, si los hay, los productos activos que con ese descuento quedan por debajo de su costo (HU-24 RF-6):
// se guarda igual, y la pantalla avisa cuáles son (hasta 10) y cuántos en total
public record DescuentoGuardadoResponse(DescuentoResponse descuento, List<String> bajoElCosto, int totalBajoElCosto) {
}
