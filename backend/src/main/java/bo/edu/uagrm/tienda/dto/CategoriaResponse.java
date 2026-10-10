package bo.edu.uagrm.tienda.dto;

import bo.edu.uagrm.tienda.entity.Categoria;

// Lo que ve el catálogo: si tiene imagen, sin el nombre del archivo (la imagen se pide por /api/categorias/{id}/imagen)
public record CategoriaResponse(Long idCategoria, String nombre, String descripcion, boolean tieneImagen) {

	public static CategoriaResponse from(Categoria categoria) {
		return new CategoriaResponse(categoria.getIdCategoria(), categoria.getNombre(), categoria.getDescripcion(),
				categoria.getImagen() != null);
	}
}
