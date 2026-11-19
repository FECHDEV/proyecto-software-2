package bo.edu.uagrm.tienda.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// HU-23 RF-26: la búsqueda por nombre nunca devuelve el correo completo
class CompradorResponseTest {

	// HU-23 RF-26
	@Test
	void dejaDosCaracteresYElDominio() {
		assertThat(CompradorResponse.ocultar("fabio@gmail.com")).isEqualTo("fa***@gmail.com");
	}

	// HU-23 RF-26: con uno o dos caracteres antes de la «@», nunca la parte entera
	@Test
	void conDosOUnCaracterNoMuestraLaParteEntera() {
		assertThat(CompradorResponse.ocultar("ab@x.com")).isEqualTo("a***@x.com");
		assertThat(CompradorResponse.ocultar("a@x.com")).isEqualTo("***@x.com");
	}

	// HU-23 RF-26
	@Test
	void sinArrobaNoMuestraNada() {
		assertThat(CompradorResponse.ocultar("sinarroba")).isEqualTo("***");
		assertThat(CompradorResponse.ocultar("@x.com")).isEqualTo("***");
	}
}
