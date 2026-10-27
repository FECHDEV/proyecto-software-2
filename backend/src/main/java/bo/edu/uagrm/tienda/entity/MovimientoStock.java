package bo.edu.uagrm.tienda.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Un cambio del stock de un producto (HU-08). No tiene setters: un movimiento registrado no se edita ni se borra, y
// un error se corrige con otro ajuste (RF-12)
@Entity
@Table(name = "movimiento_stock", indexes = @Index(name = "idx_movimiento_producto_fecha", columnList = "id_producto, fecha"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimientoStock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_movimiento")
	private Long idMovimiento;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_producto", nullable = false)
	private Producto producto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TipoMovimiento tipo;

	// Solo en los ajustes
	@Enumerated(EnumType.STRING)
	@Column(length = 12)
	private MotivoAjuste motivo;

	// Con signo: lo que cambió el stock
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal cantidad;

	// El stock que quedó: el historial se lee sin recalcular
	@Column(name = "stock_resultante", nullable = false, precision = 9, scale = 2)
	private BigDecimal stockResultante;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	private Usuario usuario;

	@Column(nullable = false)
	private LocalDateTime fecha;

	@Column(length = 200)
	private String nota;

	// Solo en los ajustes desde el 27/10/2026: lo contado en el estante y lo comprometido en pedidos abiertos en ese
	// momento (HU-08 RF-17). Los ajustes anteriores no los tienen
	@Column(precision = 9, scale = 2)
	private BigDecimal contado;

	@Column(precision = 9, scale = 2)
	private BigDecimal comprometido;

	// Solo en la reserva y la liberación de un pedido (HU-12 RF-14)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_pedido")
	private Pedido pedido;

	public static MovimientoStock entrada(Producto producto, BigDecimal cantidad, BigDecimal stockResultante,
			Usuario usuario, LocalDateTime fecha, String nota) {
		return new MovimientoStock(producto, TipoMovimiento.ENTRADA, null, cantidad, stockResultante, usuario, fecha,
				nota);
	}

	public static MovimientoStock ajuste(Producto producto, BigDecimal diferencia, BigDecimal stockResultante,
			BigDecimal contado, BigDecimal comprometido, MotivoAjuste motivo, Usuario usuario, LocalDateTime fecha,
			String nota) {
		MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.AJUSTE, Objects.requireNonNull(motivo),
				diferencia, stockResultante, usuario, fecha, nota);
		movimiento.contado = contado.setScale(2, RoundingMode.UNNECESSARY);
		movimiento.comprometido = comprometido.setScale(2, RoundingMode.UNNECESSARY);
		return movimiento;
	}

	// Resta lo que el pedido reserva, a nombre del cliente (HU-12 RF-2)
	public static MovimientoStock reserva(Producto producto, BigDecimal cantidad, BigDecimal stockResultante,
			Pedido pedido, LocalDateTime fecha) {
		MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.RESERVA, null, cantidad.negate(),
				stockResultante, pedido.getUsuario(), fecha, null);
		movimiento.pedido = pedido;
		return movimiento;
	}

	// Devuelve lo reservado al cancelar o vencer el pedido (HU-12 RF-9, RF-10)
	public static MovimientoStock liberacion(Producto producto, BigDecimal cantidad, BigDecimal stockResultante,
			Pedido pedido, LocalDateTime fecha) {
		MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.LIBERACION, null, cantidad,
				stockResultante, pedido.getUsuario(), fecha, null);
		movimiento.pedido = pedido;
		return movimiento;
	}

	private MovimientoStock(Producto producto, TipoMovimiento tipo, MotivoAjuste motivo, BigDecimal cantidad,
			BigDecimal stockResultante, Usuario usuario, LocalDateTime fecha, String nota) {
		this.producto = Objects.requireNonNull(producto);
		this.tipo = tipo;
		this.motivo = motivo;
		this.cantidad = cantidad.setScale(2, RoundingMode.UNNECESSARY);
		this.stockResultante = stockResultante.setScale(2, RoundingMode.UNNECESSARY);
		this.usuario = Objects.requireNonNull(usuario);
		this.fecha = Objects.requireNonNull(fecha);
		this.nota = nota == null || nota.isBlank() ? null : nota.strip();
	}
}
