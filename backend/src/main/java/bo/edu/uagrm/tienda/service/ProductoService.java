package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.ProductoRequest;
import bo.edu.uagrm.tienda.dto.ProductoResponse;
import bo.edu.uagrm.tienda.dto.ProductoResumenResponse;
import bo.edu.uagrm.tienda.entity.Caracteristica;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.exception.CategoriaNoEncontradaException;
import bo.edu.uagrm.tienda.exception.FichaInvalidaException;
import bo.edu.uagrm.tienda.exception.ImagenDemasiadoGrandeException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;
import bo.edu.uagrm.tienda.exception.ModeloDemasiadoGrandeException;
import bo.edu.uagrm.tienda.exception.ModeloInvalidoException;
import bo.edu.uagrm.tienda.exception.OrdenDeImagenesInvalidoException;
import bo.edu.uagrm.tienda.exception.ProductoLlenoDeImagenesException;
import bo.edu.uagrm.tienda.exception.ProductoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.UnidadIncompatibleConStockException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

// Productos de la gestión (HU-07; decisiones.md → Productos). Devuelve DTOs y no entidades: la categoría y las
// colecciones son perezosas y se leen dentro de la transacción
@Service
@RequiredArgsConstructor
public class ProductoService {

	static final int MAXIMO_IMAGENES = 5;
	private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaAlta", "idProducto");
	private static final String PREFIJO_SIN_LETRAS = "PRD";

	private final ProductoRepository productoRepository;
	private final CategoriaRepository categoriaRepository;
	private final AlmacenImagenes almacen;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Page<ProductoResumenResponse> listar(Long idCategoria, Boolean activo, String texto, int pagina,
			int tamano) {
		PageRequest paginacion = Busquedas.pagina(pagina, tamano, ORDEN);
		return productoRepository.buscar(idCategoria, activo, Busquedas.patron(texto), paginacion)
				.map(ProductoResumenResponse::from);
	}

	@Transactional(readOnly = true)
	public ProductoResponse obtener(Long idProducto) {
		return ProductoResponse.from(
				productoRepository.findById(idProducto).orElseThrow(ProductoNoEncontradoException::new));
	}

	// El código lleva el número del producto: se asigna después del primer guardado, en la misma transacción
	@Transactional
	public ProductoResponse crear(ProductoRequest solicitud) {
		Categoria categoria = categoria(solicitud.idCategoria());
		Producto producto = productoRepository.saveAndFlush(Producto.crear(solicitud.nombre(), solicitud.marca(),
				solicitud.descripcion(), solicitud.precio(), solicitud.unidad(), categoria, ficha(solicitud),
				LocalDateTime.now(clock)));
		producto.marcarDestacado(solicitud.destacado());
		producto.cambiarCosto(solicitud.costo());
		producto.asignarCodigo(prefijo(categoria.getNombre()) + "-" + "%04d".formatted(producto.getIdProducto()));
		return ProductoResponse.from(producto);
	}

	@Transactional
	public ProductoResponse editar(Long idProducto, ProductoRequest solicitud) {
		Producto producto = bloquear(idProducto);
		// 12,5 m no se pueden volver unidades (HU-08 RF-13)
		if (!InventarioService.admiteDecimales(solicitud.unidad(), producto.getStock())
				|| (producto.getStockMinimo() != null
						&& !InventarioService.admiteDecimales(solicitud.unidad(), producto.getStockMinimo()))) {
			throw new UnidadIncompatibleConStockException();
		}
		producto.editar(solicitud.nombre(), solicitud.marca(), solicitud.descripcion(), solicitud.precio(),
				solicitud.unidad(), categoria(solicitud.idCategoria()), ficha(solicitud));
		producto.marcarDestacado(solicitud.destacado());
		producto.cambiarCosto(solicitud.costo());
		return ProductoResponse.from(producto);
	}

	@Transactional
	public ProductoResponse cambiarActivo(Long idProducto, boolean activo) {
		Producto producto = bloquear(idProducto);
		if (activo) {
			producto.activar();
		} else {
			producto.desactivar();
		}
		return ProductoResponse.from(producto);
	}

	// Con la fila bloqueada, dos subidas a la vez no ocupan el quinto lugar dos veces. El formato sale del
	// contenido (decisiones.md → Imágenes subidas); si la transacción se revierte, el archivo nuevo se borra
	@Transactional
	public ProductoResponse agregarImagen(Long idProducto, byte[] contenido) {
		if (contenido.length > FormatoImagen.MAXIMO) {
			throw new ImagenDemasiadoGrandeException();
		}
		FormatoImagen formato = FormatoImagen.detectar(contenido).orElseThrow(ImagenInvalidaException::new);
		Producto producto = bloquear(idProducto);
		if (producto.getImagenes().size() >= MAXIMO_IMAGENES) {
			throw new ProductoLlenoDeImagenesException();
		}
		String nueva = almacen.guardar(contenido, formato);
		ArchivosAlTerminar.borrar(almacen, null, nueva);
		producto.agregarImagen(nueva);
		return ProductoResponse.from(producto);
	}

