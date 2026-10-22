// Espejo de ProductoResponse, ProductoResumenResponse y ProductoRequest del backend (HU-07)

export type UnidadVenta = 'UNIDAD' | 'METRO' | 'KILO' | 'LITRO' | 'CAJA' | 'PAR' | 'ROLLO';

export const UNIDADES: readonly UnidadVenta[] = ['UNIDAD', 'METRO', 'KILO', 'LITRO', 'CAJA', 'PAR', 'ROLLO'];

// Cómo se nombran en pantalla; en el código siguen siendo los del backend
export const NOMBRES_DE_UNIDAD: Record<UnidadVenta, string> = {
	UNIDAD: 'Unidad',
	METRO: 'Metro',
	KILO: 'Kilo',
	LITRO: 'Litro',
	CAJA: 'Caja',
	PAR: 'Par',
	ROLLO: 'Rollo',
};

// Los mismos límites que ProductoService y ProductoRequest
export const MAXIMO_IMAGENES = 5;
export const MAXIMO_CARACTERISTICAS = 15;

export interface Caracteristica {
	nombre: string;
	valor: string;
}

export interface Producto {
	idProducto: number;
	codigo: string;
	nombre: string;
	marca: string | null;
	descripcion: string | null;
	precio: number;
	// Solo en la gestión, para el margen de los reportes (HU-18 RF-19)
	costo: number | null;
	unidad: UnidadVenta;
	idCategoria: number;
	categoria: string;
	activo: boolean;
	fechaAlta: string;
	ficha: Caracteristica[];
	// Nombres de archivo en orden: la primera es la principal
	imagenes: string[];
	// Sale en «Destacados» de la portada (HU-09 RF-4)
	destacado: boolean;
}

// Una fila de la lista de la gestión
export interface ProductoResumen {
	idProducto: number;
	codigo: string;
	nombre: string;
	marca: string | null;
	categoria: string;
	precio: number;
	unidad: UnidadVenta;
	activo: boolean;
	imagenPrincipal: string | null;
}

// Los filtros de la lista. null en categoría o estado es "todas"; la página
// empieza en 0, como en el backend.
export interface FiltrosDeProductos {
	categoria: number | null;
	activo: boolean | null;
	texto: string;
	pagina: number;
}

// Lo que edita el formulario. La categoría es el valor del select y el precio
// y el costo, el texto escrito, que acepta coma o punto: se convierten al enviar.
export interface DatosDeProducto {
	nombre: string;
	marca: string;
	descripcion: string;
	idCategoria: string;
	unidad: UnidadVenta;
	precio: string;
	// Vacío es sin costo
	costo: string;
	ficha: Caracteristica[];
	destacado: boolean;
}
