package bo.edu.uagrm.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

class TokenJwtTest {

	private static final String CLAVE = "Y2xhdmUtZGUtcHJ1ZWJhcy1wYXJhLXRva2Vucy1qd3QtZXZlbnRvcw==";
	private static final String OTRA_CLAVE = "b3RyYS1jbGF2ZS1kZS1wcnVlYmFzLXBhcmEtZmlybWFyLXRva2Vucw==";
	private static final String CLAVE_CORTA = "Y2xhdmUtY29ydGEtZGUtMzEtYnl0ZXMtZXhhY3Rvcw==";
	private static final String HASH = "$2a$10$hashDeLaContrasenaActual";
	private static final Instant AHORA = Instant.parse("2026-09-14T15:00:00Z");
	private static final Duration OCHO_HORAS = Duration.ofHours(8);

	private static TokenJwt tokenJwt(String clave, Instant ahora) {
		return new TokenJwt(new JwtProperties(clave, OCHO_HORAS), Clock.fixed(ahora, ZoneId.of("America/La_Paz")));
	}

	private static Usuario clienteConId(long id, String hash) {
		Usuario usuario = Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", hash, null, LocalDateTime.of(2026, 9, 12, 11, 0));
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	private static Optional<Long> idLeido(TokenJwt tokenJwt, String token) {
		return tokenJwt.leer(token).map(TokenLeido::idUsuario);
	}

	// HU-02 RF-1
	@Test
	void tokenEmitidoIdentificaAlUsuarioYVenceEnOchoHoras() {
		Usuario ana = clienteConId(7, HASH);
		TokenEmitido emitido = tokenJwt(CLAVE, AHORA).emitir(ana);

		assertThat(emitido.expiracion()).isEqualTo(Instant.parse("2026-09-14T23:00:00Z"));
		assertThat(tokenJwt(CLAVE, AHORA).leer(emitido.token()))
				.hasValueSatisfying(leido -> {
					assertThat(leido.idUsuario()).isEqualTo(7L);
					assertThat(tokenJwt(CLAVE, AHORA).correspondeA(leido, ana)).isTrue();
				});
	}

	@Test
	void tokenSoloLlevaSujetoEmisionVencimientoYHuellaSinElHash() {
		String token = tokenJwt(CLAVE, AHORA).emitir(clienteConId(7, HASH)).token();

		Claims claims = Jwts.parser()
				.verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(CLAVE)))
				.clock(() -> Date.from(AHORA))
				.build().parseSignedClaims(token).getPayload();

		assertThat(claims).containsOnlyKeys("sub", "iat", "exp", "hc");
		assertThat(claims.getSubject()).isEqualTo("7");
		assertThat(claims.get("hc", String.class)).hasSize(22).doesNotContain("$2a", "hashDeLaContrasenaActual");
	}

	// HU-02 RF-9
	@Test
	void tokenEmitidoAntesDeUnCambioDeContrasenaNoCorrespondeALaCuenta() {
		TokenJwt emisor = tokenJwt(CLAVE, AHORA);
		String token = emisor.emitir(clienteConId(7, HASH)).token();
		Usuario conContrasenaNueva = clienteConId(7, "$2a$10$hashDeLaContrasenaNueva");

		assertThat(emisor.leer(token))
				.hasValueSatisfying(leido -> assertThat(emisor.correspondeA(leido, conContrasenaNueva)).isFalse());
	}

	// HU-02 RF-9
	@Test
	void tokenSinHuellaNoSeAcepta() {
		String sinHuella = Jwts.builder()
				.subject("7")
				.issuedAt(Date.from(AHORA))
				.expiration(Date.from(AHORA.plus(OCHO_HORAS)))
				.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(CLAVE)), Jwts.SIG.HS256)
				.compact();

		assertThat(tokenJwt(CLAVE, AHORA).leer(sinHuella)).isEmpty();
	}

	// HU-02 RF-9
	@Test
	void tokenVencidoNoIdentificaANadie() {
		String token = tokenJwt(CLAVE, AHORA).emitir(clienteConId(7, HASH)).token();

		assertThat(idLeido(tokenJwt(CLAVE, AHORA.plus(OCHO_HORAS).minusSeconds(1)), token)).contains(7L);
		assertThat(idLeido(tokenJwt(CLAVE, AHORA.plus(OCHO_HORAS).plusSeconds(1)), token)).isEmpty();
	}

	// HU-02 RF-9
	@Test
	void tokenFirmadoConOtraClaveNoIdentificaANadie() {
		String token = tokenJwt(OTRA_CLAVE, AHORA).emitir(clienteConId(7, HASH)).token();

		assertThat(tokenJwt(CLAVE, AHORA).leer(token)).isEmpty();
	}

	// HU-02 RF-9
	@Test
	void tokenAlteradoNoIdentificaANadie() {
		String[] partes = tokenJwt(CLAVE, AHORA).emitir(clienteConId(7, HASH)).token().split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		String payloadAlterado = payload.replace("\"sub\":\"7\"", "\"sub\":\"8\"");
		assertThat(payloadAlterado).isNotEqualTo(payload);
		String alterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payloadAlterado.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

		assertThat(tokenJwt(CLAVE, AHORA).leer(alterado)).isEmpty();
	}

	// HU-02 RF-9
	@Test
	void textoQueNoEsUnTokenNoIdentificaANadie() {
		assertThat(tokenJwt(CLAVE, AHORA).leer("basura")).isEmpty();
		assertThat(tokenJwt(CLAVE, AHORA).leer("")).isEmpty();
	}

	@Test
	void claveDeMenosDe256BitsSeRechazaIndicandoLaVariable() {
		assertThatThrownBy(() -> tokenJwt(CLAVE_CORTA, AHORA))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TIENDA_JWT_SECRETO")
				.hasMessageContaining("256 bits");
	}

	@Test
	void claveAusenteSeRechazaIndicandoLaVariable() {
		assertThatThrownBy(() -> tokenJwt(" ", AHORA))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TIENDA_JWT_SECRETO");
		assertThatThrownBy(() -> tokenJwt(null, AHORA)).isInstanceOf(IllegalStateException.class);
	}
}
