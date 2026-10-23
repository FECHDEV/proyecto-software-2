package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

// HU-15: el modelo se reconoce por su contenido (decisiones.md → Visor 3D y modelos)
public class FormatoModeloTest {

	private static final int JSON = 0x4E4F534A;
	private static final int BIN = 0x004E4942;

	// Un glTF binario versión 2 de tamano bytes: cabecera, un bloque JSON y, si sobra lugar, uno binario
	public static byte[] glb(int tamano) {
		return glb(tamano, 2, JSON);
	}

	private static byte[] glb(int tamano, int version, int tipoDelPrimerBloque) {
		byte[] json = "{\"asset\":{\"version\":\"2.0\"}} ".getBytes(StandardCharsets.US_ASCII);
		ByteBuffer buffer = ByteBuffer.allocate(tamano).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put("glTF".getBytes(StandardCharsets.US_ASCII)).putInt(version).putInt(tamano);
		buffer.putInt(json.length).putInt(tipoDelPrimerBloque).put(json);
		int resto = tamano - buffer.position() - 8;
		if (resto >= 0) {
			buffer.putInt(resto).putInt(BIN);
		}
		return buffer.array();
	}

	// RF-3
	@Test
	void aceptaUnGlbVersion2() {
		assertThat(FormatoModelo.esGlb(glb(1024))).isTrue();
		assertThat(FormatoModelo.esGlb(glb(48))).isTrue();
	}

	// RF-3: un .gltf de texto empieza con «{»
	@Test
	void rechazaGltfDeTexto() {
		assertThat(FormatoModelo.esGlb("{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8)))
				.isFalse();
	}

	// RF-3
	@Test
	void rechazaVersion1() {
		assertThat(FormatoModelo.esGlb(glb(1024, 1, JSON))).isFalse();
	}

	// RF-3: vacío, solo la cabecera, o cortado a la mitad
	@Test
	void rechazaVacioOCortado() {
		assertThat(FormatoModelo.esGlb(new byte[0])).isFalse();
		assertThat(FormatoModelo.esGlb(Arrays.copyOf(glb(1024), 12))).isFalse();
		assertThat(FormatoModelo.esGlb(Arrays.copyOf(glb(1024), 512))).isFalse();
	}

	// RF-3: una foto con la extensión cambiada
	@Test
	void rechazaUnaFotoRenombrada() {
		assertThat(FormatoModelo.esGlb(FormatoImagenTest.PNG)).isFalse();
		assertThat(FormatoModelo.esGlb(FormatoImagenTest.JPEG)).isFalse();
	}

	// RF-3: el largo de la cabecera no es el del archivo, o el primer bloque no es JSON
	@Test
	void rechazaLargoDeclaradoDistinto() {
		byte[] conDeMas = Arrays.copyOf(glb(1024), 1030);
		assertThat(FormatoModelo.esGlb(conDeMas)).isFalse();
		assertThat(FormatoModelo.esGlb(glb(1024, 2, BIN))).isFalse();
	}
}
