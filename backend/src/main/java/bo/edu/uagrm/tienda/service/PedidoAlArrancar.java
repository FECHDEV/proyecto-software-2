package bo.edu.uagrm.tienda.service;

import java.util.List;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Sin migraciones, una base de antes de HU-17 tiene pedido.estado como enum de MySQL, que no admite ENTREGADO: se pasa a
// varchar una sola vez (decisiones.md → Base de datos: sin migraciones). En H2 y en una base nueva no encuentra un enum
// y no hace nada. Corre con los beans ya creados (el esquema de Hibernate ya está al día) y antes de que arranquen el
// servidor web y las tareas programadas: ninguna entrega llega con la columna vieja, y si el cambio falla, la API no
// arranca
@Slf4j
@Component
@RequiredArgsConstructor
public class EstadoPedidoAlArrancar implements SmartInitializingSingleton {

	private final JdbcTemplate jdbc;

	@Override
	public void afterSingletonsInstantiated() {
		ajustar();
	}

	public void ajustar() {
		List<String> tipos = jdbc.queryForList("""
				select data_type from information_schema.columns
				where table_schema = database() and table_name = 'pedido' and column_name = 'estado'""", String.class);
		if (tipos.stream().anyMatch("enum"::equalsIgnoreCase)) {
			jdbc.execute("alter table pedido modify estado varchar(15) not null");
			log.info("La columna pedido.estado pasó de enum a varchar(15)");
		}
	}
}
