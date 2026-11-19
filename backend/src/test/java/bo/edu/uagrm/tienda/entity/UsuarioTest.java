package bo.edu.uagrm.tienda.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// HU-05 RF-5, RF-6: la versión de sesión invalida los tokens anteriores a una desactivación
class UsuarioTest {

	private static Usuario ana() {
		return Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null,
				LocalDateTime.of(2026, 10, 10, 9, 0));
	}

	// HU-05 RF-5, RF-6
	@Test
	void desactivarIncrementaLaVersionDeSesionYReactivarNoLaVuelveAtras() {
		Usuario ana = ana();
		int inicial = ana.getVersionSesion();

		ana.desactivar();
		ana.activar();

		assertThat(ana.getVersionSesion()).isEqualTo(inicial + 1);
	}

	// HU-05 RF-5
	@Test
	void desactivarUnaCuentaYaDesactivadaNoCambiaLaVersion() {
		Usuario ana = ana();
		ana.desactivar();
		int desactivada = ana.getVersionSesion();

		ana.desactivar();

		assertThat(ana.getVersionSesion()).isEqualTo(desactivada);
	}

	// HU-23 RF-25: el nombre y el apellido sin tildes, en minúsculas y con un solo espacio, para buscar al comprador
	@Test
	void laBusquedaVaSinTildesNiMayusculasConUnSoloEspacio() {
		Usuario jose = Usuario.registrarCliente("  José  María", "Peña ", "jose@mail.com", "$2a$10$hash", null,
				LocalDateTime.of(2026, 10, 10, 9, 0));
		Usuario admin = Usuario.crearAdministrador("Álvaro", "Núñez", "admin@mail.com", "$2a$10$hash",
				LocalDateTime.of(2026, 10, 10, 9, 0));

		assertThat(jose.getBusqueda()).isEqualTo("jose maria pena");
		assertThat(admin.getBusqueda()).isEqualTo("alvaro nunez");
	}

	// /code-review: quitar las tildes puede alargar el texto; nunca pasa el largo de la columna
	@Test
	void laBusquedaNoPasaElLargoDeLaColumna() {
		Usuario largo = Usuario.registrarCliente("한".repeat(80), "İ".repeat(80), "largo@mail.com", "$2a$10$hash", null,
				LocalDateTime.of(2026, 10, 10, 9, 0));

		assertThat(largo.getBusqueda()).hasSizeLessThanOrEqualTo(161);
	}

	// HU-23 RF-25
	@Test
	void actualizarDatosRehaceLaBusqueda() {
		Usuario ana = ana();

		ana.actualizarDatos("Anabel", "Suárez", null);

		assertThat(ana.getBusqueda()).isEqualTo("anabel suarez");
	}
}
