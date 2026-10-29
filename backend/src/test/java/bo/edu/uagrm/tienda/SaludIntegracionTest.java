package bo.edu.uagrm.tienda;

import static bo.edu.uagrm.tienda.IpsDePrueba.ipNueva;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import bo.edu.uagrm.tienda.service.AdministradorInicial;

// Salud de la API (decisiones.md → «Salud de la API»): el healthcheck de Docker pregunta sin sesión, y la respuesta
// no cuenta nada del servidor. Con el host de correo puesto, como en producción, para ver que no se revisa Gmail
@SpringBootTest(properties = "spring.mail.host=smtp.ejemplo.invalid")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaludIntegracionTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private HealthContributorRegistry contribuyentes;

	@BeforeEach
	void preparar() {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial
		administradorInicial.run(new DefaultApplicationArguments());
	}

	@Test
	void respondeSinSesionYSinDetalles() throws Exception {
		MvcTestResult salud = mvc.get().uri("/actuator/health").exchange();

		assertThat(salud).hasStatus(HttpStatus.OK);
		assertThat(salud.getResponse().getContentAsString()).isEqualTo("{\"status\":\"UP\"}");
	}

	// Solo la salud: ni las variables de entorno ni la configuración, aunque pregunte el Administrador
	@Test
	void noExponeOtrosEndpoints() throws Exception {
		assertThat(mvc.get().uri("/actuator/env").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

		String token = tokenDelAdministrador();
		for (String endpoint : new String[] { "/actuator/env", "/actuator/configprops", "/actuator/beans" }) {
			assertThat(mvc.get().uri(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange())
					.hasStatus(HttpStatus.NOT_FOUND);
		}
	}

	// Cada consulta revisaría la conexión con Gmail: un corte del correo no hace mala a la API
	@Test
	void noRevisaElServidorDeCorreo() {
		assertThat(contribuyentes.getContributor("mail")).isNull();
		assertThat(contribuyentes.getContributor("db")).isNotNull();
	}

	private String tokenDelAdministrador() throws Exception {
		MvcTestResult sesion = mvc.post().uri("/api/auth/inicio-sesion").with(ipNueva())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"admin@tienda.test\",\"contrasena\":\"clave-admin-pruebas\"}").exchange();
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}
}
