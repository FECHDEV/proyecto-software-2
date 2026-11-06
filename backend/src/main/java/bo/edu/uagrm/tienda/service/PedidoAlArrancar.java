package bo.edu.uagrm.tienda.service;

import java.util.List;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Sin migraciones (decisiones.md → Base de datos: sin migraciones), una base MySQL de antes tiene la tabla pedido como
// la dejó Hibernate entonces, y ddl-auto=update agrega columnas pero no cambia las que ya existen:
// - antes de HU-17, pedido.estado era un enum de MySQL, que no admite ENTREGADO: pasa a varchar;
// - después de HU-17, una base nueva tiene un check con los estados de entonces, que no admite ANULADO (HU-23): se
//   quita;
// - antes de HU-23, el cliente y los datos de retiro eran obligatorios, y una venta del mostrador puede no tenerlos;
// - los pedidos de antes de HU-23 no tienen canal: son todos en línea.
// En H2 y en una base nueva los pasos 1 a 3 no encuentran nada. Corre con los beans ya creados (el esquema de Hibernate
// ya está al día) y antes de que arranquen el servidor web y las tareas programadas; si un cambio falla, la API no
// arranca
@Slf4j
@Component
@RequiredArgsConstructor
public class PedidoAlArrancar implements SmartInitializingSingleton {

	private static final List<String> OPCIONALES_DESDE_HU_23 = List.of("id_usuario", "nombre_retira",
			"telefono_retira");

	private final JdbcTemplate jdbc;

	@Override
	public void afterSingletonsInstantiated() {
		ajustar();
	}

	public void ajustar() {
		if (columna("estado", "data_type").stream().anyMatch("enum"::equalsIgnoreCase)) {
			jdbc.execute("alter table pedido modify estado varchar(15) not null");
			log.info("La columna pedido.estado pasó de enum a varchar(15)");
		}
		// El nombre del check sale de information_schema, nunca de afuera
		for (String check : jdbc.queryForList("""
				select tc.constraint_name from information_schema.table_constraints tc
				join information_schema.check_constraints cc
				  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
				where tc.table_schema = database() and tc.table_name = 'pedido' and tc.constraint_type = 'CHECK'
				  and cc.check_clause like '%PENDIENTE_PAGO%' and cc.check_clause not like '%ANULADO%'""",
				String.class)) {
			jdbc.execute("alter table pedido drop check `" + check.replace("`", "``") + "`");
			log.info("Se quitó el check {} de pedido, que no admitía ANULADO", check);
		}
		for (String nombre : OPCIONALES_DESDE_HU_23) {
			if (columna(nombre, "is_nullable").contains("NO")) {
				String tipo = columna(nombre, "column_type").getFirst();
				jdbc.execute("alter table pedido modify " + nombre + " " + tipo + " null");
				log.info("La columna pedido.{} pasó a admitir nulos", nombre);
			}
		}
		int sinCanal = jdbc.update("update pedido set canal = 'EN_LINEA' where canal is null");
		if (sinCanal > 0) {
			log.info("{} pedidos de antes quedaron con canal EN_LINEA", sinCanal);
		}
	}

	// Un dato de una columna de pedido en MySQL; en H2 no encuentra la tabla en minúsculas y devuelve vacío. Los
	// nombres son constantes de esta clase
	private List<String> columna(String nombre, String dato) {
		return jdbc.queryForList("select " + dato + """
				 from information_schema.columns
				where table_schema = database() and table_name = 'pedido' and column_name = ?""", String.class, nombre);
	}
}
