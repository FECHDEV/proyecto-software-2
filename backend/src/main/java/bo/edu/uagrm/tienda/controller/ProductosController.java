package bo.edu.uagrm.tienda.controller;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.OrdenCatalogo;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.PortadaResponse;
import bo.edu.uagrm.tienda.dto.ProductoDetalleResponse;
import bo.edu.uagrm.tienda.dto.ProductoTarjetaResponse;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException.CampoInvalido;
import bo.edu.uagrm.tienda.service.Achicador;
import bo.edu.uagrm.tienda.service.CatalogoService;
import bo.edu.uagrm.tienda.service.FormatoModelo;
import bo.edu.uagrm.tienda.service.ProductoService;
import lombok.RequiredArgsConstructor;

// Lo que lee el catálogo, con o sin sesión (SecurityConfig): la lista, la portada y las marcas (HU-09) y las
// imágenes (HU-07) y el detalle (HU-10)
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductosController {

	private static final CacheControl GUARDAR_UN_ANO = CacheControl.maxAge(Duration.ofDays(365)).cachePublic()
			.immutable();

	private final ProductoService productoService;
	private final CatalogoService catalogoService;

	// Un parámetro que no se convierte (pagina=abc, orden=xyz) ya sale como 400 (HU-09 RF-13)
	@GetMapping
	public PaginaResponse<ProductoTarjetaResponse> listar(@RequestParam(required = false) String q,
			@RequestParam(required = false) Long categoria, @RequestParam(required = false) String marca,
			@RequestParam(required = false) BigDecimal precioMin, @RequestParam(required = false) BigDecimal precioMax,
			@RequestParam(defaultValue = "false") boolean disponibles,
			@RequestParam(defaultValue = "RECIENTES") OrdenCatalogo orden, @RequestParam(defaultValue = "0") int pagina) {
		return catalogoService.listar(
				new CatalogoService.Filtros(q, categoria, marca, precioMin, precioMax, disponibles, orden, pagina));
	}

	@GetMapping("/portada")
	public PortadaResponse portada() {
		return catalogoService.portada();
	}

	@GetMapping("/marcas")
	public List<String> marcas() {
		return catalogoService.marcas();
	}

	// Un id que no es un número sale como 400 (HU-10 RF-9); /portada y /marcas ganan por ser rutas fijas
	@GetMapping("/{idProducto}")
	public ProductoDetalleResponse detalle(@PathVariable Long idProducto) {
		return catalogoService.detalle(idProducto);
	}

	// Como el modelo: el nombre es nuevo con cada subida, así que se guarda un año. Sin esto salía con el no-store
	// que pone Spring Security y el navegador la volvía a bajar en cada visita (plan 2026-10-28)
	// HU-22: con ?ancho=400 o 1200, la versión achicada. Si todavía no está, el original sin caché: con un año, el
	// navegador lo seguiría usando en lugar de la versión cuando ya exista (RF-8)
	@GetMapping("/{idProducto}/imagenes/{imagen}")
	public ResponseEntity<byte[]> imagen(@PathVariable Long idProducto, @PathVariable String imagen,
			@RequestParam(required = false) Integer ancho) {
		if (ancho != null && Arrays.stream(Achicador.ANCHOS).noneMatch(valido -> valido == ancho)) {
			throw new CamposInvalidosException(List.of(new CampoInvalido("ancho", "admite 400 o 1200")));
		}
		return productoService.imagen(idProducto, imagen, ancho)
				.map(guardada -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(guardada.formato().tipoDeContenido()))
						.cacheControl(guardada.servidaComoSePidio() ? GUARDAR_UN_ANO : CacheControl.noCache())
						.body(guardada.contenido()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	// HU-15 RF-8: sin sesión, como las imágenes; solo el modelo actual del producto. El nombre es nuevo con cada
	// subida, así que el mismo nombre es siempre el mismo archivo y el navegador no lo vuelve a bajar (/code-review)
	@GetMapping("/{idProducto}/modelo/{archivo}")
	public ResponseEntity<byte[]> modelo(@PathVariable Long idProducto, @PathVariable String archivo) {
		return productoService.modelo(idProducto, archivo)
				.map(contenido -> ResponseEntity.ok().contentType(MediaType.parseMediaType(FormatoModelo.TIPO_DE_CONTENIDO))
						.cacheControl(GUARDAR_UN_ANO).body(contenido))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