	// Un nombre que no es del producto no cambia nada: así nunca se borra el archivo de otro
	@Transactional
	public ProductoResponse quitarImagen(Long idProducto, String imagen) {
		Producto producto = bloquear(idProducto);
		if (producto.getImagenes().contains(imagen)) {
			producto.quitarImagen(imagen);
			ArchivosAlTerminar.borrar(almacen, imagen, null);
		}
		return ProductoResponse.from(producto);
	}

	@Transactional
	public ProductoResponse reordenar(Long idProducto, List<String> orden) {
		Producto producto = bloquear(idProducto);
		List<String> actuales = producto.getImagenes();
		if (orden.size() != actuales.size() || !new HashSet<>(orden).equals(new HashSet<>(actuales))) {
			throw new OrdenDeImagenesInvalidoException();
		}
		producto.ordenarImagenes(orden);
		return ProductoResponse.from(producto);
	}

	// Pública, como la verá el catálogo; solo sirve las imágenes que son del producto
	@Transactional(readOnly = true)
	public Optional<ImagenGuardada> imagen(Long idProducto, String imagen) {
		return productoRepository.findById(idProducto).filter(producto -> producto.getImagenes().contains(imagen))
				.flatMap(producto -> almacen.leer(imagen))
				.map(contenido -> new ImagenGuardada(contenido, FormatoImagen.deArchivo(imagen)));
	}

	// Como agregarImagen: con la fila bloqueada, dos reemplazos a la vez dejan un solo modelo; el anterior se borra
	// solo si se confirma, y el nuevo si se revierte (decisiones.md → Visor 3D y modelos)
	@Transactional
	public ProductoResponse subirModelo(Long idProducto, byte[] contenido) {
		if (contenido.length > FormatoModelo.MAXIMO) {
			throw new ModeloDemasiadoGrandeException();
		}
		if (!FormatoModelo.esGlb(contenido)) {
			throw new ModeloInvalidoException();
		}
		Producto producto = bloquear(idProducto);
		String anterior = producto.getModelo();
		String nuevo = almacen.guardar(contenido, FormatoModelo.EXTENSION);
		ArchivosAlTerminar.borrar(almacen, anterior, nuevo);
		producto.cambiarModelo(nuevo, contenido.length);
		return ProductoResponse.from(producto);
	}

	// Sin modelo no cambia nada (HU-15 RF-6)
	@Transactional
	public ProductoResponse quitarModelo(Long idProducto) {
		Producto producto = bloquear(idProducto);
		if (producto.getModelo() != null) {
			ArchivosAlTerminar.borrar(almacen, producto.getModelo(), null);
			producto.quitarModelo();
		}
		return ProductoResponse.from(producto);
	}

	// Pública, como las imágenes: solo el modelo actual del producto (HU-15 RF-8)
	@Transactional(readOnly = true)
	public Optional<byte[]> modelo(Long idProducto, String archivo) {
		return productoRepository.findById(idProducto).filter(producto -> archivo.equals(producto.getModelo()))
				.flatMap(producto -> almacen.leer(archivo));
	}

	// Las tres primeras letras del nombre, sin tildes y en mayúsculas («Jardín» → «JAR»)
	static String prefijo(String nombreCategoria) {
		String letras = Categoria.clave(nombreCategoria).replaceAll("[^a-z]", "").toUpperCase(Locale.ROOT);
		return letras.isEmpty() ? PREFIJO_SIN_LETRAS : letras.substring(0, Math.min(3, letras.length()));
	}

	// Bloquea la categoría: no se borra mientras se le asigna un producto (HU-07 RF-12)
	private Categoria categoria(Long idCategoria) {
		return categoriaRepository.findConBloqueoByIdCategoria(idCategoria)
				.orElseThrow(CategoriaNoEncontradaException::new);
	}

	private Producto bloquear(Long idProducto) {
		return productoRepository.findConBloqueoByIdProducto(idProducto)
				.orElseThrow(ProductoNoEncontradoException::new);
	}

	// Dos características con el mismo nombre, sin importar mayúsculas ni tildes, se rechazan (HU-07 RF-6)
	private static List<Caracteristica> ficha(ProductoRequest solicitud) {
		Set<String> nombres = new HashSet<>();
		for (var par : solicitud.ficha()) {
			if (!nombres.add(Categoria.clave(par.nombre()))) {
				throw new FichaInvalidaException();
			}
		}
		return solicitud.ficha().stream().map(par -> new Caracteristica(par.nombre(), par.valor())).toList();
	}
}
