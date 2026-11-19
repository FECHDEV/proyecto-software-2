package bo.edu.uagrm.tienda.service.pago;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import bo.edu.uagrm.tienda.config.LibelulaProperties;
import bo.edu.uagrm.tienda.service.PedidoService;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// IPago contra Libélula (decisiones.md → Pagos; HU-13 RF-13). Registra la deuda y la consulta por nuestro
// identificador; el aviso por callback_url llega a /api/pagos/aviso y termina en la misma consulta. Cualquier falla del
// proveedor sale como SistemaDePagoNoDisponibleException, sin la appkey en el mensaje
@Slf4j
public class AdaptadorLibelula implements IPago {

	static final String REGISTRAR = "/rest/deuda/registrar";
	static final String CONSULTAR = "/rest/deuda/consultar_deudas/por_identificador";
	private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final RestClient http;
	private final LibelulaProperties cuenta;
	private final Clock clock;
	private final JsonMapper json = JsonMapper.builder().build();
	private final SecureRandom azar = new SecureRandom();

	public AdaptadorLibelula(RestClient.Builder builder, LibelulaProperties cuenta, Clock clock) {
		this.http = builder.baseUrl(cuenta.url()).build();
		this.cuenta = cuenta;
		this.clock = clock;
	}

	@Override
	public CobroGenerado generarCobro(Long idPedido, BigDecimal monto, String concepto, String rutaDeRetorno) {
		String identificador = nuevoIdentificador(idPedido);
		Map<String, Object> deuda = new LinkedHashMap<>();
		deuda.put("appkey", cuenta.appkey());
		deuda.put("email_cliente", cuenta.email());
		deuda.put("identificador", identificador);
		deuda.put("descripcion", concepto);
		deuda.put("moneda", "BOB");
		deuda.put("fecha_vencimiento", vencimiento() + " 23:59");
		deuda.put("callback_url", cuenta.urlDelSitio() + "/api/pagos/aviso?referencia="
				+ URLEncoder.encode(identificador, StandardCharsets.UTF_8));
		deuda.put("url_retorno", cuenta.urlDelSitio() + (rutaDeRetorno == null ? "/" : rutaDeRetorno));
		// Una sola línea por el total: el detalle del pedido está en la tienda
		deuda.put("lineas_detalle_deuda", List.of(Map.of("concepto", concepto, "cantidad", 1, "costo_unitario", monto)));

		JsonNode respuesta = enviar(REGISTRAR, deuda);
		String codigoQr = textoDelQr(respuesta);
		log.info("Deuda {} registrada en el sistema de pago por Bs {} (pedido {})", identificador, monto.toPlainString(),
				idPedido);
		return new CobroGenerado(identificador, codigoQr, textoONulo(respuesta.path("url_pasarela_pagos")));
	}

	@Override
	public ResultadoDelCobro consultar(String referencia) {
		JsonNode respuesta = enviar(CONSULTAR, Map.of("appkey", cuenta.appkey(), "identificador", referencia));
		JsonNode deuda = deudaConIdentificador(respuesta.path("datos"), referencia);
		if (deuda == null || !verdadero(deuda.path("pagado"))) {
			return ResultadoDelCobro.pendiente();
		}
		return new ResultadoDelCobro(EstadoDelCobro.PAGADO, fechaYHora(deuda.path("fecha_pago")));
	}

	// Único aunque el mismo pedido vuelva a pedir el cobro después de una falla, y corto para cobro.referencia
	private String nuevoIdentificador(Long idPedido) {
		byte[] sufijo = new byte[4];
		azar.nextBytes(sufijo);
		return "TM-P" + idPedido + "-" + HexFormat.of().formatHex(sufijo);
	}

	// Libélula vence la deuda por día (decisiones.md → Pagos; los 30 minutos los controla el sistema): el día en que
	// vencería un pedido confirmado ahora, para que uno de las 23:55 se pueda pagar pasada la medianoche. Va con «23:59», como
	// en el manual: solo con la fecha, Libélula la toma como las 00:00 y rechaza la de hoy por pasada
	private LocalDate vencimiento() {
		return LocalDateTime.now(clock).plus(PedidoService.VIGENCIA).toLocalDate();
	}

