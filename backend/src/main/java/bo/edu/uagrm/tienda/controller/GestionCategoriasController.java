package bo.edu.uagrm.tienda.controller;

import java.io.IOException;
import java.util.List;

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

import bo.edu.uagrm.tienda.dto.CategoriaGestionResponse;
import bo.edu.uagrm.tienda.dto.CategoriaRequest;
import bo.edu.uagrm.tienda.dto.VisibilidadRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo el Administrador llega acá (SecurityConfig → /api/gestion/**). Los productos de cada categoría se cuentan
// desde HU-07; hasta entonces, 0
@RestController
@RequestMapping("/api/gestion/categorias")
@RequiredArgsConstructor
public class GestionCategoriasController {

	private final CategoriaService categoriaService;

	@GetMapping
	public List<CategoriaGestionResponse> listar() {
		return categoriaService.todas().stream().map(GestionCategoriasController::respuesta).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoriaGestionResponse crear(@Valid @RequestBody CategoriaRequest solicitud) {
		return respuesta(categoriaService.crear(solicitud));
	}

	@PutMapping("/{idCategoria}")
	public CategoriaGestionResponse editar(@PathVariable Long idCategoria,
			@Valid @RequestBody CategoriaRequest solicitud) {
		return respuesta(categoriaService.editar(idCategoria, solicitud));
	}

	@PutMapping("/{idCategoria}/visible")
	public CategoriaGestionResponse cambiarVisibilidad(@PathVariable Long idCategoria,
			@Valid @RequestBody VisibilidadRequest solicitud) {
		return respuesta(categoriaService.cambiarVisibilidad(idCategoria, solicitud.visible()));
	}

	@PostMapping("/{idCategoria}/imagen")
	public CategoriaGestionResponse cambiarImagen(@PathVariable Long idCategoria,
			@RequestParam("imagen") MultipartFile imagen) throws IOException {
		return respuesta(categoriaService.cambiarImagen(idCategoria, imagen.getBytes()));
	}

	@DeleteMapping("/{idCategoria}/imagen")
	public CategoriaGestionResponse quitarImagen(@PathVariable Long idCategoria) {
		return respuesta(categoriaService.quitarImagen(idCategoria));
	}

	@DeleteMapping("/{idCategoria}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrar(@PathVariable Long idCategoria) {
		categoriaService.borrar(idCategoria);
	}

	private static CategoriaGestionResponse respuesta(Categoria categoria) {
		return CategoriaGestionResponse.from(categoria, 0);
	}
}
