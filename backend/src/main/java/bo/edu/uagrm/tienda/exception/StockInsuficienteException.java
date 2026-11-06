package bo.edu.uagrm.tienda.exception;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

// Las líneas que impiden confirmar, todas y en el orden del carrito (HU-12 RF-3). Al Cliente le dice por qué, nunca
// cuánto stock hay; en la venta del mostrador, el personal ya ve el stock y la línea trae el disponible (HU-23 RF-12)
public class StockInsuficienteException extends ErrorNegocioException {

	private final List<LineaRechazada> lineas;

	public StockInsuficienteException(List<LineaRechazada> lineas) {
		this(lineas, "Algunos productos del carrito no se pueden confirmar.");
	}

	private StockInsuficienteException(List<LineaRechazada> lineas, String mensaje) {
		super("STOCK_INSUFICIENTE", HttpStatus.CONFLICT, mensaje);
		this.lineas = List.copyOf(lineas);
	}

	public static StockInsuficienteException enLaVenta(List<LineaRechazada> lineas) {
		return new StockInsuficienteException(lineas, "Algunos productos de la venta no se pueden registrar.");
	}

	@Override
	public Map<String, Object> propiedades() {
		return Map.of("lineas", lineas);
	}

	// disponible: solo en la venta del mostrador; en línea es nulo y no sale en la respuesta
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LineaRechazada(Long idProducto, Motivo motivo, BigDecimal disponible) {

		public LineaRechazada(Long idProducto, Motivo motivo) {
			this(idProducto, motivo, null);
		}
	}

	// Los tres primeros, como en el carrito (HU-11 RF-9); NO_ALCANZA: pide más de lo que hay
	public enum Motivo {
		AGOTADO, NO_DISPONIBLE, CANTIDAD_INVALIDA, NO_ALCANZA
	}
}
