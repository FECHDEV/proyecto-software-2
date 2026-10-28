package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import bo.edu.uagrm.tienda.exception.ImagenConDemasiadosPixelesException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;

// HU-22: las versiones de 400 y 1200 px de una foto
class AchicadorTest {

	private final Achicador achicador = new Achicador();

	// RF-1: la misma proporción en las dos versiones, en JPEG
	@Test
	void achicaA400Y1200ConLaMismaProporcion() throws IOException {
		Achicador.Versiones versiones = achicador.achicar(png(mitades(1600, 1000)));

		assertThat(medidas(versiones.de400())).containsExactly(400, 250);
		assertThat(medidas(versiones.de1200())).containsExactly(1200, 750);
		assertThat(Arrays.copyOf(versiones.de400(), 3)).containsExactly(0xFF, 0xD8, 0xFF);
	}

	// RF-2
	@Test
	void unaFotoAngostaNoSeAgranda() throws IOException {
		Achicador.Versiones versiones = achicador.achicar(png(mitades(350, 350)));

		assertThat(medidas(versiones.de400())).containsExactly(350, 350);
		assertThat(medidas(versiones.de1200())).containsExactly(350, 350);
	}

	// RF-3: orientación 6 (girar 90° a la derecha): la mitad izquierda, roja, queda arriba
	@Test
	void respetaLaOrientacionExif() throws IOException {
		byte[] conExif = conOrientacion(jpeg(mitades(300, 200)), 6);

		BufferedImage version = ImageIO.read(new ByteArrayInputStream(achicador.achicar(conExif).de400()));

		assertThat(new int[] { version.getWidth(), version.getHeight() }).containsExactly(200, 300);
		assertThat(esRojo(version.getRGB(100, 20))).isTrue();
		assertThat(esRojo(version.getRGB(100, 280))).isFalse();
	}

	// RF-4
	@Test
	void loTransparenteQuedaBlanco() throws IOException {
		BufferedImage transparente = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

		BufferedImage version = ImageIO.read(new ByteArrayInputStream(achicador.achicar(png(transparente)).de400()));

		Color color = new Color(version.getRGB(50, 50));
		assertThat(color.getRed()).isGreaterThan(245);
		assertThat(color.getGreen()).isGreaterThan(245);
		assertThat(color.getBlue()).isGreaterThan(245);
	}

	// RF-5: un PNG de menos de 100 bytes que declara 20 000 × 20 000; abierto ocuparía 1,6 GB
	@Test
	void rechazaMasDe8000PxSinDecodificar() {
		assertThatThrownBy(() -> achicador.achicar(soloEncabezadoPng(20_000, 20_000)))
				.isInstanceOf(ImagenConDemasiadosPixelesException.class);
		assertThatThrownBy(() -> achicador.achicar(soloEncabezadoPng(8001, 10)))
				.isInstanceOf(ImagenConDemasiadosPixelesException.class);
	}

	// RF-5: los lados entran, pero 7000 × 6000 son 42 megapíxeles
	@Test
	void rechazaMasDe40Megapixeles() {
		assertThatThrownBy(() -> achicador.achicar(soloEncabezadoPng(7000, 6000)))
				.isInstanceOf(ImagenConDemasiadosPixelesException.class);
	}

	// RF-6: empieza como JPEG, pero se corta antes de las medidas
	@Test
	void rechazaUnJpegCortado() throws IOException {
		byte[] cortado = Arrays.copyOf(jpeg(mitades(300, 200)), 30);

		assertThatThrownBy(() -> achicador.achicar(cortado)).isInstanceOf(ImagenInvalidaException.class);
		assertThatThrownBy(() -> achicador.achicar("no es una foto".getBytes()))
				.isInstanceOf(ImagenInvalidaException.class);
	}

	// RF-1 (decisión: las WebP también se achican). 600 × 400, roja a la izquierda
	@Test
	void leeWebP() throws IOException {
		Achicador.Versiones versiones = achicador.achicar(recurso("simple.webp"));

		assertThat(medidas(versiones.de400())).containsExactly(400, 267);
		assertThat(medidas(versiones.de1200())).containsExactly(600, 400);
		BufferedImage version = ImageIO.read(new ByteArrayInputStream(versiones.de400()));
		assertThat(esRojo(version.getRGB(20, 130))).isTrue();
	}

