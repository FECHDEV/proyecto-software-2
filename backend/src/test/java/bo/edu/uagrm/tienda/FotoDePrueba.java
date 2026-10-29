package bo.edu.uagrm.tienda;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

// Fotos para las pruebas. Desde HU-22 una foto subida se abre para achicarla (RF-6): la firma de un PNG sola ya
// no alcanza, hace falta un PNG de verdad
public final class FotoDePrueba {

	// Un PNG de verdad de 4 × 4: lo mínimo que se puede abrir
	public static final byte[] PNG = png(4, 4);

	// Solo la firma de un PNG: empieza como imagen pero no se puede abrir
	public static final byte[] PNG_DANADO = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0 };

	private FotoDePrueba() {
	}

	public static byte[] png(int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D lapiz = imagen.createGraphics();
		lapiz.setColor(Color.ORANGE);
		lapiz.fillRect(0, 0, ancho, alto);
		lapiz.dispose();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try {
			ImageIO.write(imagen, "png", salida);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return salida.toByteArray();
	}

	// La firma de un PNG y el bloque IHDR con sus medidas, sin píxeles: pesa menos de 100 bytes y declara lo que sea
	public static byte[] soloEncabezadoPng(int ancho, int alto) {
		ByteBuffer ihdr = ByteBuffer.allocate(17);
		ihdr.put(new byte[] { 'I', 'H', 'D', 'R' }).putInt(ancho).putInt(alto).put(new byte[] { 8, 2, 0, 0, 0 });
		CRC32 crc = new CRC32();
		crc.update(ihdr.array());
		return ByteBuffer.allocate(8 + 4 + 17 + 4)
				.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }).putInt(13)
				.put(ihdr.array()).putInt((int) crc.getValue()).array();
	}
}
