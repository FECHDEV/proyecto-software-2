package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Usuario;

// HU-17 y HU-23 (técnico): lo que cambia en una base MySQL de antes, sin migraciones (decisiones.md → Base de datos:
// sin migraciones). En H2 las columnas ya nacen como hacen falta y no hay enum ni check viejo: solo se completa el
// canal de los pedidos de antes. El caso MySQL se prueba con Docker
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PedidoAlArrancar.class)
class PedidoAlArrancarTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 5, 10, 0);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private PedidoAlArrancar pedidoAlArrancar;

	@Test
	void sinEnumNiCheckNoHaceNada() {
		String antes = tipoDeLaColumna();

		assertThatCode(pedidoAlArrancar::ajustar).doesNotThrowAnyException();

		assertThat(antes).isEqualToIgnoringCase("CHARACTER VARYING");
		assertThat(tipoDeLaColumna()).isEqualTo(antes);
	}

	// HU-23: un pedido de antes de la venta del mostrador no tiene canal; al arrancar queda en línea
	@Test
	void losPedidosSinCanalQuedanEnLinea() {
		Usuario cliente = entityManager.persist(
				Usuario.registrarCliente("Ana", "Paz", "ana@mail.com", "$2a$10$hash", null, AHORA));
		Pedido pedido = entityManager.persist(
				Pedido.confirmar(cliente, "Ana Paz", "70000000", null, AHORA, AHORA.plusMinutes(30)));
		entityManager.flush();
		jdbc.update("update pedido set canal = null where id_pedido = ?", pedido.getIdPedido());

		pedidoAlArrancar.ajustar();

		assertThat(jdbc.queryForObject("select canal from pedido where id_pedido = ?", String.class,
				pedido.getIdPedido())).isEqualTo("EN_LINEA");
	}

	private String tipoDeLaColumna() {
		return jdbc.queryForObject("""
				select data_type from information_schema.columns
				where upper(table_name) = 'PEDIDO' and upper(column_name) = 'ESTADO'""", String.class);
	}
}
