package bo.edu.uagrm.tienda.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Lo que un Cliente confirmó de su carrito, para retirar en la tienda (HU-12). Espera el pago hasta que se paga
// (HU-13), vence a los 30 minutos o el cliente lo cancela; el stock lo reserva y lo devuelve PedidoService. Pagado, el
// personal lo entrega a quien lo retira (HU-17)
@Entity
@Table(name = "pedido", indexes = {
		@Index(name = "idx_pedido_usuario_estado", columnList = "id_usuario, estado"),
		@Index(name = "idx_pedido_estado_vence", columnList = "estado, vence"),
		@Index(name = "idx_pedido_estado_pagado", columnList = "estado, pagado"),
		@Index(name = "idx_pedido_estado_confirmado", columnList = "estado, confirmado"),
		@Index(name = "idx_pedido_confirmado", columnList = "confirmado") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_pedido")
	private Long idPedido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	private Usuario usuario;

	// varchar y no el enum de MySQL, que no admitía ENTREGADO (EstadoPedidoAlArrancar). En una base nueva Hibernate
	// crea además un check con los estados de ahora, que ddl-auto=update no cambia: un estado nuevo vuelve a pedir
	// cambiar la columna (decisiones.md → Base de datos: sin migraciones)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private EstadoPedido estado;

	// Hasta 50 líneas de 99 999,99 × 999
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;

	@Column(name = "nombre_retira", nullable = false, length = 160)
	private String nombreRetira;

	// El nombre de quien retira sin tildes ni mayúsculas, para que la búsqueda del personal los ignore igual en H2 y en
	// MySQL (HU-17 RF-3). Los pedidos que ya existían la reciben al arrancar (BusquedaAlArrancar)
	@Column(name = "busqueda_retira", length = 160)
	private String busquedaRetira;

	@Column(name = "telefono_retira", nullable = false, length = 20)
	private String telefonoRetira;

	@Column(length = 200)
	private String nota;

	@Column(nullable = false)
	private LocalDateTime confirmado;

	@Column(nullable = false)
	private LocalDateTime vence;

	// Cuándo se canceló o venció
	private LocalDateTime cerrado;

	// Cuándo se pagó, también en un pago para devolver (HU-13)
	private LocalDateTime pagado;

	// Cuándo y quién del personal lo entregó (HU-17 RF-6)
	private LocalDateTime entregado;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_entregado_por")
	private Usuario entregadoPor;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
	@OrderBy("idLinea")
	private List<LineaPedido> lineas = new ArrayList<>();

	// Los datos de retiro ya validados (RF-6); se guardan sin los espacios de alrededor
	public static Pedido confirmar(Usuario usuario, String nombreRetira, String telefonoRetira, String nota,
			LocalDateTime confirmado, LocalDateTime vence) {
		Pedido pedido = new Pedido();
		pedido.usuario = Objects.requireNonNull(usuario);
		pedido.estado = EstadoPedido.PENDIENTE_PAGO;
		pedido.nombreRetira = nombreRetira.strip();
		pedido.actualizarBusqueda();
		pedido.telefonoRetira = telefonoRetira.strip();
		pedido.nota = nota == null || nota.isBlank() ? null : nota.strip();
		pedido.confirmado = Objects.requireNonNull(confirmado);
		pedido.vence = Objects.requireNonNull(vence);
		return pedido;
	}

	// Copia el producto como está ahora (RF-2)
	public void agregarLinea(Producto producto, BigDecimal cantidad) {
		LineaPedido linea = new LineaPedido(this, producto, cantidad);
		lineas.add(linea);
		total = total.add(linea.getSubtotal());
	}

	// Ordenadas por producto: el orden en que se bloquean los productos al liberar, el mismo que al confirmar
	public List<LineaPedido> lineasPorProducto() {
		return lineas.stream().sorted(Comparator.comparing(LineaPedido::getIdProducto)).toList();
	}

	public void cancelar(LocalDateTime fecha) {
		cerrar(EstadoPedido.CANCELADO, fecha);
	}

	public void vencer(LocalDateTime fecha) {
		cerrar(EstadoPedido.VENCIDO, fecha);
	}

	// Desde pendiente, o desde vencido o cancelado si es un pago tardío que revive (HU-13 RF-5, RF-9). La conciliación
	// lo revisa antes, con el pedido bloqueado
	public void pagar(LocalDateTime fecha) {
		if (estado != EstadoPedido.PENDIENTE_PAGO && !esPagoTardio()) {
			throw new IllegalStateException("El pedido " + idPedido + " no se puede pagar");
		}
		estado = EstadoPedido.PAGADO;
		pagado = Objects.requireNonNull(fecha);
	}

	// Un pago tardío sin stock: la devolución se hace fuera del sistema (HU-13 RF-9)
	public void marcarPagoADevolver(LocalDateTime fecha) {
		if (!esPagoTardio()) {
			throw new IllegalStateException("El pedido " + idPedido + " no tiene un pago tardío");
		}
		estado = EstadoPedido.PAGO_A_DEVOLVER;
		pagado = Objects.requireNonNull(fecha);
	}

	// Solo desde pagado (HU-17 RF-6 a RF-8): DespachoService lo revisa antes, con el pedido bloqueado. No mueve el stock:
	// la venta ya estaba hecha al pagar
	public void entregar(Usuario quien, LocalDateTime fecha) {
		if (estado != EstadoPedido.PAGADO) {
			throw new IllegalStateException("El pedido " + idPedido + " no está pagado");
		}
		estado = EstadoPedido.ENTREGADO;
		entregado = Objects.requireNonNull(fecha);
		entregadoPor = Objects.requireNonNull(quien);
	}

	public void actualizarBusqueda() {
		busquedaRetira = Producto.textoDeBusqueda(nombreRetira);
	}

	private boolean esPagoTardio() {
		return estado == EstadoPedido.VENCIDO || estado == EstadoPedido.CANCELADO;
	}

	// Solo desde «Pendiente de pago» (RF-12): PedidoService lo revisa antes, con el pedido bloqueado
	private void cerrar(EstadoPedido nuevo, LocalDateTime fecha) {
		if (estado != EstadoPedido.PENDIENTE_PAGO) {
			throw new IllegalStateException("El pedido " + idPedido + " ya no espera el pago");
		}
		estado = nuevo;
		cerrado = Objects.requireNonNull(fecha);
	}
}
