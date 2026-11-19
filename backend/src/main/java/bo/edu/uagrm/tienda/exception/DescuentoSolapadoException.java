package bo.edu.uagrm.tienda.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import bo.edu.uagrm.tienda.entity.Descuento;

// HU-24 RF-2: el descuento se cruza en al menos un día con otro del mismo producto o categoría; lleva con cuál
public class DescuentoSolapadoException extends ErrorNegocioException {

	private final Descuento otro;

	public DescuentoSolapadoException(Descuento otro) {
		super("DESCUENTO_SOLAPADO", HttpStatus.CONFLICT, "Se cruza con otro descuento en las mismas fechas.");
		this.otro = otro;
	}

	@Override
	public Map<String, Object> propiedades() {
		return Map.of("inicio", otro.getInicio(), "fin", otro.getFin(), "porcentaje", otro.getPorcentaje());
	}
}
