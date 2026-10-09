package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
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
	private final NotificadorCorreo notificador = new NotificadorCorreoSmtp(mailSender,
			new CorreoProperties("Tienda", "Tienda <no-responder@mail.com>", "https://tienda.bo/"));

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

	@Test
	void unErrorInesperadoTambienSeRegistraSinPropagarse(CapturedOutput salida) {
		willThrow(new IllegalStateException("sesión de correo rota")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarRecuperacion("ana@mail.com", "Ana", "token")).doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de recuperación");
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
