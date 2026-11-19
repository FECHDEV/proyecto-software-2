package bo.edu.uagrm.tienda.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// Un descuento en porcentaje sobre un producto o una categoría, entre dos días incluidos, en hora de Bolivia (HU-24
// RF-1). No cambia el precio del producto: lo aplica PreciosDeVenta al mostrar y al cobrar, y la línea del pedido
// guarda lo cobrado, así que borrarlo o terminarlo no toca lo vendido. Exactamente uno de producto o categoría: lo
// aseguran las dos fábricas (sin migraciones, Hibernate no genera el CHECK)
@Entity
@Table(name = "descuento", indexes = {
		@Index(name = "idx_descuento_producto_fin", columnList = "id_producto, fin"),
		@Index(name = "idx_descuento_categoria_fin", columnList = "id_categoria, fin") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Descuento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_descuento")
	private Long idDescuento;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_producto")
	private Producto producto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_categoria")
	private Categoria categoria;

	@Column(nullable = false)
	private int porcentaje;

	@Column(nullable = false)
	private LocalDate inicio;

	@Column(nullable = false)
	private LocalDate fin;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_creado_por", nullable = false)
	private Usuario creadoPor;

	@Column(nullable = false)
	private LocalDateTime creado;

	public static Descuento deProducto(Producto producto, int porcentaje, LocalDate inicio, LocalDate fin,
			Usuario creadoPor, LocalDateTime creado) {
		Descuento descuento = nuevo(porcentaje, inicio, fin, creadoPor, creado);
		descuento.producto = Objects.requireNonNull(producto);
		return descuento;
	}

	public static Descuento deCategoria(Categoria categoria, int porcentaje, LocalDate inicio, LocalDate fin,
			Usuario creadoPor, LocalDateTime creado) {
		Descuento descuento = nuevo(porcentaje, inicio, fin, creadoPor, creado);
		descuento.categoria = Objects.requireNonNull(categoria);
		return descuento;
	}

	private static Descuento nuevo(int porcentaje, LocalDate inicio, LocalDate fin, Usuario creadoPor,
			LocalDateTime creado) {
		Descuento descuento = new Descuento();
		descuento.cambiar(porcentaje, inicio, fin);
		descuento.creadoPor = Objects.requireNonNull(creadoPor);
		descuento.creado = Objects.requireNonNull(creado);
		return descuento;
	}

	// RF-4: lo programado cambia el porcentaje y las fechas, nunca a qué se aplica. Las reglas las valida
	// DescuentoService
	public void cambiar(int porcentaje, LocalDate inicio, LocalDate fin) {
		this.porcentaje = porcentaje;
		this.inicio = Objects.requireNonNull(inicio);
		this.fin = Objects.requireNonNull(fin);
	}

	// RF-5: un vigente termina ayer, así hoy ya no se aplica
	public void terminar(LocalDate hoy) {
		fin = hoy.minusDays(1);
	}

	public EstadoDescuento estado(LocalDate hoy) {
		if (hoy.isBefore(inicio)) {
			return EstadoDescuento.PROGRAMADO;
		}
		return hoy.isAfter(fin) ? EstadoDescuento.VENCIDO : EstadoDescuento.VIGENTE;
	}
}
