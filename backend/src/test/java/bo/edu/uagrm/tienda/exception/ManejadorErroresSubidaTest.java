package bo.edu.uagrm.tienda.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// HU-15 RF-4: multipart corta la subida antes del controlador; MockMvc no pasa por ese corte, así que se prueba el
// manejador directamente
class ManejadorErroresSubidaTest {

	private final ManejadorErrores manejador = new ManejadorErrores();

	private String codigo(String uri) throws Exception {
		ResponseEntity<Object> respuesta = manejador.handleException(new MaxUploadSizeExceededException(3_145_728),
				new ServletWebRequest(new MockHttpServletRequest("PUT", uri)));
		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		return (String) ((ProblemDetail) respuesta.getBody()).getProperties().get("codigo");
	}

	// HU-15 RF-4
	@Test
	void unModeloDeMasDe3MbEsModeloDemasiadoGrande() throws Exception {
		assertThat(codigo("/api/gestion/productos/7/modelo")).isEqualTo("MODELO_DEMASIADO_GRANDE");
	}

	// Las fotos siguen con su código (HU-06, HU-07)
	@Test
	void unaFotoSigueSiendoImagenDemasiadoGrande() throws Exception {
		assertThat(codigo("/api/gestion/productos/7/imagenes")).isEqualTo("IMAGEN_DEMASIADO_GRANDE");
		assertThat(codigo("/api/gestion/categorias/3/imagen")).isEqualTo("IMAGEN_DEMASIADO_GRANDE");
	}
}
