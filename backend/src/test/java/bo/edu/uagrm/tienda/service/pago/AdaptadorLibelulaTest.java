package bo.edu.uagrm.tienda.service.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import bo.edu.uagrm.tienda.config.LibelulaProperties;

// HU-13 RF-13 (decisiones.md → Pagos). Contra un servidor falso: las pruebas nunca llaman a Libélula
class AdaptadorLibelulaTest {

	private static final String APPKEY = "clave-de-prueba-0000";
	private static final String REGISTRAR = "https://api.libelula.test/rest/deuda/registrar";
	private static final String CONSULTAR = "https://api.libelula.test/rest/deuda/consultar_deudas/por_identificador";
	private static final LibelulaProperties CUENTA = new LibelulaProperties("https://api.libelula.test/", APPKEY,
			"pagos@tienda.test", "https://tienda.test/");
	// 13/10/2026 a las 15:00 en La Paz
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-13T19:00:00Z"), ZoneId.of("America/La_Paz"));

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

	private AdaptadorLibelula adaptador(Clock reloj) {
		return new AdaptadorLibelula(builder, CUENTA, reloj);
	}

	private final AdaptadorLibelula adaptador = adaptador(RELOJ);

	private static String base64(String texto) {
		return Base64.getEncoder().encodeToString(LectorDeQrTest.png(texto));
	}

	private static String registrada(String camposQr) {
		return """
				{"error": false, "mensaje": "Deuda registrada", "id_transaccion": "a1b2-c3d4",
				 "url_pasarela_pagos": "https://pagos.libelula.test/?id=a1b2-c3d4", %s}
				""".formatted(camposQr);
	}

	// Libélula puede responder JSON con otro Content-Type: el adaptador no depende de él
	private void responderRegistro(String cuerpo) {
		servidor.expect(once(), requestTo(REGISTRAR)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(cuerpo, MediaType.TEXT_HTML));
	}

	@Test
	void registraLaDeudaConLosDatosDelCobro() {
		servidor.expect(once(), requestTo(REGISTRAR)).andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.appkey").value(APPKEY))
				.andExpect(jsonPath("$.email_cliente").value("pagos@tienda.test"))
				.andExpect(jsonPath("$.identificador").value(Matchers.matchesPattern("TM-P40-[0-9a-f]{8}")))
				.andExpect(jsonPath("$.descripcion").value("Pedido N.º 40 — Tienda Montero"))
				.andExpect(jsonPath("$.moneda").value("BOB"))
				.andExpect(jsonPath("$.fecha_vencimiento").value("2026-10-13 23:59"))
				.andExpect(jsonPath("$.callback_url")
						.value(Matchers.matchesPattern("https://tienda\\.test/api/pagos/aviso\\?referencia=TM-P40-[0-9a-f]{8}")))
				.andExpect(jsonPath("$.url_retorno").value("https://tienda.test/mis-pedidos/40"))
				.andExpect(jsonPath("$.lineas_detalle_deuda[0].concepto").value("Pedido N.º 40 — Tienda Montero"))
				.andExpect(jsonPath("$.lineas_detalle_deuda[0].cantidad").value(1))
				.andExpect(jsonPath("$.lineas_detalle_deuda[0].costo_unitario").value(150.50))
				.andRespond(withSuccess(registrada("\"qr_simple_base64\": \"" + base64("QR-40") + "\""),
						MediaType.APPLICATION_JSON));

		CobroGenerado cobro = adaptador.generarCobro(40L, new BigDecimal("150.50"), "Pedido N.º 40 — Tienda Montero",
				"/mis-pedidos/40");

		servidor.verify();
		assertThat(cobro.referencia()).matches("TM-P40-[0-9a-f]{8}");
	}

	// HU-13 RF-13: el QR del mostrador no tiene a dónde volver en «Mis pedidos»: vuelve a la portada
	@Test
	void sinRutaDeRetornoVuelveALaPortada() {
		servidor.expect(once(), requestTo(REGISTRAR)).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.url_retorno").value("https://tienda.test/"))
				.andRespond(withSuccess(registrada("\"qr_simple_base64\": \"" + base64("QR-40") + "\""),
						MediaType.APPLICATION_JSON));

		adaptador.generarCobro(40L, BigDecimal.TEN, "Venta N.º 40", null);

		servidor.verify();
	}

	@Test
	void devuelveElTextoDelQrYLaPasarela() {
		responderRegistro(registrada("\"qr_simple_base64\": \"" + base64(LectorDeQrTest.TEXTO) + "\""));

		CobroGenerado cobro = adaptador.generarCobro(40L, new BigDecimal("150.50"), "Pedido N.º 40", null);

		assertThat(cobro.codigoQr()).isEqualTo(LectorDeQrTest.TEXTO);
		assertThat(cobro.urlPasarela()).isEqualTo("https://pagos.libelula.test/?id=a1b2-c3d4");
	}

