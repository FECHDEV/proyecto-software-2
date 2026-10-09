package bo.edu.uagrm.tienda.service;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import bo.edu.uagrm.tienda.config.CorreoProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Envía los correos por SMTP: Gmail con una cuenta del proyecto y una contraseña de aplicación (decisiones.md →
// 'Notificaciones'). En segundo plano y sin propagar los fallos: el envío no puede cambiar la respuesta de quien lo
// pide. Cada correo va en texto plano y en HTML; en el HTML, todo dato que escribió un usuario se escapa
@Slf4j
@Service
@ConditionalOnProperty(name = "tienda.correo.proveedor", havingValue = "smtp")
@RequiredArgsConstructor
public class NotificadorCorreoSmtp implements NotificadorCorreo {

	private final JavaMailSender mailSender;
	private final CorreoProperties propiedades;

	@Async
	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		// El token es base64 para URL (TokenRecuperacion): va tal cual en el enlace
		String enlace = propiedades.urlDelSitio() + "/restablecer?token=" + token;
		String marca = propiedades.marca();
		String texto = """
				Hola, %s:

				Recibimos una solicitud para recuperar la contraseña de tu cuenta de %s. Para elegir una \
				contraseña nueva, abre este enlace:

				%s

				El enlace vence en 1 hora y sirve una sola vez. Si no pediste recuperar tu contraseña, ignora este \
				correo: tu contraseña sigue siendo la misma.
				""".formatted(nombre, marca, enlace);
		String html = """
				<p>Hola, %s:</p>
				<p>Recibimos una solicitud para recuperar la contraseña de tu cuenta de %s.</p>
				%s
				<p>O copia este enlace en tu navegador:<br><a href="%s" style="color:#8a5a00;word-break:break-all">%s</a></p>
				<p style="color:#5d636b">El enlace vence en 1 hora y sirve una sola vez. Si no pediste recuperar tu \
				contraseña, ignora este correo: tu contraseña sigue siendo la misma.</p>
				""".formatted(htmlEscape(nombre), htmlEscape(marca), boton("Elegir una contraseña nueva", enlace),
				enlace, enlace);
		enviar("recuperación", correo, "Recupera tu contraseña de " + marca, texto, html);
	}

	private void enviar(String cual, String para, String asunto, String texto, String cuerpoHtml) {
		try {
			MimeMessage mensaje = mailSender.createMimeMessage();
			MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
			ayudante.setFrom(propiedades.remitente());
			ayudante.setTo(para);
			ayudante.setSubject(asunto);
			ayudante.setText(texto, plantilla(cuerpoHtml));
			mailSender.send(mensaje);
		} catch (MessagingException | RuntimeException error) {
			// Cualquier fallo, también uno inesperado: en segundo plano no hay quién lo atrape. Sin el contenido del
			// correo: el de recuperación lleva el token
			log.error("No se pudo enviar el correo de {} a {}: {}", cual, para, error.getMessage());
		}
	}

	private String plantilla(String cuerpo) {
		return """
				<!DOCTYPE html>
				<html lang="es"><body style="margin:0;padding:24px;background:#f3f2ee;font-family:Arial,sans-serif;color:#1d1f22">
				<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden">
				<div style="background:#16181b;border-bottom:3px solid #f2b81c;padding:20px 24px;color:#f2b81c;font-size:22px;font-weight:bold;letter-spacing:0.02em;text-transform:uppercase">%s</div>
				<div style="padding:24px;font-size:15px;line-height:1.5">%s</div>
				</div>
				</body></html>
				""".formatted(htmlEscape(propiedades.marca()), cuerpo);
	}

	private static String boton(String texto, String enlace) {
		return """
				<p><a href="%s" style="display:inline-block;padding:12px 20px;background:#f2b81c;color:#16181b;\
				text-decoration:none;border-radius:6px;font-weight:bold">%s</a></p>
				""".formatted(enlace, texto);
	}
}
