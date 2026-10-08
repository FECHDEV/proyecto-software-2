package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class TokenRecuperacionTest {

	@Test
	void generaTokensDe256BitsEnBase64UrlSinRelleno() {
		String token = TokenRecuperacion.generar();

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
	}

	@Test
	void cadaTokenEsDistinto() {
		assertThat(Stream.generate(TokenRecuperacion::generar).limit(100).collect(Collectors.toSet())).hasSize(100);
	}

	@Test
	void hashEsElSha256EnHexadecimal() {
		assertThat(TokenRecuperacion.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void hashDeUnTokenEsEstableYNoContieneAlToken() {
		String token = TokenRecuperacion.generar();

		assertThat(TokenRecuperacion.hash(token)).hasSize(64).isNotEqualTo(token)
				.isEqualTo(TokenRecuperacion.hash(token));
	}
}
