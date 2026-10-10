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
}
