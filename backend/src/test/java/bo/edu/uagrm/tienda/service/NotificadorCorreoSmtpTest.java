package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import bo.edu.uagrm.tienda.config.CorreoProperties;
import bo.edu.uagrm.tienda.config.RetiroProperties;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoSmtpTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final CorreoProperties propiedades = new CorreoProperties("Tienda", "Tienda <no-responder@mail.com>",
			"https://tienda.bo/");
	private final NotificadorCorreo notificador = new NotificadorCorreoSmtp(mailSender, propiedades,
			new RetiroProperties("Av. Principal 123", "Lunes a sábado, de 8:00 a 18:00"));

	@BeforeEach
	void mensajesReales() {
		given(mailSender.createMimeMessage()).willAnswer(invocacion -> new MimeMessage((Session) null));
	}

	// HU-03 RF-13
	@Test
	void laRecuperacionVaAlUsuarioDesdeElRemitenteDelSitio() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("ana@mail.com"));
		assertThat(((InternetAddress) mensaje.getFrom()[0]).getAddress()).isEqualTo("no-responder@mail.com");
		assertThat(((InternetAddress) mensaje.getFrom()[0]).getPersonal()).isEqualTo("Tienda");
		assertThat(mensaje.getSubject()).isEqualTo("Recupera tu contraseña de Tienda");
	}

	// HU-03 RF-1, RF-13
	@Test
	void laRecuperacionLlevaElEnlaceConElTokenEnTextoYEnHtml() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		List<String> partes = partes(enviado());
		assertThat(partes).hasSize(2)
				.allSatisfy(parte -> assertThat(parte)
						.contains("https://tienda.bo/restablecer?token=token-de-prueba")
						.contains("1 hora"));
	}

	// HU-03 RF-13
	@Test
	void elCorreoVaFirmadoConLaMarcaDelSitio() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		assertThat(partes(enviado())).allSatisfy(parte -> assertThat(parte).contains("Tienda"));
	}

	// HU-03 RF-13
	@Test
	void elNombreDelUsuarioSeEscapaEnElHtml() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "<script>alert(1)</script>", "token-de-prueba");

		String html = partes(enviado()).get(1);
		assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
	}

	// HU-03 RF-13: los colores de la marca (decisiones.md → Paleta y modos); los de la plantilla vieja no quedan
	@Test
	void elHtmlUsaLosColoresDeLaMarca() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		String html = partes(enviado()).get(1);
		assertThat(html).contains("#16181b", "#f2b81c").doesNotContain("#0b1020", "#f5b301", "#f4f6ff");
	}

	// HU-03 RF-2
	@Test
	void unFalloDelEnvioSeRegistraSinPropagarseNiMostrarElToken(CapturedOutput salida) {
		willThrow(new MailSendException("servidor caído")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-secreto"))
				.doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de recuperación")
				.doesNotContain("token-secreto");
	}

	// HU-20 RF-14
	@Test
	void laVerificacionLlevaElAsuntoYElEnlaceEnTextoYEnHtml() throws Exception {
		notificador.enviarVerificacion("ana@mail.com", "Ana", "token-de-prueba");

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("ana@mail.com"));
		assertThat(mensaje.getSubject()).isEqualTo("Confirme su correo en Tienda");
		assertThat(partes(mensaje)).hasSize(2)
				.allSatisfy(parte -> assertThat(parte)
						.contains("https://tienda.bo/verificacion?token=token-de-prueba")
						.contains("24 horas"));
	}

	// HU-20 RF-14
	@Test
	void laVerificacionEscapaElNombreEnElHtml() throws Exception {
		notificador.enviarVerificacion("ana@mail.com", "<b>Ana</b>", "token-de-prueba");

		String html = partes(enviado()).get(1);
		assertThat(html).doesNotContain("<b>Ana</b>").contains("&lt;b&gt;Ana&lt;/b&gt;");
	}

	// HU-20 RF-14: el texto del HTML sale de un bloque de texto; un corte de línea mal hecho deja tabulaciones
	@Test
	void elHtmlDeLaVerificacionNoLlevaTabulaciones() throws Exception {
		notificador.enviarVerificacion("ana@mail.com", "Ana", "token-de-prueba");

		assertThat(partes(enviado()).get(1)).doesNotContain("\t");
	}

	// HU-20 RF-13
	@Test
	void unFalloDeLaVerificacionSeRegistraSinPropagarseNiMostrarElToken(CapturedOutput salida) {
		willThrow(new MailSendException("servidor caído")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarVerificacion("ana@mail.com", "Ana", "token-secreto"))
				.doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de verificación")
				.doesNotContain("token-secreto");
	}

	@Test
	void unErrorInesperadoTambienSeRegistraSinPropagarse(CapturedOutput salida) {
		willThrow(new IllegalStateException("sesión de correo rota")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarRecuperacion("ana@mail.com", "Ana", "token")).doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de recuperación");
	}

	// HU-21: el pedido N.º 12 de Ana, con dos líneas
	private static CorreoDelPedido pedido(String nombreDeProducto, String nombreRetira) {
		return new CorreoDelPedido("ana@mail.com", "Ana", 12L, LocalDateTime.of(2026, 10, 26, 14, 5),
				List.of(new CorreoDelPedido.Linea(nombreDeProducto, UnidadVenta.UNIDAD, new BigDecimal("2"),
						new BigDecimal("85.00"), new BigDecimal("170.00")),
						new CorreoDelPedido.Linea("Wincha de 5 m", UnidadVenta.UNIDAD, new BigDecimal("1"),
								new BigDecimal("1045.50"), new BigDecimal("1045.50"))),
				new BigDecimal("1215.50"), nombreRetira, "70012345");
	}

	private static CorreoDelPedido pedido() {
		return pedido("Martillo de peña", "Ana Pérez");
	}

	// HU-21 RF-1
	@Test
	void elPedidoPagadoVaAlClienteConSuAsunto() throws Exception {
		notificador.enviarPedidoPagado(pedido());

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("ana@mail.com"));
		assertThat(((InternetAddress) mensaje.getFrom()[0]).getAddress()).isEqualTo("no-responder@mail.com");
		assertThat(mensaje.getSubject()).isEqualTo("Pedido N.º 12 pagado en Tienda");
	}

	// HU-21 RF-3: lo mismo en el texto plano y en el HTML
	@Test
	void elPedidoPagadoLlevaLineasTotalQuienRetiraRetiroYEnlace() throws Exception {
		notificador.enviarPedidoPagado(pedido());

		assertThat(partes(enviado())).hasSize(2).allSatisfy(parte -> assertThat(parte)
				.contains("Ana", "N.º 12", "26/10/2026 14:05")
				.contains("Martillo de peña", "2 × Bs 85,00", "Bs 170,00")
				.contains("Wincha de 5 m", "1 × Bs 1.045,50", "Bs 1.045,50")
				.contains("Bs 1.215,50")
				.contains("Ana Pérez", "70012345")
				.contains("Av. Principal 123", "Lunes a sábado, de 8:00 a 18:00")
				.contains("https://tienda.bo/mis-pedidos/12")
				// Un solo fin de línea en todo el texto, también en Windows (/code-review, ronda 2)
				.doesNotContain("\r"));
	}

	// HU-21 RF-3 (/code-review, ronda 2): la dirección y el horario sin espacios de más, como en la página
	@Test
	void elRetiroVaSinEspaciosDeMas() throws Exception {
		new NotificadorCorreoSmtp(mailSender, propiedades, new RetiroProperties("  Av. Principal 123  ", "  De 8 a 18 "))
				.enviarPedidoPagado(pedido());

		assertThat(partes(enviado()).get(0)).contains("Dónde retirarlo: Av. Principal 123\n", "Horario: De 8 a 18\n");
	}

	// HU-21 RF-3, caso límite: sin dirección ni horario configurados, no se muestran esas líneas
	@Test
	void sinRetiroConfiguradoNoMuestraEsasLineas() throws Exception {
		new NotificadorCorreoSmtp(mailSender, propiedades, new RetiroProperties(null, " ")).enviarPedidoPagado(pedido());

		assertThat(partes(enviado())).allSatisfy(parte -> assertThat(parte)
				.doesNotContain("Dónde retirarlo")
				.doesNotContain("Horario")
				.contains("Ana Pérez"));
	}

	// HU-21 RF-3, caso límite
	@Test
	void conSoloElHorarioMuestraSoloEse() throws Exception {
		new NotificadorCorreoSmtp(mailSender, propiedades, new RetiroProperties("", "De 8:00 a 18:00"))
				.enviarPedidoPagado(pedido());

		assertThat(partes(enviado())).allSatisfy(parte -> assertThat(parte)
				.doesNotContain("Dónde retirarlo")
				.contains("Horario", "De 8:00 a 18:00"));
	}

	// HU-21 RF-3, caso límite: como en la página del pedido, «2,5 × Bs 12,00 / metro»
	@Test
	void lasCantidadesConDecimalesVanConSuUnidad() throws Exception {
		CorreoDelPedido cable = new CorreoDelPedido("ana@mail.com", "Ana", 15L, LocalDateTime.of(2026, 10, 26, 9, 0),
				List.of(new CorreoDelPedido.Linea("Cable 2,5 mm", UnidadVenta.METRO, new BigDecimal("2.50"),
						new BigDecimal("12.00"), new BigDecimal("30.00"))),
				new BigDecimal("30.00"), "Ana Pérez", "70012345");

		notificador.enviarPedidoPagado(cable);

		assertThat(partes(enviado())).allSatisfy(parte -> assertThat(parte).contains("2,5 × Bs 12,00 / metro"));
	}

	// HU-21 RF-4
	@Test
	void elPagoADevolverLlevaNumeroMontoYElAvisoDelTelefono() throws Exception {
		notificador.enviarPagoADevolver(pedido());

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("ana@mail.com"));
		assertThat(mensaje.getSubject()).isEqualTo("Pedido N.º 12: le devolveremos su pago");
		assertThat(partes(mensaje)).hasSize(2).allSatisfy(parte -> assertThat(parte)
				.contains("N.º 12", "Bs 1.215,50", "70012345")
				.contains("devolver")
				// Las líneas largas del texto siguen sin cortarse a la mitad (/code-review)
				.contains("ya estaba cerrado y no pudimos reservar sus productos")
				.doesNotContain("\r", "https://tienda.bo/mis-pedidos/12"));
	}

	// HU-21 RF-8
	@Test
	void losDatosDelPedidoSeEscapanEnElHtml() throws Exception {
		notificador.enviarPedidoPagado(pedido("<b>raro</b>", "<script>x</script>"));

		String html = partes(enviado()).get(1);
		assertThat(html).doesNotContain("<b>raro</b>", "<script>").contains("&lt;b&gt;raro&lt;/b&gt;",
				"&lt;script&gt;");
	}

	// HU-21: la plantilla de los otros correos, sin tabulaciones sueltas
	@Test
	void elHtmlDelPedidoNoLlevaTabulaciones() throws Exception {
		notificador.enviarPedidoPagado(pedido());
		notificador.enviarPagoADevolver(pedido());

		ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender, times(2)).send(mensajes.capture());
		for (MimeMessage mensaje : mensajes.getAllValues()) {
			assertThat(partes(mensaje).get(1)).doesNotContain("\t");
		}
	}

	// HU-21 RF-7
	@Test
	void siElEnvioDelPedidoFallaQuedaEnElLog(CapturedOutput salida) {
		willThrow(new MailSendException("servidor caído")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarPedidoPagado(pedido())).doesNotThrowAnyException();
		assertThatCode(() -> notificador.enviarPagoADevolver(pedido())).doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de pedido pagado a ana@mail.com")
				.contains("No se pudo enviar el correo de pago a devolver a ana@mail.com");
	}

	private MimeMessage enviado() {
		ArgumentCaptor<MimeMessage> mensaje = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(mensaje.capture());
		return mensaje.getValue();
	}

	// Las partes de texto del mensaje, en orden: primero el texto plano y después el HTML
	private static List<String> partes(Part parte) throws MessagingException, IOException {
		List<String> textos = new ArrayList<>();
		if (parte.getContent() instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart hija = multipart.getBodyPart(i);
				textos.addAll(partes(hija));
			}
		} else if (parte.getContent() instanceof String texto) {
			textos.add(texto);
		}
		return textos;
	}
}
