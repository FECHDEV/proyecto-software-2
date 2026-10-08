package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

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
		contexto.withPropertyValues("tienda.correo.proveedor=smtp").run(ctx -> {
			assertThat(ctx).hasSingleBean(NotificadorCorreo.class).doesNotHaveBean(NotificadorCorreoSimulado.class);
			assertThat(AopUtils.isAopProxy(ctx.getBean(NotificadorCorreo.class))).isTrue();
		});
	}

	@Test
	void conSmtpSinLaUrlDelSitioNoArranca() {
		contexto.withPropertyValues("tienda.correo.proveedor=smtp", "tienda.correo.url-del-sitio=")
				.run(ctx -> assertThat(ctx).hasFailed().getFailure()
						.hasStackTraceContaining("tienda.correo.url-del-sitio"));
	}
}
