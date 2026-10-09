package bo.edu.uagrm.tienda;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Los límites por IP (registro, inicio de sesión, recuperación) viven en el contexto que comparten los tests de
// integración: cada petición que no prueba un límite usa una IP propia para no consumir el de otro test
public final class IpsDePrueba {

	private static final AtomicInteger SIGUIENTE = new AtomicInteger();

	private IpsDePrueba() {
	}

	public static RequestPostProcessor ipNueva() {
		int numero = SIGUIENTE.incrementAndGet();
		return peticion -> {
			peticion.setRemoteAddr("10.200." + (numero / 250) + "." + (numero % 250 + 1));
			return peticion;
		};
	}
}
