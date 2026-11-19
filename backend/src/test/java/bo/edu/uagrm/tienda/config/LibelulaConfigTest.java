package bo.edu.uagrm.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import bo.edu.uagrm.tienda.service.pago.IPago;
import bo.edu.uagrm.tienda.service.pago.SistemaDePagoNoDisponibleException;

// Libélula no lee un cuerpo enviado por partes (Transfer-Encoding: chunked): responde «Debe especificar el parámetro
// AppKey» aunque la appkey vaya en el JSON. Visto el 13/10/2026 con la appkey real. El cliente de esta configuración
// manda el cuerpo entero, con Content-Length, y por HTTP/1.1. Contra un servidor HTTP local de verdad: el servidor falso
// de AdaptadorLibelulaTest no pasa por el cliente HTTP
class LibelulaConfigTest {

	private HttpServer servidor;
	private final AtomicReference<Headers> cabeceras = new AtomicReference<>();
	private final AtomicReference<String> cuerpo = new AtomicReference<>();

	@BeforeEach
	void levantar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/", intercambio -> {
			cabeceras.set(intercambio.getRequestHeaders());
			cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] respuesta = "{\"error\": 1, \"mensaje\": \"eco\"}".getBytes(StandardCharsets.UTF_8);
			intercambio.sendResponseHeaders(200, respuesta.length);
			intercambio.getResponseBody().write(respuesta);
			intercambio.close();
		});
		servidor.start();
	}

	@AfterEach
	void detener() {
		servidor.stop(0);
	}

	@Test
	void mandaElCuerpoEnteroConContentLengthYPorHttp11() {
		IPago pago = new LibelulaConfig().adaptadorLibelula(new LibelulaProperties(
				"http://127.0.0.1:" + servidor.getAddress().getPort(), "clave-de-prueba", "pagos@tienda.test",
				"http://localhost"), Clock.systemDefaultZone());

		assertThatThrownBy(() -> pago.generarCobro(7L, new BigDecimal("0.10"), "Pedido N.º 7 — Prueba", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class).hasMessageContaining("eco");

		assertThat(cabeceras.get().getFirst("Content-Length")).isNotNull();
		assertThat(cabeceras.get().getFirst("Transfer-Encoding")).isNull();
		assertThat(cabeceras.get().getFirst("Upgrade")).isNull();
		assertThat(cuerpo.get()).contains("\"appkey\":\"clave-de-prueba\"").contains("Pedido N.º 7 — Prueba");
		assertThat(Integer.parseInt(cabeceras.get().getFirst("Content-Length")))
				.isEqualTo(cuerpo.get().getBytes(StandardCharsets.UTF_8).length);
	}
}
