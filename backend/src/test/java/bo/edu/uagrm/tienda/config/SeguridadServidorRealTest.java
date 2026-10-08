package bo.edu.uagrm.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.service.AutenticacionService;

// Con Tomcat real: MockMvc no hace el despacho interno a /error que sigue a una excepción o a un sendError
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SeguridadServidorRealTest {

	@LocalServerPort
	private int puerto;

	@Autowired
	private TokenJwt tokenJwt;

	@MockitoBean
	private AutenticacionService autenticacionService;

	private HttpResponse<String> pedir(String ruta, String authorization) throws Exception {
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
		if (authorization != null) {
			peticion.header("Authorization", authorization);
		}
		return HttpClient.newHttpClient().send(peticion.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void falloInternoAlValidarLaSesionDevuelve500YNoCierraLaSesion() throws Exception {
		Usuario ana = Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null, LocalDateTime.of(2026, 9, 12, 11, 0));
		ReflectionTestUtils.setField(ana, "idUsuario", 7L);
		given(autenticacionService.usuarioActivo(7L)).willThrow(new IllegalStateException("La base de datos no responde"));

		HttpResponse<String> respuesta = pedir("/api/no-existe", "Bearer " + tokenJwt.emitir(ana).token());

		assertThat(respuesta.statusCode()).isEqualTo(500);
		assertThat(respuesta.headers().firstValue("WWW-Authenticate")).isEmpty();
	}

	@Test
	void peticionSinTokenSigueDevolviendo401() throws Exception {
		HttpResponse<String> respuesta = pedir("/api/no-existe", null);

		assertThat(respuesta.statusCode()).isEqualTo(401);
		assertThat(respuesta.body()).contains("NO_AUTENTICADO");
	}

	@Test
	void pedirLaRutaDeErroresDirectamenteSigueExigiendoSesion() throws Exception {
		assertThat(pedir("/error", null).statusCode()).isEqualTo(401);
	}
}
