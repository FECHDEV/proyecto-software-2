package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// HU-17 (técnico): el cambio de pedido.estado a varchar es solo para una base MySQL de antes (decisiones.md → Base de
// datos: sin migraciones). En H2 la columna ya nace varchar y la tarea no hace nada; el caso MySQL se prueba con Docker
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EstadoPedidoAlArrancar.class)
class EstadoPedidoAlArrancarTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EstadoPedidoAlArrancar estadoPedidoAlArrancar;

	@Test
	void enH2NoHaceNada() {
		String antes = tipoDeLaColumna();

		assertThatCode(estadoPedidoAlArrancar::ajustar).doesNotThrowAnyException();

		assertThat(antes).isEqualToIgnoringCase("CHARACTER VARYING");
		assertThat(tipoDeLaColumna()).isEqualTo(antes);
	}

	private String tipoDeLaColumna() {
		return jdbc.queryForObject("""
				select data_type from information_schema.columns
				where upper(table_name) = 'PEDIDO' and upper(column_name) = 'ESTADO'""", String.class);
	}
}
