package bo.edu.uagrm.tienda.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;

import bo.edu.uagrm.tienda.exception.ErrorNegocioException;
import bo.edu.uagrm.tienda.exception.ImagenConDemasiadosPixelesException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;

// Las versiones de 400 y 1200 px de ancho de una foto, en JPEG (HU-22; decisiones.md → Fotos achicadas). Lee lo que
// lee ImageIO más los lectores de TwelveMonkeys: JPEG (también en CMYK), PNG y WebP
@Component
public class Achicador {

	public static final int[] ANCHOS = { 400, 1200 };
	public static final int MAXIMO_LADO = 8000;
	public static final long MAXIMO_PIXELES = 40_000_000L;
	private static final float CALIDAD = 0.82f;

	public record Versiones(byte[] de400, byte[] de1200) {
	}

	public Versiones achicar(byte[] contenido) {
		try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
			Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
			if (!lectores.hasNext()) {
				throw new ImagenInvalidaException();
			}
			ImageReader lector = lectores.next();
			try {
				lector.setInput(entrada, true, true);
				int ancho = lector.getWidth(0);
				int alto = lector.getHeight(0);
				// Antes de decodificar: lo que pesa en memoria son los píxeles, no el archivo (RF-5)
				if (ancho > MAXIMO_LADO || alto > MAXIMO_LADO || (long) ancho * alto > MAXIMO_PIXELES) {
					throw new ImagenConDemasiadosPixelesException();
				}
				ImageReadParam parametros = lector.getDefaultReadParam();
				// Se lee ya reducida, a no menos del doble de la versión más grande: menos memoria, y el achique
				// en pasos conserva la nitidez
				int salto = Math.max(1, ancho / (2 * ANCHOS[1]));
				parametros.setSourceSubsampling(salto, salto, 0, 0);
				BufferedImage derecha = enderezar(sobreBlanco(lector.read(0, parametros)), orientacion(contenido));
				return new Versiones(jpeg(reducir(derecha, ANCHOS[0])), jpeg(reducir(derecha, ANCHOS[1])));
			} finally {
				lector.dispose();
			}
		} catch (ErrorNegocioException rechazo) {
			throw rechazo;
		} catch (IOException | RuntimeException danada) {
			// Empieza como JPG, PNG o WebP pero no se puede abrir: el navegador tampoco la mostraría (RF-6)
			throw new ImagenInvalidaException();
		}
	}

	// Lo transparente queda del blanco de fondo de las fotos de la tienda (RF-4), y JPEG no guarda transparencia
	private static BufferedImage sobreBlanco(BufferedImage leida) {
		BufferedImage rgb = new BufferedImage(leida.getWidth(), leida.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D lapiz = rgb.createGraphics();
		lapiz.setColor(Color.WHITE);
		lapiz.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
		lapiz.drawImage(leida, 0, 0, null);
		lapiz.dispose();
		return rgb;
	}

	// El valor 1 a 8 de la orientación EXIF de un JPEG; sin EXIF, o si no se entiende, 1 (como está)
	private static int orientacion(byte[] contenido) {
		if (FormatoImagen.detectar(contenido).orElse(null) != FormatoImagen.JPEG) {
			return 1;
		}
		try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
			List<JPEGSegment> exif = JPEGSegmentUtil.readSegments(entrada, JPEG.APP1, "Exif");
			if (exif.isEmpty()) {
				return 1;
			}
			InputStream datos = exif.getFirst().data();
			// El identificador «Exif» va seguido de dos ceros; data() ya saltó el primero
			datos.read();
			try (ImageInputStream tiff = ImageIO.createImageInputStream(datos)) {
				Directory directorio = new TIFFReader().read(tiff);
				Entry entrada0 = directorio.getEntryById(TIFF.TAG_ORIENTATION);
				return Optional.ofNullable(entrada0).map(Entry::getValue).filter(Number.class::isInstance)
						.map(valor -> ((Number) valor).intValue()).filter(valor -> valor >= 1 && valor <= 8).orElse(1);
			}
		} catch (IOException | RuntimeException sinOrientacion) {
			return 1;
		}
	}

	// Como la muestra el navegador (RF-3): 2 a 4 espejan o giran 180°, 5 a 8 además cambian ancho por alto
	private static BufferedImage enderezar(BufferedImage imagen, int orientacion) {
		int w = imagen.getWidth();
		int h = imagen.getHeight();
		AffineTransform giro = switch (orientacion) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
			case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
			case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
			case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
			case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
			default -> null;
		};
		if (giro == null) {
			return imagen;
		}
		boolean cambiaLados = orientacion >= 5;
		BufferedImage derecha = new BufferedImage(cambiaLados ? h : w, cambiaLados ? w : h, BufferedImage.TYPE_INT_RGB);
		Graphics2D lapiz = derecha.createGraphics();
		lapiz.drawImage(imagen, giro, null);
		lapiz.dispose();
		return derecha;
	}

	// A la mitad en pasos bilineales hasta el ancho pedido: nítido sin el costo de SCALE_AREA_AVERAGING. Nunca agranda
	// (RF-2)
	private static BufferedImage reducir(BufferedImage imagen, int anchoPedido) {
		int anchoFinal = Math.min(anchoPedido, imagen.getWidth());
		int altoFinal = Math.max(1, Math.round((float) imagen.getHeight() * anchoFinal / imagen.getWidth()));
		BufferedImage actual = imagen;
		while (actual.getWidth() / 2 >= anchoFinal) {
			actual = escalar(actual, actual.getWidth() / 2, Math.max(altoFinal, actual.getHeight() / 2));
		}
		return actual.getWidth() == anchoFinal && actual.getHeight() == altoFinal ? actual
				: escalar(actual, anchoFinal, altoFinal);
	}

	private static BufferedImage escalar(BufferedImage imagen, int ancho, int alto) {
		BufferedImage escalada = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D lapiz = escalada.createGraphics();
		lapiz.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		lapiz.drawImage(imagen, 0, 0, ancho, alto, null);
		lapiz.dispose();
		return escalada;
	}

	// Sin metadatos: la versión no lleva el EXIF ni la ubicación de la foto original
	private static byte[] jpeg(BufferedImage imagen) throws IOException {
		ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream salida = ImageIO.createImageOutputStream(bytes)) {
			escritor.setOutput(salida);
			ImageWriteParam parametros = escritor.getDefaultWriteParam();
			parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parametros.setCompressionQuality(CALIDAD);
			escritor.write(null, new IIOImage(imagen, null, null), parametros);
		} finally {
			escritor.dispose();
		}
		return bytes.toByteArray();
	}
}
