package bo.edu.uagrm.tienda.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.OrdenCatalogo;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.PortadaResponse;
import bo.edu.uagrm.tienda.dto.ProductoDetalleResponse;
import bo.edu.uagrm.tienda.dto.ProductoTarjetaResponse;
import bo.edu.uagrm.tienda.dto.SeccionResponse;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException.CampoInvalido;
import bo.edu.uagrm.tienda.exception.ProductoNoEncontradoException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

// El catálogo público (HU-09; decisiones.md → Catálogo público): solo productos activos de categorías visibles, y
// nunca la cantidad en stock
@Service
@RequiredArgsConstructor
public class CatalogoService {

	static final int POR_PAGINA = 24;
	static final int POR_CARRUSEL = 12;
	static final int BUSQUEDA_MAXIMA = 100;
	private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("99999.99");

	private final ProductoRepository productoRepository;
	private final CategoriaRepository categoriaRepository;
	private final PreciosDeVenta preciosDeVenta;

	public record Filtros(String texto, Long idCategoria, String marca, BigDecimal precioMin, BigDecimal precioMax,
			boolean soloDisponibles, OrdenCatalogo orden, int pagina) {
	}

	@Transactional(readOnly = true)
	public PaginaResponse<ProductoTarjetaResponse> listar(Filtros filtros) {
		validar(filtros);
		String marca = filtros.marca() == null || filtros.marca().isBlank() ? null
				: filtros.marca().strip().toLowerCase(Locale.ROOT);
		OrdenCatalogo orden = filtros.orden() == null ? OrdenCatalogo.RECIENTES : filtros.orden();
		// Una página negativa es la primera, como en la gestión (HU-07); una enorme, una que llega vacía
		PageRequest paginacion = PageRequest.of(Busquedas.paginaValida(filtros.pagina(), POR_PAGINA), POR_PAGINA,
				orden.orden());
		String patron = Busquedas.patron(filtros.texto(), Producto::textoDeBusqueda);
		Page<Producto> pagina = productoRepository.catalogo(patron, filtros.idCategoria(), marca, filtros.precioMin(),
				filtros.precioMax(), filtros.soloDisponibles(), paginacion);
		// HU-24 RF-12: los precios de toda la página en una sola lectura de los descuentos
		Map<Long, PrecioDeVenta> precios = preciosDeVenta.para(pagina.getContent());
		return PaginaResponse.from(pagina,
				producto -> ProductoTarjetaResponse.from(producto, precios.get(producto.getIdProducto())));
	}

	// HU-24 RF-12: todos los carruseles juntos piden sus precios en una sola lectura de los descuentos
	@Transactional(readOnly = true)
	public PortadaResponse portada() {
		List<Producto> destacados = vitrina(null, true, POR_CARRUSEL);
		List<Producto> recientes = vitrina(null, false, POR_CARRUSEL);
		Map<Categoria, List<Producto>> porCategoria = new LinkedHashMap<>();
		categoriaRepository.findByVisibleTrueOrderByNombreClave().forEach(categoria -> {
			List<Producto> productos = vitrina(categoria.getIdCategoria(), false, POR_CARRUSEL);
			if (!productos.isEmpty()) {
				porCategoria.put(categoria, productos);
			}
		});
		List<Producto> todos = new ArrayList<>(destacados);
		todos.addAll(recientes);
		porCategoria.values().forEach(todos::addAll);
		Map<Long, PrecioDeVenta> precios = preciosDeVenta.para(todos);
		List<SeccionResponse> categorias = porCategoria.entrySet().stream()
				.map(seccion -> new SeccionResponse(seccion.getKey().getIdCategoria(), seccion.getKey().getNombre(),
						tarjetas(seccion.getValue(), precios)))
				.toList();
		return new PortadaResponse(tarjetas(destacados, precios), tarjetas(recientes, precios), categorias);
	}

	// HU-10: el detalle con hasta 12 de su categoría; se piden 13 por si el producto está entre ellos
	@Transactional(readOnly = true)
	public ProductoDetalleResponse detalle(Long idProducto) {
		Producto producto = productoRepository.publico(idProducto).orElseThrow(ProductoNoEncontradoException::new);
		List<Producto> relacionados = vitrina(producto.getCategoria().getIdCategoria(), false, POR_CARRUSEL + 1)
				.stream().filter(otro -> !otro.getIdProducto().equals(idProducto)).limit(POR_CARRUSEL).toList();
		// HU-24 RF-12, RF-14: el del producto y los de sus relacionados, en una sola lectura de los descuentos
		List<Producto> todos = new ArrayList<>(relacionados);
		todos.add(producto);
		Map<Long, PrecioDeVenta> precios = preciosDeVenta.para(todos);
		return ProductoDetalleResponse.from(producto, precios.get(idProducto), tarjetas(relacionados, precios));
	}

	// Lo que lista el mapa del sitio: la misma regla que el catálogo, también agotado (plan 2026-11-07-seo)
	@Transactional(readOnly = true)
	public List<Long> idsPublicos() {
		return productoRepository.idsPublicos();
	}

	@Transactional(readOnly = true)
	public List<String> marcas() {
		return productoRepository.marcasDelCatalogo();
	}

	// Los carruseles de la portada y «Más de…» del detalle: la misma regla y el mismo orden
	private List<Producto> vitrina(Long idCategoria, boolean soloDestacados, int cantidad) {
		return productoRepository
				.vitrina(idCategoria, soloDestacados, PageRequest.of(0, cantidad, OrdenCatalogo.RECIENTES.orden()));
	}

	private static List<ProductoTarjetaResponse> tarjetas(List<Producto> productos, Map<Long, PrecioDeVenta> precios) {
		return productos.stream()
				.map(producto -> ProductoTarjetaResponse.from(producto, precios.get(producto.getIdProducto()))).toList();
	}

	// HU-09 RF-13, RF-14: lo que la pantalla ya descarta, la API lo rechaza
	private static void validar(Filtros filtros) {
		List<CampoInvalido> errores = new ArrayList<>();
		if (filtros.texto() != null && filtros.texto().strip().length() > BUSQUEDA_MAXIMA) {
			errores.add(new CampoInvalido("q", "admite hasta " + BUSQUEDA_MAXIMA + " caracteres"));
		}
		validarPrecio("precioMin", filtros.precioMin(), errores);
		validarPrecio("precioMax", filtros.precioMax(), errores);
		if (errores.isEmpty() && filtros.precioMin() != null && filtros.precioMax() != null
				&& filtros.precioMin().compareTo(filtros.precioMax()) > 0) {
			errores.add(new CampoInvalido("precioMax", "tiene que ser mayor o igual que el mínimo"));
		}
		if (!errores.isEmpty()) {
			throw new CamposInvalidosException(errores);
		}
	}

	private static void validarPrecio(String campo, BigDecimal precio, List<CampoInvalido> errores) {
		if (precio == null) {
			return;
		}
		if (precio.signum() < 0 || precio.compareTo(PRECIO_MAXIMO) > 0) {
			errores.add(new CampoInvalido(campo, "tiene que estar entre 0 y 99 999,99"));
		} else if (precio.stripTrailingZeros().scale() > 2) {
			errores.add(new CampoInvalido(campo, "admite hasta dos decimales"));
		}
	}
}