	@Test
	void aceptaElQrConPrefijoData() {
		responderRegistro(registrada("\"qr_simple_base64\": \"data:image/png;base64," + base64("QR-40") + "\""));

		assertThat(adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null).codigoQr()).isEqualTo("QR-40");
	}

	@Test
	void descargaElQrSiSoloVieneLaUrl() {
		responderRegistro(registrada("\"qr_simple_url\": \"https://qr.libelula.test/a1b2.png\""));
		servidor.expect(once(), requestTo("https://qr.libelula.test/a1b2.png")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(LectorDeQrTest.png("QR-URL"), MediaType.IMAGE_PNG));

		assertThat(adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null).codigoQr()).isEqualTo("QR-URL");
	}

	// Libélula vence la deuda por día: un pedido que vence pasada la medianoche se paga hasta el día siguiente
	@Test
	void cercaDeMedianocheVenceAlDiaSiguiente() {
		Clock casiMedianoche = Clock.fixed(Instant.parse("2026-10-14T03:55:00Z"), ZoneId.of("America/La_Paz"));
		servidor.expect(once(), requestTo(REGISTRAR)).andExpect(jsonPath("$.fecha_vencimiento").value("2026-10-14 23:59"))
				.andRespond(withSuccess(registrada("\"qr_simple_base64\": \"" + base64("QR") + "\""),
						MediaType.APPLICATION_JSON));

		adaptador(casiMedianoche).generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null);

		servidor.verify();
	}

	// Si un intento falla después de registrar la deuda, el siguiente no choca con el identificador anterior
	@Test
	void identificadoresDistintosParaElMismoPedido() {
		servidor.expect(times(2), requestTo(REGISTRAR))
				.andRespond(withSuccess(registrada("\"qr_simple_base64\": \"" + base64("QR") + "\""),
						MediaType.APPLICATION_JSON));

		String primera = adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null).referencia();
		String segunda = adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null).referencia();

		assertThat(primera).isNotEqualTo(segunda);
	}

	@Test
	void errorDeLibelula() {
		responderRegistro("{\"error\": true, \"mensaje\": \"Appkey inválida\"}");

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class).hasMessageContaining("Appkey inválida");
	}

	@Test
	void errorComoNumero() {
		responderRegistro("{\"error\": 1, \"mensaje\": \"Datos incompletos\"}");

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void respuestaHttpDeError() {
		servidor.expect(once(), requestTo(REGISTRAR)).andRespond(withServerError());

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void respuestaQueNoEsJson() {
		responderRegistro("<html>Mantenimiento</html>");

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void sinQr() {
		responderRegistro(registrada("\"otro\": 1"));

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void qrIlegible() {
		responderRegistro(registrada("\"qr_simple_base64\": \"no-es-base64!!\""));

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	// Una URL de QR que no es web no se descarga
	@Test
	void urlDelQrQueNoEsWeb() {
		responderRegistro(registrada("\"qr_simple_url\": \"file:///etc/passwd\""));

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void elMensajeDeErrorNoLlevaLaAppkey() {
		responderRegistro("{\"error\": true, \"mensaje\": \"Appkey " + APPKEY + " inválida\"}");

		assertThatThrownBy(() -> adaptador.generarCobro(40L, BigDecimal.TEN, "Pedido N.º 40", null))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class)
				.hasMessageNotContaining(APPKEY);
	}

	private void responderConsulta(String cuerpo) {
		servidor.expect(once(), requestTo(CONSULTAR)).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.appkey").value(APPKEY))
				.andExpect(jsonPath("$.identificador").value("TM-P40-0a1b2c3d"))
				.andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
	}

	private ResultadoDelCobro consultar() {
		return adaptador.consultar("TM-P40-0a1b2c3d");
	}

	@Test
	void pagadaDevuelvePagadoConLaFecha() {
		responderConsulta("""
				{"error": 0, "datos": {"identificador": "TM-P40-0a1b2c3d", "pagado": true,
				 "fecha_pago": "2026-10-13 15:04:30", "valor_total": 150.50}}
				""");

		assertThat(consultar())
				.isEqualTo(new ResultadoDelCobro(EstadoDelCobro.PAGADO, LocalDateTime.of(2026, 10, 13, 15, 4, 30)));
	}

	// Sin la hora, la fecha no sirve para el registro del pago: la conciliación usa la hora en que lo aplica
	@Test
	void pagadaSoloConLaFecha() {
		responderConsulta("""
				{"error": 0, "datos": {"identificador": "TM-P40-0a1b2c3d", "pagado": true, "fecha_pago": "2026-10-13"}}
				""");

		assertThat(consultar()).isEqualTo(new ResultadoDelCobro(EstadoDelCobro.PAGADO, null));
	}

	@Test
	void noPagadaSiguePendiente() {
		responderConsulta("""
				{"error": 0, "datos": {"identificador": "TM-P40-0a1b2c3d", "pagado": false, "fecha_pago": null,
				 "deuda_expirada": true}}
				""");

		assertThat(consultar()).isEqualTo(ResultadoDelCobro.pendiente());
	}

	@Test
	void datosComoArreglo() {
		responderConsulta("""
				{"error": 0, "datos": [{"identificador": "TM-P40-0a1b2c3d", "pagado": true,
				 "fecha_pago": "2026-10-13 15:04:30"}]}
				""");

		assertThat(consultar().estado()).isEqualTo(EstadoDelCobro.PAGADO);
	}

	@Test
	void pagadoComoTextoONumero() {
		responderConsulta("""
				{"error": "0", "datos": {"identificador": "TM-P40-0a1b2c3d", "pagado": "1",
				 "fecha_pago": "2026-10-13 15:04:30"}}
				""");

		assertThat(consultar().estado()).isEqualTo(EstadoDelCobro.PAGADO);
	}

	// Una respuesta que trae otra deuda no confirma la nuestra
	@Test
	void otraDeudaNoCuenta() {
		responderConsulta("""
				{"error": 0, "datos": [{"identificador": "TM-P99-ffffffff", "pagado": true,
				 "fecha_pago": "2026-10-13 15:04:30"}]}
				""");

		assertThat(consultar()).isEqualTo(ResultadoDelCobro.pendiente());
	}

	@Test
	void errorAlConsultar() {
		responderConsulta("{\"error\": true, \"mensaje\": \"Servicio no disponible\"}");

		assertThatThrownBy(this::consultar).isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void consultaQueNoResponde() {
		servidor.expect(once(), requestTo(CONSULTAR)).andRespond(withServerError());

		assertThatThrownBy(this::consultar).isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}
}
