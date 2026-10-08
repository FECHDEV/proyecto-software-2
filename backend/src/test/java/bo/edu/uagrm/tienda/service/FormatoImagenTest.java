package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FormatoImagenTest {

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };
	static final byte[] WEBP = { 'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };

	// RF-22 y RF-23: se reconoce por los primeros bytes, no por la extensión
	@Test
	void reconoceLosTresFormatosPorSuContenido() {
		assertThat(FormatoImagen.detectar(PNG)).contains(FormatoImagen.PNG);
		assertThat(FormatoImagen.detectar(JPEG)).contains(FormatoImagen.JPEG);
		assertThat(FormatoImagen.detectar(WEBP)).contains(FormatoImagen.WEBP);
	}

	@Test
	void rechazaLoQueNoEsImagen() {
		assertThat(FormatoImagen.detectar("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8)))
				.isEmpty();
		assertThat(FormatoImagen.detectar("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(FormatoImagen.detectar(new byte[0])).isEmpty();
		// RIFF que no es WebP (un WAV)
		assertThat(FormatoImagen.detectar(new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E' }))
				.isEmpty();
	}

	@Test
	void conoceSuTipoDeContenidoYSuExtension() {
		assertThat(FormatoImagen.JPEG.tipoDeContenido()).isEqualTo("image/jpeg");
		assertThat(FormatoImagen.PNG.extension()).isEqualTo("png");
		assertThat(FormatoImagen.WEBP.tipoDeContenido()).isEqualTo("image/webp");
	}

	@Test
	void recuperaElFormatoDeUnArchivoGuardado() {
		assertThat(FormatoImagen.deArchivo("5f2c-a.jpg")).isEqualTo(FormatoImagen.JPEG);
		assertThat(FormatoImagen.deArchivo("5f2c-a.webp")).isEqualTo(FormatoImagen.WEBP);
		assertThatThrownBy(() -> FormatoImagen.deArchivo("5f2c-a.gif")).isInstanceOf(IllegalArgumentException.class);
	}
}
