// Espejo de CategoriaGestionResponse (gestión) y CategoriaRequest del backend
export interface CategoriaGestion {
	idCategoria: number;
	nombre: string;
	descripcion: string | null;
	tieneImagen: boolean;
	visible: boolean;
	// Cuántos productos tiene: 0 hasta HU-07
	productos: number;
}

export interface DatosDeCategoria {
	nombre: string;
	descripcion: string;
}
