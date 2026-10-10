package bo.edu.uagrm.tienda.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.CategoriaResponse;
import bo.edu.uagrm.tienda.service.CategoriaService;
import lombok.RequiredArgsConstructor;

// Lo que lee el catálogo, con o sin sesión (SecurityConfig): solo las categorías visibles (HU-06 RF-11)
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriasController {

	private final CategoriaService categoriaService;

	@GetMapping
	public List<CategoriaResponse> listar() {
		return categoriaService.visibles().stream().map(CategoriaResponse::from).toList();
	}

	@GetMapping("/{idCategoria}/imagen")
	public ResponseEntity<byte[]> imagen(@PathVariable Long idCategoria) {
		return categoriaService.imagen(idCategoria)
				.map(imagen -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(imagen.formato().tipoDeContenido()))
						.body(imagen.contenido()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
