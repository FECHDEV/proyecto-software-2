package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.LineaPedido;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;

// HU-12: los pedidos, sus líneas copiadas y los movimientos de la reserva
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PedidoRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 9, 0);
	private static final LocalDateTime VENCE = AHORA.plusMinutes(30);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private PedidoRepository pedidoRepository;

	private Usuario ana;
	private Usuario beto;
	private Producto martillo;
	private Producto cable;

	@BeforeEach
	void preparar() {
		Categoria herramientas = em.persist(Categoria.crear("Herramientas HU-12", null));
		martillo = producto("Martillo", new BigDecimal("85.50"), UnidadVenta.UNIDAD, herramientas);
		cable = producto("Cable", new BigDecimal("12.00"), UnidadVenta.METRO, herramientas);
		ana = em.persist(Usuario.registrarCliente("Ana", "Paz", "ana.hu12@mail.com", "$2a$10$hash", null, AHORA));
		beto = em.persist(Usuario.registrarCliente("Beto", "Rojas", "beto.hu12@mail.com", "$2a$10$hash", null, AHORA));
		em.flush();
	}

	private Producto producto(String nombre, BigDecimal precio, UnidadVenta unidad, Categoria categoria) {
		Producto producto = em.persist(Producto.crear(nombre, null, null, precio, unidad, categoria, List.of(), AHORA));
		producto.asignarCodigo("HER-H12-" + producto.getIdProducto());
		return producto;
	}

	private Pedido pedido(Usuario cliente, LocalDateTime confirmado) {
		Pedido pedido = Pedido.confirmar(cliente, "Ana Paz", "70000000", null, confirmado, confirmado.plusMinutes(30));
		pedido.agregarLinea(martillo, new BigDecimal("2"));
		return em.persist(pedido);
	}

	// HU-12 RF-2: el pedido guarda una copia; un cambio de precio después no lo toca
	@Test
	void elPedidoGuardaLasLineasCopiadas() {
		Pedido pedido = Pedido.confirmar(ana, "  Carla Paz ", " 71234567 ", "  Paso a las 18  ", AHORA, VENCE);
		pedido.agregarLinea(martillo, new BigDecimal("2"));
		pedido.agregarLinea(cable, new BigDecimal("3.5"));
		Long id = em.persist(pedido).getIdPedido();
		em.flush();
		martillo.editar("Martillo nuevo", null, null, new BigDecimal("99.00"), UnidadVenta.UNIDAD,
				martillo.getCategoria(), List.of());
		em.flush();
		em.clear();

		Pedido leido = pedidoRepository.findById(id).orElseThrow();

		assertThat(leido.getEstado()).isEqualTo(EstadoPedido.PENDIENTE_PAGO);
		assertThat(leido.getNombreRetira()).isEqualTo("Carla Paz");
		assertThat(leido.getTelefonoRetira()).isEqualTo("71234567");
		assertThat(leido.getNota()).isEqualTo("Paso a las 18");
		assertThat(leido.getConfirmado()).isEqualTo(AHORA);
		assertThat(leido.getVence()).isEqualTo(VENCE);
		assertThat(leido.getLineas()).extracting(LineaPedido::getNombre, LineaPedido::getUnidad,
				LineaPedido::getPrecio, LineaPedido::getCantidad, LineaPedido::getSubtotal)
				.containsExactly(
						tuple("Martillo", UnidadVenta.UNIDAD, new BigDecimal("85.50"),
								new BigDecimal("2.00"), new BigDecimal("171.00")),
						tuple("Cable", UnidadVenta.METRO, new BigDecimal("12.00"),
								new BigDecimal("3.50"), new BigDecimal("42.00")));
		assertThat(leido.getTotal()).isEqualByComparingTo("213.00");
	}

	// HU-12 RF-13: un cliente solo encuentra sus pedidos
	@Test
	void soloElDuenoEncuentraSuPedido() {
		Long id = pedido(ana, AHORA).getIdPedido();
		em.flush();

		assertThat(pedidoRepository.findByIdPedidoAndUsuario_IdUsuario(id, ana.getIdUsuario())).isPresent();
		assertThat(pedidoRepository.findByIdPedidoAndUsuario_IdUsuario(id, beto.getIdUsuario())).isEmpty();
		assertThat(pedidoRepository.findConBloqueoByIdPedidoAndUsuario_IdUsuario(id, beto.getIdUsuario())).isEmpty();
	}

	// HU-12 RF-10: solo los pendientes con la hora cumplida
	@Test
	void buscaLosPendientesVencidos() {
		Pedido vencido = pedido(ana, AHORA.minusMinutes(30));
		pedido(beto, AHORA.minusMinutes(10));
		Usuario carla = em.persist(
				Usuario.registrarCliente("Carla", "Vaca", "carla.hu12@mail.com", "$2a$10$hash", null, AHORA));
		pedido(carla, AHORA.minusHours(2)).cancelar(AHORA.minusHours(1));
		em.flush();

		assertThat(pedidoRepository.findByEstadoAndVenceLessThanEqual(EstadoPedido.PENDIENTE_PAGO, AHORA))
				.containsExactly(new PedidoRepository.PedidoAVencer(vencido.getIdPedido(), ana.getIdUsuario()));
	}

	// HU-12 RF-12: cancelar y vencer solo desde «Pendiente de pago»
	@Test
	void cancelarOVencerSoloDesdePendiente() {
		Pedido cancelado = pedido(ana, AHORA);
		cancelado.cancelar(AHORA.plusMinutes(5));
		Pedido vencido = pedido(beto, AHORA);
		vencido.vencer(VENCE);

		assertThat(cancelado.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
		assertThat(cancelado.getCerrado()).isEqualTo(AHORA.plusMinutes(5));
		assertThat(vencido.getEstado()).isEqualTo(EstadoPedido.VENCIDO);
		assertThatThrownBy(() -> cancelado.vencer(VENCE)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> vencido.cancelar(VENCE)).isInstanceOf(IllegalStateException.class);
	}

	// HU-12 RF-14: la reserva resta y la liberación suma, con el pedido y a nombre del cliente
	@Test
	void reservaYLiberacionGuardanElPedido() {
		Pedido pedido = pedido(ana, AHORA);
		MovimientoStock reserva = em.persist(
				MovimientoStock.reserva(martillo, new BigDecimal("2"), new BigDecimal("8"), pedido, AHORA));
		MovimientoStock liberacion = em.persist(
				MovimientoStock.liberacion(martillo, new BigDecimal("2"), new BigDecimal("10"), pedido, VENCE));
		em.flush();

		assertThat(reserva.getTipo()).isEqualTo(TipoMovimiento.RESERVA);
		assertThat(reserva.getCantidad()).isEqualByComparingTo("-2");
		assertThat(liberacion.getTipo()).isEqualTo(TipoMovimiento.LIBERACION);
		assertThat(liberacion.getCantidad()).isEqualByComparingTo("2");
		assertThat(List.of(reserva, liberacion)).allSatisfy(movimiento -> {
			assertThat(movimiento.getPedido()).isSameAs(pedido);
			assertThat(movimiento.getUsuario()).isSameAs(ana);
		});
	}

	// HU-14 RF-1: la lista cuenta las líneas en la misma consulta, del más nuevo al más viejo
	@Test
	void misPedidosCuentaLasLineasSinCargarlas() {
		Pedido viejo = pedido(ana, AHORA.minusHours(2));
		Pedido nuevo = Pedido.confirmar(ana, "Ana Paz", "70000000", null, AHORA, VENCE);
		nuevo.agregarLinea(martillo, new BigDecimal("1"));
		nuevo.agregarLinea(cable, new BigDecimal("2.5"));
		em.persist(nuevo);
		pedido(beto, AHORA);
		em.flush();
		em.clear();

		var filas = pedidoRepository.misPedidos(ana.getIdUsuario(), PageRequest.of(0, 10));

		assertThat(filas.getTotalElements()).isEqualTo(2);
		assertThat(filas.getContent()).extracting(PedidoRepository.FilaDePedido::idPedido,
				PedidoRepository.FilaDePedido::cantidadProductos)
				.containsExactly(tuple(nuevo.getIdPedido(), 2), tuple(viejo.getIdPedido(), 1));
	}
}
