package bo.edu.uagrm.tienda.service;

import java.util.Arrays;
import java.util.Optional;

// Formatos aceptados para una imagen subida (decisiones.md → Imágenes subidas). Se reconocen por sus primeros
// bytes: la extensión y el tipo que declara el navegador los elige quien sube el archivo
public enum FormatoImagen {

	JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

	private static final byte[] FIRMA_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
	private static final byte[] FIRMA_RIFF = { 'R', 'I', 'F', 'F' };
	private static final byte[] FIRMA_WEBP = { 'W', 'E', 'B', 'P' };

	private final String tipoDeContenido;
	private final String extension;

	FormatoImagen(String tipoDeContenido, String extension) {
		this.tipoDeContenido = tipoDeContenido;
		this.extension = extension;
	}

	public String tipoDeContenido() {
		return tipoDeContenido;
	}

	public String extension() {
		return extension;
	}

	public static Optional<FormatoImagen> detectar(byte[] contenido) {
		if (empiezaCon(contenido, 0, FIRMA_JPEG)) {
			return Optional.of(JPEG);
		}
		if (empiezaCon(contenido, 0, FIRMA_PNG)) {
			return Optional.of(PNG);
		}
		// WebP: "RIFF", cuatro bytes de tamaño y "WEBP"
		if (empiezaCon(contenido, 0, FIRMA_RIFF) && empiezaCon(contenido, 8, FIRMA_WEBP)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	// Los archivos guardados llevan la extensión de su formato (AlmacenImagenes)
	public static FormatoImagen deArchivo(String nombre) {
		String extension = nombre.substring(nombre.lastIndexOf('.') + 1);
		return Arrays.stream(values()).filter(formato -> formato.extension.equals(extension)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Extensión de imagen desconocida: " + extension));
	}

	private static boolean empiezaCon(byte[] contenido, int desde, byte[] firma) {
		if (contenido.length < desde + firma.length) {
			return false;
		}
		return Arrays.equals(contenido, desde, desde + firma.length, firma, 0, firma.length);
	}
}
