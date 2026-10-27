package bo.edu.uagrm.tienda.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Un producto del pedido (HU-12 RF-2): una copia del nombre, la unidad y el precio al confirmar. Si el producto
// cambia después, el pedido queda como se confirmó
@Entity
@Table(name = "linea_pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaPedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_linea")
	private Long idLinea;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_pedido", nullable = false)
	private Pedido pedido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_producto", nullable = false)
	private Producto producto;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private UnidadVenta unidad;

	@Column(nullable = false, precision = 7, scale = 2)
	private BigDecimal precio;

	@Column(nullable = false, precision = 7, scale = 2)
	private BigDecimal cantidad;

	// Hasta 99 999,99 × 999
	@Column(nullable = false, precision = 11, scale = 2)
	private BigDecimal subtotal;

	// El costo del producto al confirmar, como el precio: un cambio después no toca el margen de lo vendido. Nulo si el
	// producto no tenía costo, y en las líneas de antes de HU-18 (RF-20)
	@Column(precision = 7, scale = 2)
	private BigDecimal costo;

	// Lo que volvió al carrito al cancelar o vencer, con sus topes; nulo si no volvió nada. Un pago tardío resta del
	// carrito solo esto (HU-13 RF-9)
	@Column(name = "cantidad_devuelta", precision = 7, scale = 2)
	private BigDecimal cantidadDevuelta;

	// La línea del carrito que lo recibió: si el Cliente la quitó y agregó el producto otra vez, la nueva no tiene nada
	// del pedido y el pago tardío no la toca (revisión total). Nulo en las líneas de antes
	@Column(name = "id_linea_carrito")
	private Long idLineaCarrito;

	LineaPedido(Pedido pedido, Producto producto, BigDecimal cantidad) {
		this.pedido = Objects.requireNonNull(pedido);
		this.producto = Objects.requireNonNull(producto);
		this.nombre = producto.getNombre();
		this.unidad = producto.getUnidad();
		this.precio = producto.getPrecio();
		this.costo = producto.getCosto();
		this.cantidad = cantidad.setScale(2, RoundingMode.UNNECESSARY);
		this.subtotal = precio.multiply(this.cantidad).setScale(2, RoundingMode.HALF_UP);
	}

	public void volvioAlCarrito(BigDecimal cantidadDevuelta, Long idLineaCarrito) {
		boolean volvio = cantidadDevuelta.signum() > 0;
		this.cantidadDevuelta = volvio ? cantidadDevuelta : null;
		this.idLineaCarrito = volvio ? idLineaCarrito : null;
	}

	public Long getIdProducto() {
		return producto.getIdProducto();
	}
}
