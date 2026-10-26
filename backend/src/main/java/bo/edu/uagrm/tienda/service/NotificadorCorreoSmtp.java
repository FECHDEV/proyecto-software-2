package bo.edu.uagrm.tienda.service;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import bo.edu.uagrm.tienda.config.CorreoProperties;
import bo.edu.uagrm.tienda.config.RetiroProperties;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
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

	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final JavaMailSender mailSender;
	private final CorreoProperties propiedades;
	private final RetiroProperties retiro;

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
				""".formatted(escapar(nombre), escapar(marca), boton("Elegir una contraseña nueva", enlace),
				enlace, enlace);
		enviar("recuperación", correo, "Recupera tu contraseña de " + marca, texto, html);
	}

	// De usted, como la tienda (HU-20 RF-14)
	@Async
	@Override
	public void enviarVerificacion(String correo, String nombre, String token) {
		String enlace = propiedades.urlDelSitio() + "/verificacion?token=" + token;
		String marca = propiedades.marca();
		String texto = """
				Hola, %s:

				Para confirmar pedidos en %s necesitamos verificar su correo. Abra este enlace:

				%s

				El enlace vence en 24 horas y sirve una sola vez. Si usted no creó una cuenta, ignore este correo.
				""".formatted(nombre, marca, enlace);
		String html = """
				<p>Hola, %s:</p>
				<p>Para confirmar pedidos en %s necesitamos verificar su correo.</p>
				%s
				<p>O copie este enlace en su navegador:<br><a href="%s" style="color:#8a5a00;word-break:break-all">%s</a></p>
				<p style="color:#5d636b">El enlace vence en 24 horas y sirve una sola vez. Si usted no creó una cuenta, \
				ignore este correo.</p>
				""".formatted(escapar(nombre), escapar(marca), boton("Verificar mi correo", enlace), enlace,
				enlace);
		enviar("verificación", correo, "Confirme su correo en " + marca, texto, html);
	}

	// HU-21 RF-1 a RF-3: qué compró, cuánto pagó y cómo lo retira, con el enlace a su pedido
	@Async
	@Override
	public void enviarPedidoPagado(CorreoDelPedido pedido) {
		String enlace = propiedades.urlDelSitio() + "/mis-pedidos/" + pedido.numero();
		String marca = propiedades.marca();
		String fecha = pedido.fechaPago().format(FECHA_HORA);
		String total = FormatoBolivia.dinero(pedido.total());
		List<Dato> retiro = datosDeRetiro(pedido);

		StringBuilder texto = new StringBuilder("""
				Hola, %s:

				Recibimos el pago de su pedido N.º %d en %s, el %s. Este es el detalle:

				""".formatted(pedido.nombre(), pedido.numero(), marca, fecha));
		StringBuilder filas = new StringBuilder();
		for (CorreoDelPedido.Linea linea : pedido.lineas()) {
			String detalle = detalle(linea);
			String subtotal = FormatoBolivia.dinero(linea.subtotal());
			texto.append("- %s: %s = %s\n".formatted(linea.nombre(), detalle, subtotal));
			filas.append("""
					<tr><td style="padding:8px 0;border-bottom:1px solid #e3e1da">%s<br><span style="color:#5d636b">%s</span></td>\
					<td style="padding:8px 0 8px 12px;border-bottom:1px solid #e3e1da;text-align:right;white-space:nowrap">%s</td></tr>
					""".formatted(escapar(linea.nombre()), escapar(detalle), subtotal));
		}
		texto.append("\nTotal: %s\n\n".formatted(total));
		StringBuilder datosHtml = new StringBuilder();
		for (Dato dato : retiro) {
			texto.append("%s: %s\n".formatted(dato.etiqueta(), dato.valor()));
			datosHtml.append("<p style=\"margin:4px 0\"><strong>%s:</strong> %s</p>\n".formatted(dato.etiqueta(),
					escapar(dato.valor())));
		}
		texto.append("""

				Al retirarlo, dé el número del pedido; quien retira muestra su documento.

				Ver mi pedido: %s
				""".formatted(enlace));

		String html = """
				<p>Hola, %s:</p>
				<p>Recibimos el pago de su pedido <strong>N.º %d</strong> en %s, el %s. Este es el detalle:</p>
				<table style="width:100%%;border-collapse:collapse;font-size:15px">
				%s<tr><td style="padding:12px 0 0;font-weight:bold">Total</td>\
				<td style="padding:12px 0 0 12px;text-align:right;font-weight:bold;white-space:nowrap">%s</td></tr>
				</table>
				<div style="margin-top:20px">%s</div>
				<p style="color:#5d636b">Al retirarlo, dé el número del pedido; quien retira muestra su documento.</p>
				%s
				""".formatted(escapar(pedido.nombre()), pedido.numero(), escapar(marca), fecha, filas, total,
				datosHtml, boton("Ver mi pedido", enlace));
		enviar("pedido pagado", pedido.correo(), "Pedido N.º %d pagado en %s".formatted(pedido.numero(), marca),
				texto.toString(), html);
	}

	// HU-21 RF-4: la devolución se hace fuera del sistema; la tienda llama al teléfono del pedido
	@Async
	@Override
	public void enviarPagoADevolver(CorreoDelPedido pedido) {
		String marca = propiedades.marca();
		String monto = FormatoBolivia.dinero(pedido.total());
		String texto = """
				Hola, %s:

				Recibimos el pago de %s de su pedido N.º %d en %s, pero llegó cuando el pedido ya estaba cerrado y \
				no pudimos reservar sus productos.

				Le devolveremos el dinero: nos comunicaremos con usted al %s, el teléfono del pedido.
				""".formatted(pedido.nombre(), monto, pedido.numero(), marca, pedido.telefonoRetira());
		String html = """
				<p>Hola, %s:</p>
				<p>Recibimos el pago de <strong>%s</strong> de su pedido <strong>N.º %d</strong> en %s, pero llegó \
				cuando el pedido ya estaba cerrado y no pudimos reservar sus productos.</p>
				<p>Le devolveremos el dinero: nos comunicaremos con usted al <strong>%s</strong>, el teléfono del \
				pedido.</p>
				""".formatted(escapar(pedido.nombre()), monto, pedido.numero(), escapar(marca),
				escapar(pedido.telefonoRetira()));
		enviar("pago a devolver", pedido.correo(), "Pedido N.º %d: le devolveremos su pago".formatted(pedido.numero()),
				texto, html);
	}

	// Quién retira siempre; la dirección y el horario, solo si están configurados (HU-12 RF-17)
	// La etiqueta es fija; el valor lo escribió alguien y se escapa en el HTML
	private record Dato(String etiqueta, String valor) {
	}

	private List<Dato> datosDeRetiro(CorreoDelPedido pedido) {
		List<Dato> datos = new ArrayList<>();
		datos.add(new Dato("Retira", pedido.nombreRetira() + " (" + pedido.telefonoRetira() + ")"));
		if (retiro.direccion() != null) {
			datos.add(new Dato("Dónde retirarlo", retiro.direccion()));
		}
		if (retiro.horario() != null) {
			datos.add(new Dato("Horario", retiro.horario()));
		}
		return datos;
	}

	// Solo los caracteres especiales del HTML: el correo va en UTF-8 y «peña» se lee igual en el código (HU-21 RF-8).
	// Todos los correos escapan así
	private static String escapar(String texto) {
		return htmlEscape(texto, StandardCharsets.UTF_8.name());
	}

	// Como en la página del pedido: «2,5 × Bs 12,00 / metro»
	private static String detalle(CorreoDelPedido.Linea linea) {
		String cantidad = FormatoBolivia.numero(linea.cantidad(), 0, linea.unidad().decimales());
		String unidad = linea.unidad() == UnidadVenta.UNIDAD ? "" : " / " + linea.unidad().name().toLowerCase(Locale.ROOT);
		return cantidad + " × " + FormatoBolivia.dinero(linea.precio()) + unidad;
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
				""".formatted(escapar(propiedades.marca()), cuerpo);
	}

	private static String boton(String texto, String enlace) {
		return """
				<p><a href="%s" style="display:inline-block;padding:12px 20px;background:#f2b81c;color:#16181b;\
				text-decoration:none;border-radius:6px;font-weight:bold">%s</a></p>
				""".formatted(enlace, texto);
	}
}
