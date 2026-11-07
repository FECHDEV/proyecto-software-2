package bo.edu.uagrm.tienda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.CobroRepository.CobroAConciliar;

// HU-13: los cobros de los pedidos y los estados del pago
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CobroRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 20, 9, 0);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private CobroRepository cobroRepository;

	private Usuario ana;
	private Producto martillo;

	@BeforeEach
	void preparar() {
		Categoria herramientas = em.persist(Categoria.crear("Herramientas HU-13", null));
		martillo = em.persist(Producto.crear("Martillo", null, null, new BigDecimal("85.50"), UnidadVenta.UNIDAD,
				herramientas, List.of(), AHORA));
		martillo.asignarCodigo("HER-H13-" + martillo.getIdProducto());
		ana = em.persist(Usuario.registrarCliente("Ana", "Paz", "ana.hu13@mail.com", "$2a$10$hash", null, AHORA));
		em.flush();
	}

	private Pedido pedido(LocalDateTime confirmado) {
		Pedido pedido = Pedido.confirmar(ana, "Ana Paz", "70000000", null, confirmado, confirmado.plusMinutes(30));
		pedido.agregarLinea(martillo, new BigDecimal("2"));
		return em.persist(pedido);
	}

	private Cobro cobro(Pedido pedido, String referencia, LocalDateTime generado) {
		return em.persist(Cobro.generar(pedido, referencia, "QR|" + referencia, null, generado));
	}

	// HU-13 RF-2: se muestra el pendiente; uno con error no cuenta. La referencia no se repite
	@Test
	void unCobroPendientePorPedidoYLaReferenciaNoSeRepite() {
		Pedido pedido = pedido(AHORA);
		cobro(pedido, "TM-P1-a", AHORA).registrarError();
		Cobro pendiente = cobro(pedido, "TM-P1-b", AHORA.plusMinutes(1));
		em.flush();

		assertThat(cobroRepository.findFirstByPedido_IdPedidoAndEstado(pedido.getIdPedido(), EstadoCobro.PENDIENTE))
				.containsSame(pendiente);
		assertThat(pendiente.getMonto()).isEqualByComparingTo("171.00");
		assertThat(cobroRepository.idPorReferencia("TM-P1-b")).contains(pendiente.getIdCobro());
		assertThat(cobroRepository.idPorReferencia("otra")).isEmpty();

		Pedido otro = pedido(AHORA);
		// Con IDENTITY, persist inserta en el acto
		assertThatThrownBy(() -> {
			cobro(otro, "TM-P1-b", AHORA);
			em.flush();
		}).isInstanceOf(RuntimeException.class);
	}

	// HU-13 RF-8: los pendientes generados en las últimas 24 horas; los procesados, no
	@Test
	void idsPorConciliarSoloPendientesDeLasUltimas24Horas() {
		Pedido pedido = pedido(AHORA.minusDays(2));
		Cobro viejo = cobro(pedido, "TM-viejo", AHORA.minusHours(25));
		Cobro reciente = cobro(pedido(AHORA.minusHours(3)), "TM-reciente", AHORA.minusHours(3));
		Cobro pagado = cobro(pedido(AHORA), "TM-pagado", AHORA);
		pagado.registrarPago(AHORA);
		Cobro conError = cobro(pedido(AHORA), "TM-error", AHORA);
		conError.registrarError();
		em.flush();

		assertThat(cobroRepository.idsPorConciliar(AHORA.minusHours(24))).containsExactly(reciente.getIdCobro());
		assertThat(viejo.getEstado()).isEqualTo(EstadoCobro.PENDIENTE);
	}

	// HU-13 RF-4, RF-11: lo necesario para consultar, sin cargar el cobro, y solo si está pendiente
	@Test
	void datosParaConciliarSoloDeUnCobroPendiente() {
		Pedido pedido = pedido(AHORA);
		Cobro pendiente = cobro(pedido, "TM-pend", AHORA);
		Cobro pagado = cobro(pedido(AHORA), "TM-pag", AHORA);
		pagado.registrarPago(AHORA);
		em.flush();
		em.clear();

		assertThat(cobroRepository.datosParaConciliar(pendiente.getIdCobro())).contains(new CobroAConciliar(
				pendiente.getIdCobro(), "TM-pend", pedido.getIdPedido(), ana.getIdUsuario(),
				bo.edu.uagrm.tienda.entity.Canal.EN_LINEA));
		assertThat(cobroRepository.datosParaConciliar(pagado.getIdCobro())).isEmpty();
	}

	// HU-13 RF-11: el resultado del sistema de pago se aplica una sola vez
	@Test
	void elCobroSeProcesaUnaSolaVez() {
		Cobro pagado = cobro(pedido(AHORA), "TM-1", AHORA);
		pagado.registrarPago(AHORA.plusMinutes(3));
		Cobro conError = cobro(pedido(AHORA), "TM-2", AHORA);
		conError.registrarError();
		Cobro consultado = cobro(pedido(AHORA), "TM-3", AHORA);
		consultado.consultadoSinPago(AHORA.plusMinutes(31));

		assertThat(pagado.getFechaPago()).isEqualTo(AHORA.plusMinutes(3));
		assertThat(consultado.getConsultadoSinPago()).isEqualTo(AHORA.plusMinutes(31));
		assertThatThrownBy(() -> pagado.registrarError()).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> pagado.registrarPago(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> conError.registrarPago(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> conError.consultadoSinPago(AHORA)).isInstanceOf(IllegalStateException.class);
	}

	// HU-13 RF-5, RF-9: se paga desde pendiente, y desde vencido o cancelado (el pago tardío)
	@Test
	void pagarDesdePendienteVencidoOCancelado() {
		Pedido pendiente = pedido(AHORA);
		Pedido vencido = pedido(AHORA);
		vencido.vencer(AHORA.plusMinutes(30));
		Pedido cancelado = pedido(AHORA);
		cancelado.cancelar(AHORA.plusMinutes(5));

		List.of(pendiente, vencido, cancelado).forEach(pedido -> pedido.pagar(AHORA.plusMinutes(40)));

		assertThat(List.of(pendiente, vencido, cancelado)).allSatisfy(pedido -> {
			assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.PAGADO);
			assertThat(pedido.getPagado()).isEqualTo(AHORA.plusMinutes(40));
		});
		// Pagado no se cancela, no vence ni se paga otra vez (RF-5)
		assertThatThrownBy(() -> pendiente.cancelar(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> pendiente.vencer(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> pendiente.pagar(AHORA)).isInstanceOf(IllegalStateException.class);
	}

	// HU-13 RF-9: «Pago a devolver» solo para un pago tardío
	@Test
	void pagoADevolverSoloDesdeVencidoOCancelado() {
		Pedido vencido = pedido(AHORA);
		vencido.vencer(AHORA.plusMinutes(30));
		Pedido pendiente = pedido(AHORA);

		vencido.marcarPagoADevolver(AHORA.plusMinutes(40));

		assertThat(vencido.getEstado()).isEqualTo(EstadoPedido.PAGO_A_DEVOLVER);
		assertThat(vencido.getPagado()).isEqualTo(AHORA.plusMinutes(40));
		assertThatThrownBy(() -> pendiente.marcarPagoADevolver(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> vencido.pagar(AHORA)).isInstanceOf(IllegalStateException.class);
	}
}
