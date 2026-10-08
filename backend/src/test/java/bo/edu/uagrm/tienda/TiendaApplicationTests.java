package bo.edu.uagrm.tienda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de humo: verifica que el contexto de Spring levanta.
 * Corre con el perfil {@code test}, que apunta a H2 en memoria, para no
 * depender de que el MySQL local este disponible.
 */
@SpringBootTest
@ActiveProfiles("test")
class TiendaApplicationTests {

	@Test
	void contextLoads() {
	}

}
