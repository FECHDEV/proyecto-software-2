package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.dto.AjusteRequest;
import bo.edu.uagrm.tienda.dto.EntradaRequest;
import bo.edu.uagrm.tienda.dto.InventarioResponse;
import bo.edu.uagrm.tienda.dto.MinimoRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.MotivoAjuste;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.AjusteSinCambioException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.ProductoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.StockFueraDeRangoException;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;

// HU-08: inventario
@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

	private static final ZoneId ZONA = ZoneId.of("America/La_Paz");
	private static final Instant AHORA = Instant.parse("2026-10-12T13:00:00Z");
	private static final long EMPLEADO = 4L;

	@Mock
	private ProductoRepository productoRepository;

	@Mock
	private MovimientoStockRepository movimientoRepository;

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private PedidoRepository pedidoRepository;

	private InventarioService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new InventarioService(productoRepository, movimientoRepository, pedidoRepository, usuarioRepository,
				Clock.fixed(AHORA, ZONA));
		// Sin pedidos abiertos: lo contado es lo disponible (lo comprometido se prueba en InventarioIntegracionTest)
		lenient().when(pedidoRepository.comprometidos(any())).thenReturn(List.of());
	}

	private Producto bloqueado(long id, UnidadVenta unidad, String stock) {
		Producto producto = Producto.crear("Producto " + id, null, null, BigDecimal.TEN, unidad,
				Categoria.crear("Herramientas", null), List.of(), LocalDateTime.ofInstant(AHORA, ZONA));
		ReflectionTestUtils.setField(producto, "idProducto", id);
		producto.asignarCodigo("HER-%04d".formatted(id));
		producto.aplicarMovimiento(new BigDecimal(stock));
		given(productoRepository.findConBloqueoByIdProducto(id)).willReturn(Optional.of(producto));
		return producto;
	}

	private void conEmpleado() {
		given(usuarioRepository.getReferenceById(EMPLEADO))
				.willReturn(Usuario.registrarCliente("Eva", "Paz", "eva@mail.com", "$2a$10$hash", null,
						LocalDateTime.ofInstant(AHORA, ZONA)));
	}

	private MovimientoStock guardado() {
		ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
		then(movimientoRepository).should().save(captor.capture());
		return captor.getValue();
	}

	// HU-08 RF-3
	@Test
	void entradaSumaYGuardaElMovimiento() {
		bloqueado(7, UnidadVenta.UNIDAD, "3");
		conEmpleado();

		InventarioResponse respuesta = servicio.registrarEntrada(7L, EMPLEADO,
				new EntradaRequest(new BigDecimal("10"), " Factura 123 "));

		assertThat(respuesta.stock()).isEqualByComparingTo("13");
		MovimientoStock movimiento = guardado();
		assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(movimiento.getCantidad()).isEqualByComparingTo("10");
		assertThat(movimiento.getStockResultante()).isEqualByComparingTo("13");
		assertThat(movimiento.getUsuario().getNombre()).isEqualTo("Eva");
		assertThat(movimiento.getFecha()).isEqualTo(LocalDateTime.of(2026, 10, 12, 9, 0));
		assertThat(movimiento.getNota()).isEqualTo("Factura 123");
	}

	// HU-08 RF-4: se escribe lo contado y se guarda la diferencia
	@Test
	void ajusteDejaLaCantidadContadaYGuardaLaDiferencia() {
		bloqueado(7, UnidadVenta.UNIDAD, "10");
		conEmpleado();

		InventarioResponse respuesta = servicio.registrarAjuste(7L, EMPLEADO,
				new AjusteRequest(new BigDecimal("8"), MotivoAjuste.MERMA, null));

		assertThat(respuesta.stock()).isEqualByComparingTo("8");
		MovimientoStock movimiento = guardado();
		assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.AJUSTE);
		assertThat(movimiento.getMotivo()).isEqualTo(MotivoAjuste.MERMA);
		assertThat(movimiento.getCantidad()).isEqualByComparingTo("-2");
		assertThat(movimiento.getStockResultante()).isEqualByComparingTo("8");
	}

	// HU-08 RF-4: se perdió todo
	@Test
	void ajusteACeroSeAcepta() {
		bloqueado(7, UnidadVenta.UNIDAD, "4");
		conEmpleado();

		assertThat(servicio.registrarAjuste(7L, EMPLEADO, new AjusteRequest(BigDecimal.ZERO, MotivoAjuste.CONTEO, null))
				.stock()).isEqualByComparingTo("0");
	}

	// HU-08 RF-5: los decimales que admite la unidad
	@Test
	void decimalesSegunLaUnidad() {
		bloqueado(7, UnidadVenta.UNIDAD, "0");
		bloqueado(8, UnidadVenta.METRO, "0");
		conEmpleado();

		assertThatThrownBy(() -> servicio.registrarEntrada(7L, EMPLEADO, new EntradaRequest(new BigDecimal("2.5"), null)))
				.isInstanceOfSatisfying(CamposInvalidosException.class, ex -> assertThat(ex.getErrores())
						.containsExactly(new CamposInvalidosException.CampoInvalido("cantidad",
								"tiene que ser un número entero")));
		// 2,00 en una unidad entera es 2
		assertThat(servicio.registrarEntrada(7L, EMPLEADO, new EntradaRequest(new BigDecimal("2.00"), null)).stock())
				.isEqualByComparingTo("2");
		assertThat(servicio.registrarEntrada(8L, EMPLEADO, new EntradaRequest(new BigDecimal("25.5"), null)).stock())
				.isEqualByComparingTo("25.5");
		assertThatThrownBy(() -> servicio.registrarAjuste(8L, EMPLEADO,
				new AjusteRequest(new BigDecimal("25.555"), MotivoAjuste.CONTEO, null)))
				.isInstanceOfSatisfying(CamposInvalidosException.class, ex -> assertThat(ex.getErrores())
						.containsExactly(new CamposInvalidosException.CampoInvalido("contada",
								"admite hasta dos decimales")));
	}

	// HU-08 RF-6
	@Test
	void entradaQueSuperaElMaximoSeRechaza() {
		Producto producto = bloqueado(7, UnidadVenta.UNIDAD, "9999990");

		assertThatThrownBy(() -> servicio.registrarEntrada(7L, EMPLEADO, new EntradaRequest(new BigDecimal("10"), null)))
				.isInstanceOfSatisfying(StockFueraDeRangoException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("STOCK_FUERA_DE_RANGO"));
		assertThat(producto.getStock()).isEqualByComparingTo("9999990");
		then(movimientoRepository).should(never()).save(any());
	}

	// HU-08 RF-6: 8 contados con 8,00 en stock no es un cambio
	@Test
	void ajusteSinCambioSeRechaza() {
		bloqueado(7, UnidadVenta.METRO, "8.00");

		assertThatThrownBy(() -> servicio.registrarAjuste(7L, EMPLEADO,
				new AjusteRequest(new BigDecimal("8"), MotivoAjuste.CONTEO, null)))
				.isInstanceOfSatisfying(AjusteSinCambioException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("AJUSTE_SIN_CAMBIO"));
		then(movimientoRepository).should(never()).save(any());
	}

	// HU-08 RF-7
	@Test
	void motivoOtroExigeNota() {
		bloqueado(7, UnidadVenta.UNIDAD, "5");

		assertThatThrownBy(() -> servicio.registrarAjuste(7L, EMPLEADO,
				new AjusteRequest(new BigDecimal("4"), MotivoAjuste.OTRO, "  ")))
				.isInstanceOfSatisfying(CamposInvalidosException.class, ex -> assertThat(ex.getErrores())
						.extracting(CamposInvalidosException.CampoInvalido::campo).containsExactly("nota"));
		then(movimientoRepository).should(never()).save(any());
	}

	// HU-08 RF-9: menor o igual al mínimo es stock bajo; sin mínimo, nunca
	@Test
	void minimoMarcaStockBajoYSeQuita() {
		bloqueado(7, UnidadVenta.UNIDAD, "5");

		assertThat(servicio.cambiarMinimo(7L, new MinimoRequest(new BigDecimal("5"))).stockBajo()).isTrue();
		assertThat(servicio.cambiarMinimo(7L, new MinimoRequest(new BigDecimal("4"))).stockBajo()).isFalse();
		InventarioResponse sinMinimo = servicio.cambiarMinimo(7L, new MinimoRequest(null));
		assertThat(sinMinimo.stockMinimo()).isNull();
		assertThat(sinMinimo.stockBajo()).isFalse();
		assertThatThrownBy(() -> servicio.cambiarMinimo(7L, new MinimoRequest(new BigDecimal("1.5"))))
				.isInstanceOf(CamposInvalidosException.class);
	}

	// HU-08 RF-10: un producto inactivo también se cuenta
	@Test
	void productoInactivoAdmiteMovimientos() {
		bloqueado(7, UnidadVenta.UNIDAD, "5").desactivar();
		conEmpleado();

		InventarioResponse respuesta = servicio.registrarAjuste(7L, EMPLEADO,
				new AjusteRequest(new BigDecimal("2"), MotivoAjuste.CONTEO, null));

		assertThat(respuesta.stock()).isEqualByComparingTo("2");
		assertThat(respuesta.activo()).isFalse();
	}

	// HU-08 RF-14
	@Test
	void productoInexistente() {
		given(productoRepository.findConBloqueoByIdProducto(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.registrarEntrada(99L, EMPLEADO, new EntradaRequest(BigDecimal.ONE, null)))
				.isInstanceOf(ProductoNoEncontradoException.class);
	}
}
