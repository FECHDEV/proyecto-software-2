package bo.edu.uagrm.tienda.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Un producto de la ferretería (HU-07). Nunca se borra: se desactiva
@Entity
@Table(name = "producto", indexes = @Index(name = "idx_producto_fecha_alta", columnList = "fecha_alta"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_producto")
	private Long idProducto;

	// Se asigna en la misma transacción, después del primer guardado, porque lleva el número del producto
	@Column(length = 20, unique = true)
	private String codigo;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 60)
	private String marca;

	@Column(length = 1000)
	private String descripcion;

	@Column(nullable = false, precision = 7, scale = 2)
	private BigDecimal precio;

	// Opcional, lo carga el Administrador: con él se calcula el margen de los reportes (HU-18 RF-19). Nunca sale al
	// cliente
	@Column(precision = 7, scale = 2)
	private BigDecimal costo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private UnidadVenta unidad;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_categoria", nullable = false)
	private Categoria categoria;

	@Column(nullable = false)
	private boolean activo;

	@Column(name = "fecha_alta", nullable = false)
	private LocalDateTime fechaAlta;

	// Solo cambia con un movimiento (HU-08). El valor por defecto completa los productos que ya existían
	@ColumnDefault("0")
	@Column(nullable = false, precision = 9, scale = 2)
	private BigDecimal stock = BigDecimal.ZERO;

	// Opcional: con stock menor o igual, el producto está en «Stock bajo»
	@Column(name = "stock_minimo", precision = 9, scale = 2)
	private BigDecimal stockMinimo;

	// La portada lo muestra en «Destacados» (HU-09). El valor por defecto completa los productos que ya existían
	@ColumnDefault("false")
	@Column(nullable = false)
	private boolean destacado;

	// Nombre, marca y código sin tildes ni mayúsculas, para que la búsqueda del catálogo los ignore igual en H2 y en
	// MySQL (HU-09 RF-8). Los productos que ya existían la reciben al arrancar (BusquedaAlArrancar)
	@Column(length = 250)
	private String busqueda;

	@ElementCollection
	@CollectionTable(name = "producto_caracteristica", joinColumns = @JoinColumn(name = "id_producto"))
	@OrderColumn(name = "posicion")
	private List<Caracteristica> ficha = new ArrayList<>();

	// Nombres de archivo que generó AlmacenImagenes, en orden: el primero es la imagen principal. Se cargan de a
	// página para la lista, en vez de una consulta por producto
	@ElementCollection
	@BatchSize(size = 20)
	@CollectionTable(name = "producto_imagen", joinColumns = @JoinColumn(name = "id_producto"))
	@OrderColumn(name = "posicion")
	@Column(name = "imagen", nullable = false, length = 64)
	private List<String> imagenes = new ArrayList<>();

	// El modelo 3D (HU-15): el archivo que generó AlmacenImagenes y su peso en bytes, los dos o ninguno
	@Column(length = 64)
	private String modelo;

	@Column(name = "peso_modelo")
	private Long pesoModelo;

	public static Producto crear(String nombre, String marca, String descripcion, BigDecimal precio,
			UnidadVenta unidad, Categoria categoria, List<Caracteristica> ficha, LocalDateTime fechaAlta) {
		Producto producto = new Producto();
		producto.editar(nombre, marca, descripcion, precio, unidad, categoria, ficha);
		producto.activo = true;
		producto.fechaAlta = Objects.requireNonNull(fechaAlta);
		return producto;
	}

	public void editar(String nombre, String marca, String descripcion, BigDecimal precio, UnidadVenta unidad,
			Categoria categoria, List<Caracteristica> ficha) {
		this.nombre = Objects.requireNonNull(nombre).strip();
		this.marca = sinVacio(marca);
		this.descripcion = sinVacio(descripcion);
		this.precio = Objects.requireNonNull(precio);
		this.unidad = Objects.requireNonNull(unidad);
		this.categoria = Objects.requireNonNull(categoria);
		this.ficha.clear();
		this.ficha.addAll(ficha);
		actualizarBusqueda();
	}

	public void cambiarCosto(BigDecimal costo) {
		this.costo = costo;
	}

	public void asignarCodigo(String codigo) {
		if (this.codigo != null) {
			throw new IllegalStateException("El código de un producto no cambia");
		}
		this.codigo = Objects.requireNonNull(codigo);
		actualizarBusqueda();
	}

	public void marcarDestacado(boolean destacado) {
		this.destacado = destacado;
	}

	public void actualizarBusqueda() {
		this.busqueda = textoDeBusqueda(Stream.of(nombre, marca, codigo).filter(Objects::nonNull)
				.collect(Collectors.joining(" ")));
	}

	// Sin tildes, en minúsculas y con un solo espacio entre palabras: lo que se guarda y lo que se busca
	public static String textoDeBusqueda(String texto) {
		return Categoria.clave(texto).replaceAll("\\s+", " ");
	}

	public List<Caracteristica> getFicha() {
		return Collections.unmodifiableList(ficha);
	}

	public List<String> getImagenes() {
		return Collections.unmodifiableList(imagenes);
	}

	public void agregarImagen(String imagen) {
		imagenes.add(Objects.requireNonNull(imagen));
	}

	public void quitarImagen(String imagen) {
		imagenes.remove(imagen);
	}

	public void ordenarImagenes(List<String> orden) {
		imagenes.clear();
		imagenes.addAll(orden);
	}

	public void cambiarModelo(String modelo, long peso) {
		this.modelo = Objects.requireNonNull(modelo);
		this.pesoModelo = peso;
	}

	public void quitarModelo() {
		modelo = null;
		pesoModelo = null;
	}

	// Lo llama InventarioService con la fila bloqueada, junto con el movimiento que lo explica
	public void aplicarMovimiento(BigDecimal nuevoStock) {
		if (nuevoStock.signum() < 0) {
			throw new IllegalArgumentException("El stock nunca es negativo");
		}
		// Con la escala de la columna: la respuesta dice 8.00 tanto recién guardado como leído de la base
		this.stock = nuevoStock.setScale(2, RoundingMode.UNNECESSARY);
	}

	public void cambiarMinimo(BigDecimal minimo) {
		this.stockMinimo = minimo == null ? null : minimo.setScale(2, RoundingMode.UNNECESSARY);
	}

	public boolean isStockBajo() {
		return stockMinimo != null && stock.compareTo(stockMinimo) <= 0;
	}

	public void activar() {
		activo = true;
	}

	public void desactivar() {
		activo = false;
	}

	private static String sinVacio(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}
}
