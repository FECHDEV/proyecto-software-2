package bo.edu.uagrm.tienda.dto;

import bo.edu.uagrm.tienda.entity.Categoria;

// Lo que ve el Administrador: también las ocultas, y cuántos productos tiene cada una (0 hasta HU-07)
public record CategoriaGestionResponse(Long idCategoria, String nombre, String descripcion, boolean tieneImagen,
		boolean visible, long productos) {

	public static CategoriaGestionResponse from(Categoria categoria, long productos) {
		return new CategoriaGestionResponse(categoria.getIdCategoria(), categoria.getNombre(),
				categoria.getDescripcion(), categoria.getImagen() != null, categoria.isVisible(), productos);
	}
}
