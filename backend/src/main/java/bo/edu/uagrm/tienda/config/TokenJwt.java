package bo.edu.uagrm.tienda.config;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import bo.edu.uagrm.tienda.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

public class TokenJwt {

	private static final String HUELLA = "hc";
	private static final String VERSION_SESION = "vs";
	private static final int BYTES_HUELLA = 16;

	private final SecretKey clave;
	private final SecretKeySpec claveHuella;
	private final Duration duracion;
	private final Clock clock;
	private final JwtParser lector;

	public TokenJwt(JwtProperties propiedades, Clock clock) {
		byte[] bytesClave = decodificar(propiedades.secreto());
		try {
			this.clave = Keys.hmacShaKeyFor(bytesClave);
		} catch (WeakKeyException e) {
			throw new IllegalStateException(
					"TIENDA_JWT_SECRETO es demasiado corta: tiene que ser una clave en base64 de al menos 256 bits.");
		}
		this.claveHuella = new SecretKeySpec(bytesClave, "HmacSHA256");
		this.duracion = Objects.requireNonNull(propiedades.duracion(), "Falta configurar tienda.jwt.duracion");
		this.clock = clock;
		this.lector = Jwts.parser().verifyWith(clave).clock(() -> Date.from(clock.instant())).build();
	}

	public TokenEmitido emitir(Usuario usuario) {
		Instant emitido = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant expiracion = emitido.plus(duracion);
		String token = Jwts.builder()
				.subject(Objects.requireNonNull(usuario.getIdUsuario()).toString())
				.issuedAt(Date.from(emitido))
				.expiration(Date.from(expiracion))
				.claim(HUELLA, huella(usuario.getContrasena()))
				.claim(VERSION_SESION, usuario.getVersionSesion())
				.signWith(clave, Jwts.SIG.HS256)
				.compact();
		return new TokenEmitido(token, expiracion);
	}

	public Optional<TokenLeido> leer(String token) {
		try {
			Claims claims = lector.parseSignedClaims(token).getPayload();
			String huella = claims.get(HUELLA, String.class);
			// Un token sin versión de sesión es de antes de que existiera: no se acepta
			Integer version = claims.get(VERSION_SESION, Integer.class);
			if (huella == null || version == null) {
				return Optional.empty();
			}
			return Optional.of(new TokenLeido(Long.valueOf(claims.getSubject()), huella, version));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	// El token deja de corresponder a la cuenta si su contraseña cambió o la cuenta se desactivó después de emitirlo
	public boolean correspondeA(TokenLeido leido, Usuario usuario) {
		return leido.versionSesion() == usuario.getVersionSesion() && MessageDigest.isEqual(leido.huellaContrasena().getBytes(StandardCharsets.US_ASCII),
				huella(usuario.getContrasena()).getBytes(StandardCharsets.US_ASCII));
	}

	// HMAC del hash con la clave del token: identifica la contraseña vigente sin permitir reconstruir el hash
	private String huella(String hashContrasena) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(claveHuella);
			byte[] firma = mac.doFinal(hashContrasena.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(firma, BYTES_HUELLA));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 no está disponible", e);
		}
	}

	private static byte[] decodificar(String secreto) {
		if (secreto == null || secreto.isBlank()) {
			throw new IllegalStateException(
					"Falta configurar TIENDA_JWT_SECRETO: una clave en base64 de al menos 256 bits.");
		}
		try {
			return Decoders.BASE64.decode(secreto);
		} catch (DecodingException e) {
			// Sin la variable, Spring deja "${TIENDA_JWT_SECRETO}" sin resolver, que tampoco es base64.
			// No se encadena la causa: su mensaje incluye un carácter de la clave.
			throw new IllegalStateException(
					"TIENDA_JWT_SECRETO no está configurada o no es una clave en base64 válida.");
		}
	}
}
