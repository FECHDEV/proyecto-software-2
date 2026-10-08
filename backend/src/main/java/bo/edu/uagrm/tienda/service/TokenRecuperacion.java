package bo.edu.uagrm.tienda.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// El token viaja solo en el correo; la base guarda su SHA-256 (decisiones.md → Recuperación de contraseña)
public final class TokenRecuperacion {

	private static final int BYTES_ALEATORIOS = 32;
	private static final SecureRandom ALEATORIO = new SecureRandom();

	private TokenRecuperacion() {
	}

	public static String generar() {
		byte[] bytes = new byte[BYTES_ALEATORIOS];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String token) {
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumen);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no ofrece SHA-256", e);
		}
	}
}
