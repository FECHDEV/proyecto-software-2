package bo.edu.uagrm.tienda.controller;

import java.io.IOException;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import bo.edu.uagrm.tienda.dto.ActivoRequest;
import bo.edu.uagrm.tienda.dto.OrdenImagenesRequest;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.ProductoRequest;
import bo.edu.uagrm.tienda.dto.ProductoResponse;
import bo.edu.uagrm.tienda.dto.ProductoResumenResponse;
import bo.edu.uagrm.tienda.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo el Administrador llega acá (SecurityConfig → /api/gestion/**). HU-07
@RestController
@RequestMapping("/api/gestion/productos")
@RequiredArgsConstructor
public class GestionProductosController {

	private final ProductoService productoService;

	@GetMapping
	public PaginaResponse<ProductoResumenResponse> listar(@RequestParam(required = false) Long categoria,
			@RequestParam(required = false) Boolean activo, @RequestParam(required = false) String texto,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
		return PaginaResponse.from(productoService.listar(categoria, activo, texto, pagina, tamano), Function.identity());
	}

	@GetMapping("/{idProducto}")
	public ProductoResponse obtener(@PathVariable Long idProducto) {
		return productoService.obtener(idProducto);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoResponse crear(@Valid @RequestBody ProductoRequest solicitud) {
		return productoService.crear(solicitud);
	}

	@PutMapping("/{idProducto}")
	public ProductoResponse editar(@PathVariable Long idProducto, @Valid @RequestBody ProductoRequest solicitud) {
		return productoService.editar(idProducto, solicitud);
	}

	@PutMapping("/{idProducto}/activo")
	public ProductoResponse cambiarActivo(@PathVariable Long idProducto,
			@Valid @RequestBody ActivoRequest solicitud) {
		return productoService.cambiarActivo(idProducto, solicitud.activo());
	}

	@PostMapping("/{idProducto}/imagenes")
	public ProductoResponse agregarImagen(@PathVariable Long idProducto,
			@RequestParam("imagen") MultipartFile imagen) throws IOException {
		return productoService.agregarImagen(idProducto, imagen.getBytes());
	}

	@PutMapping("/{idProducto}/imagenes")
	public ProductoResponse reordenar(@PathVariable Long idProducto,
			@Valid @RequestBody OrdenImagenesRequest solicitud) {
		return productoService.reordenar(idProducto, solicitud.imagenes());
	}

	// HU-15: subir el modelo lo reemplaza si ya había uno
	@PutMapping("/{idProducto}/modelo")
	public ProductoResponse subirModelo(@PathVariable Long idProducto, @RequestParam("modelo") MultipartFile modelo)
			throws IOException {
		return productoService.subirModelo(idProducto, modelo.getBytes());
	}

	@DeleteMapping("/{idProducto}/modelo")
	public ProductoResponse quitarModelo(@PathVariable Long idProducto) {
		return productoService.quitarModelo(idProducto);
	}

	@DeleteMapping("/{idProducto}/imagenes/{imagen}")
	public ProductoResponse quitarImagen(@PathVariable Long idProducto, @PathVariable String imagen) {
		return productoService.quitarImagen(idProducto, imagen);
	}
}
