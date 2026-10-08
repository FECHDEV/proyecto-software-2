package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.RecuperacionContrasena;
import bo.edu.uagrm.tienda.entity.Usuario;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecuperacionContrasenaRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 15, 10, 0);
	private static final String HASH = "a".repeat(64);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private RecuperacionContrasenaRepository recuperacionRepository;

	private static Usuario cliente(String correo) {
		return Usuario.registrarCliente("Ana", "Rojas", correo, "$2a$10$hash", null, AHORA);
	}

	@Test
	void guardaElHashEnLaColumnaTokenConVigenciaDeUnaHora() {
		Usuario ana = em.persist(cliente("ana@mail.com"));
		Long id = em.persistAndGetId(RecuperacionContrasena.crear(ana, HASH, AHORA), Long.class);
		em.flush();
		em.clear();

		RecuperacionContrasena leida = recuperacionRepository.findById(id).orElseThrow();

		assertThat(leida.getUsuario().getIdUsuario()).isEqualTo(ana.getIdUsuario());
		assertThat(leida.getHashToken()).isEqualTo(HASH);
		assertThat(leida.getFechaCreacion()).isEqualTo(AHORA);
		assertThat(leida.getFechaExpiracion()).isEqualTo(AHORA.plusHours(1));
		assertThat(leida.isUsado()).isFalse();
		assertThat(em.getEntityManager()
				.createNativeQuery("SELECT token FROM recuperacion_contrasena WHERE id_recuperacion = :id")
				.setParameter("id", id).getSingleResult()).isEqualTo(HASH);
	}

	@Test
	void findByHashTokenEncuentraSoloElRegistroDeEseHash() {
		Usuario ana = em.persist(cliente("ana@mail.com"));
		em.persist(RecuperacionContrasena.crear(ana, HASH, AHORA));
		em.flush();
		em.clear();

		assertThat(recuperacionRepository.findByHashToken(HASH)).isPresent();
		assertThat(recuperacionRepository.findByHashToken("b".repeat(64))).isEmpty();
	}

	@Test
	void findByUsuarioAndUsadoFalseDevuelveSoloLosPendientesDeEseUsuario() {
		Usuario ana = em.persist(cliente("ana@mail.com"));
		Usuario beto = em.persist(cliente("beto@mail.com"));
		RecuperacionContrasena usada = RecuperacionContrasena.crear(ana, "b".repeat(64), AHORA);
		usada.marcarUsada();
		em.persist(usada);
		Long pendienteDeAna = em.persistAndGetId(RecuperacionContrasena.crear(ana, "c".repeat(64), AHORA), Long.class);
		em.persist(RecuperacionContrasena.crear(beto, "d".repeat(64), AHORA));
		em.flush();
		em.clear();

		assertThat(recuperacionRepository.findByUsuarioAndUsadoFalse(em.find(Usuario.class, ana.getIdUsuario())))
				.extracting(RecuperacionContrasena::getIdRecuperacion).containsExactly(pendienteDeAna);
	}

	@Test
	void hashRepetidoSeRechazaPorLaRestriccionUnica() {
		Usuario ana = em.persist(cliente("ana@mail.com"));
		recuperacionRepository.saveAndFlush(RecuperacionContrasena.crear(ana, HASH, AHORA));

		assertThatThrownBy(() -> recuperacionRepository.saveAndFlush(RecuperacionContrasena.crear(ana, HASH, AHORA)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void dejaDeEstarVigenteAlCumplirseLaHoraOAlUsarse() {
		RecuperacionContrasena recuperacion = RecuperacionContrasena.crear(cliente("ana@mail.com"), HASH, AHORA);

		assertThat(recuperacion.estaVigente(AHORA.plusMinutes(59).plusSeconds(59))).isTrue();
		assertThat(recuperacion.estaVigente(AHORA.plusHours(1))).isFalse();
		recuperacion.marcarUsada();
		assertThat(recuperacion.estaVigente(AHORA)).isFalse();
	}
}
