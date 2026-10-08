package bo.edu.uagrm.tienda.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Habilita las tareas programadas y el envío de correos en segundo plano (NotificadorCorreoSmtp). Las propiedades de
// correo se registran acá y no en el adaptador SMTP: las puede necesitar cualquier servicio, con cualquier proveedor
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(CorreoProperties.class)
public class ProgramacionConfig {
}
