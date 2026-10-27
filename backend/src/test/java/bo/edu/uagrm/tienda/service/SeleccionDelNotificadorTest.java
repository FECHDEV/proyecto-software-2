package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import bo.edu.uagrm.tienda.config.CredencialesSmtp;
import bo.edu.uagrm.tienda.config.ProgramacionConfig;

class SeleccionDelNotificadorTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withUserConfiguration(ProgramacionConfig.class, NotificadorCorreoSimulado.class,
					NotificadorCorreoSmtp.class)
			.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
			.withPropertyValues("tienda.correo.marca=Tienda", "tienda.correo.remitente=Tienda <no-responder@mail.com>",
					"tienda.correo.url-del-sitio=http://localhost");

	@Test
	void sinProveedorSeUsaElSimulador() {
		contexto.run(ctx -> assertThat(ctx).getBean(NotificadorCorreo.class)
				.isInstanceOf(NotificadorCorreoSimulado.class));
	}

	@Test
	void conSmtpSeUsaElAdaptadorYEnviaEnSegundoPlano() {
		conSmtp().run(ctx -> {
			assertThat(ctx).hasSingleBean(NotificadorCorreo.class).doesNotHaveBean(NotificadorCorreoSimulado.class);
			assertThat(AopUtils.isAopProxy(ctx.getBean(NotificadorCorreo.class))).isTrue();
		});
	}

	@Test
	void conSmtpSinLaUrlDelSitioNoArranca() {
		conSmtp().withPropertyValues("tienda.correo.url-del-sitio=")
				.run(ctx -> assertThat(ctx).hasFailed().getFailure()
						.hasStackTraceContaining("tienda.correo.url-del-sitio"));
	}

	// Revisión total (26/10/2026): con smtp y sin la cuenta de Gmail no arranca, en vez de fallar en cada envío
	@Test
	void conSmtpSinUsuarioOContrasenaNoArranca() {
		conSmtp().withPropertyValues("spring.mail.username=")
				.run(ctx -> assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("TIENDA_CORREO_USUARIO"));
		conSmtp().withPropertyValues("spring.mail.password= ")
				.run(ctx -> assertThat(ctx).hasFailed().getFailure()
						.hasStackTraceContaining("TIENDA_CORREO_CONTRASENA"));
	}

	// La contraseña nunca sale en un mensaje
	@Test
	void lasCredencialesNoMuestranLaContrasena() {
		assertThat(new CredencialesSmtp("tienda@mail.com", "clave-de-aplicacion").toString())
				.contains("tienda@mail.com").doesNotContain("clave-de-aplicacion");
	}

	private ApplicationContextRunner conSmtp() {
		return contexto.withPropertyValues("tienda.correo.proveedor=smtp", "spring.mail.username=tienda@mail.com",
				"spring.mail.password=clave-de-aplicacion");
	}
}