	// Caso límite: el primer cuadro es verde y el segundo negro
	@Test
	void unaWebPAnimadaUsaElPrimerCuadro() throws IOException {
		BufferedImage version = ImageIO.read(new ByteArrayInputStream(achicador.achicar(recurso("animada.webp")).de400()));

		Color color = new Color(version.getRGB(150, 100));
		assertThat(color.getGreen()).isGreaterThan(150);
		assertThat(color.getRed()).isLessThan(80);
	}

	// Caso límite: un JPEG en CMYK amarillo puro no sale con los colores invertidos (azul)
	@Test
	void leeUnJpegCmyk() throws IOException {
		BufferedImage version = ImageIO.read(new ByteArrayInputStream(achicador.achicar(recurso("cmyk.jpg")).de400()));

		Color color = new Color(version.getRGB(150, 100));
		assertThat(color.getRed()).isGreaterThan(200);
		assertThat(color.getGreen()).isGreaterThan(200);
		assertThat(color.getBlue()).isLessThan(80);
	}

	// Roja la mitad izquierda y azul la derecha: así se ve si la foto se giró
	private static BufferedImage mitades(int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D lapiz = imagen.createGraphics();
		lapiz.setColor(Color.RED);
		lapiz.fillRect(0, 0, ancho / 2, alto);
		lapiz.setColor(Color.BLUE);
		lapiz.fillRect(ancho / 2, 0, ancho - ancho / 2, alto);
		lapiz.dispose();
		return imagen;
	}

	private static boolean esRojo(int rgb) {
		Color color = new Color(rgb);
		return color.getRed() > 180 && color.getBlue() < 80;
	}

	private static byte[] png(BufferedImage imagen) throws IOException {
		return escribir(imagen, "png");
	}

	private static byte[] jpeg(BufferedImage imagen) throws IOException {
		return escribir(imagen, "jpg");
	}

	private static byte[] escribir(BufferedImage imagen, String formato) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(imagen, formato, salida);
		return salida.toByteArray();
	}

	private static int[] medidas(byte[] jpeg) throws IOException {
		BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(jpeg));
		return new int[] { imagen.getWidth(), imagen.getHeight() };
	}

	private static byte[] recurso(String nombre) throws IOException {
		try (InputStream entrada = AchicadorTest.class.getResourceAsStream("/fotos/" + nombre)) {
			return entrada.readAllBytes();
		}
	}

	// La firma PNG y el bloque IHDR con sus medidas, sin píxeles
	private static byte[] soloEncabezadoPng(int ancho, int alto) {
		ByteBuffer ihdr = ByteBuffer.allocate(17);
		ihdr.put(new byte[] { 'I', 'H', 'D', 'R' }).putInt(ancho).putInt(alto).put(new byte[] { 8, 2, 0, 0, 0 });
		CRC32 crc = new CRC32();
		crc.update(ihdr.array());
		return ByteBuffer.allocate(8 + 4 + 17 + 4)
				.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }).putInt(13)
				.put(ihdr.array()).putInt((int) crc.getValue()).array();
	}

	// Agrega después del SOI un bloque APP1 de EXIF con solo la orientación (TIFF little-endian, una entrada)
	private static byte[] conOrientacion(byte[] jpeg, int orientacion) {
		byte[] app1 = { (byte) 0xFF, (byte) 0xE1, 0, 34, 'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 0x2A, 0, 8, 0, 0, 0,
				1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientacion, 0, 0, 0, 0, 0, 0, 0 };
		byte[] resultado = new byte[jpeg.length + app1.length];
		System.arraycopy(jpeg, 0, resultado, 0, 2);
		System.arraycopy(app1, 0, resultado, 2, app1.length);
		System.arraycopy(jpeg, 2, resultado, 2 + app1.length, jpeg.length - 2);
		return resultado;
	}
}