	// La respuesta se lee en bytes y como JSON (UTF-8): Libélula puede mandarla con otro Content-Type y sin charset
	private JsonNode enviar(String ruta, Map<String, Object> cuerpo) {
		JsonNode respuesta;
		try {
			byte[] bytes = http.post().uri(ruta).contentType(MediaType.APPLICATION_JSON).accept(MediaType.ALL)
					.body(cuerpo).retrieve().body(byte[].class);
			respuesta = json.readTree(bytes == null ? new byte[0] : bytes);
		} catch (RestClientException | JacksonException error) {
			throw falla("El sistema de pago no respondió bien a " + ruta + ": " + error.getClass().getSimpleName(),
					error);
		}
		if (!respuesta.isObject()) {
			throw falla("El sistema de pago respondió algo que no es un objeto en " + ruta, null);
		}
		if (verdadero(respuesta.path("error"))) {
			throw falla("El sistema de pago rechazó " + ruta + ": " + textoONulo(respuesta.path("mensaje")), null);
		}
		return respuesta;
	}

	private String textoDelQr(JsonNode respuesta) {
		String base64 = textoONulo(respuesta.path("qr_simple_base64"));
		if (base64 != null) {
			try {
				return LectorDeQr.leer(Base64.getMimeDecoder().decode(base64.substring(base64.indexOf(',') + 1)));
			} catch (IllegalArgumentException error) {
				throw falla("El QR del sistema de pago no es base64 válido", error);
			}
		}
		String url = textoONulo(respuesta.path("qr_simple_url"));
		if (url == null) {
			throw falla("El sistema de pago no devolvió el QR", null);
		}
		return LectorDeQr.leer(descargar(url));
	}

	private byte[] descargar(String url) {
		URI direccion;
		try {
			direccion = URI.create(url);
		} catch (IllegalArgumentException error) {
			throw falla("La URL del QR no es válida", error);
		}
		if (!"https".equalsIgnoreCase(direccion.getScheme()) && !"http".equalsIgnoreCase(direccion.getScheme())) {
			throw falla("La URL del QR no es web", null);
		}
		try {
			return http.get().uri(direccion).retrieve().body(byte[].class);
		} catch (RestClientException error) {
			throw falla("No se pudo descargar el QR: " + error.getClass().getSimpleName(), error);
		}
	}

	// datos llega como objeto o como arreglo; solo cuenta la deuda con nuestro identificador, si lo trae
	private static JsonNode deudaConIdentificador(JsonNode datos, String referencia) {
		for (JsonNode deuda : datos.isArray() ? datos : List.of(datos)) {
			if (!deuda.isObject()) {
				continue;
			}
			String identificador = textoONulo(deuda.path("identificador"));
			if (identificador == null || identificador.equals(referencia)) {
				return deuda;
			}
		}
		return null;
	}

	// true, 1, "1" o "true"
	private static boolean verdadero(JsonNode valor) {
		if (valor.isBoolean()) {
			return valor.booleanValue();
		}
		if (valor.isNumber()) {
			return valor.intValue() != 0;
		}
		String texto = textoONulo(valor);
		return "1".equals(texto) || "true".equalsIgnoreCase(texto);
	}

	// Solo con la fecha no hay hora del pago: null, y la conciliación usa la hora en que lo aplica
	private static LocalDateTime fechaYHora(JsonNode valor) {
		String texto = textoONulo(valor);
		if (texto == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(texto, FECHA_Y_HORA);
		} catch (DateTimeParseException error) {
			return null;
		}
	}

	private static String textoONulo(JsonNode valor) {
		if (valor.isMissingNode() || valor.isNull()) {
			return null;
		}
		String texto = valor.asString().strip();
		return texto.isEmpty() ? null : texto;
	}

	private SistemaDePagoNoDisponibleException falla(String mensaje, Throwable causa) {
		return new SistemaDePagoNoDisponibleException(mensaje.replace(cuenta.appkey(), "***"), causa);
	}
}
