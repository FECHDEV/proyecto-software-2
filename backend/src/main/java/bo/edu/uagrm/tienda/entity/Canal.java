package bo.edu.uagrm.tienda.entity;

import java.util.Arrays;

// Dónde se hizo la venta: el Cliente en la tienda en línea, o el personal en el mostrador del local (HU-23;
// decisiones.md → Venta en tienda)
public enum Canal {
	EN_LINEA, TIENDA;

	// El filtro de /pedidos y de los reportes (HU-23 RF-16, RF-22): vacío o uno que no existe, null (los dos canales)
	public static Canal deTexto(String texto) {
		return Arrays.stream(values()).filter(valor -> valor.name().equals(texto)).findFirst().orElse(null);
	}
}
