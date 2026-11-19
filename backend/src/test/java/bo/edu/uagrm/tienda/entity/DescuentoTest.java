package bo.edu.uagrm.tienda.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

// HU-24 RF-1, RF-3, RF-5, RF-10: el descuento y su estado según el día de hoy
class DescuentoTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 18, 9, 0);
	private static final LocalDate INICIO = LocalDate.of(2026, 11, 20);
	private static final LocalDate FIN = LocalDate.of(2026, 11, 30);

	private final Categoria herramientas = Categoria.crear("Herramientas", null);
	private final Producto martillo = Producto.crear("Martillo", null, null, new BigDecimal("100.00"),
			UnidadVenta.UNIDAD, herramientas, List.of(), AHORA);
	private final Usuario admin = Usuario.crearAdministrador("Ada", "Admin", "ada@mail.com", "$2a$10$hash", AHORA);

	// HU-24 RF-3, RF-10: el día de inicio y el de fin son «Vigente»; el siguiente, «Vencido»
	@Test
	void estadoSegunHoy() {
		Descuento descuento = Descuento.deProducto(martillo, 15, INICIO, FIN, admin, AHORA);

		assertThat(descuento.estado(INICIO.minusDays(1))).isEqualTo(EstadoDescuento.PROGRAMADO);
		assertThat(descuento.estado(INICIO)).isEqualTo(EstadoDescuento.VIGENTE);
		assertThat(descuento.estado(FIN)).isEqualTo(EstadoDescuento.VIGENTE);
		assertThat(descuento.estado(FIN.plusDays(1))).isEqualTo(EstadoDescuento.VENCIDO);
	}

	// HU-24 RF-5: terminar deja el fin en ayer, así hoy ya no se aplica
	@Test
	void terminarDejaElFinEnAyer() {
		Descuento descuento = Descuento.deCategoria(herramientas, 20, INICIO, FIN, admin, AHORA);
		LocalDate hoy = INICIO.plusDays(3);

		descuento.terminar(hoy);

		assertThat(descuento.getFin()).isEqualTo(hoy.minusDays(1));
		assertThat(descuento.estado(hoy)).isEqualTo(EstadoDescuento.VENCIDO);
	}

	// HU-24 RF-1: un producto o una categoría, nunca ninguno
	@Test
	void exigeProductoOCategoria() {
		assertThatThrownBy(() -> Descuento.deProducto(null, 15, INICIO, FIN, admin, AHORA))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Descuento.deCategoria(null, 15, INICIO, FIN, admin, AHORA))
				.isInstanceOf(NullPointerException.class);

		Descuento deProducto = Descuento.deProducto(martillo, 15, INICIO, FIN, admin, AHORA);
		assertThat(deProducto.getProducto()).isSameAs(martillo);
		assertThat(deProducto.getCategoria()).isNull();
		Descuento deCategoria = Descuento.deCategoria(herramientas, 15, INICIO, FIN, admin, AHORA);
		assertThat(deCategoria.getCategoria()).isSameAs(herramientas);
		assertThat(deCategoria.getProducto()).isNull();
	}

	// HU-24 RF-4: editar cambia el porcentaje y las fechas, no a qué se aplica
	@Test
	void cambiarSoloElPorcentajeYLasFechas() {
		Descuento descuento = Descuento.deProducto(martillo, 15, INICIO, FIN, admin, AHORA);

		descuento.cambiar(25, INICIO.plusDays(1), FIN.plusDays(1));

		assertThat(descuento.getPorcentaje()).isEqualTo(25);
		assertThat(descuento.getInicio()).isEqualTo(INICIO.plusDays(1));
		assertThat(descuento.getFin()).isEqualTo(FIN.plusDays(1));
		assertThat(descuento.getProducto()).isSameAs(martillo);
	}
}
