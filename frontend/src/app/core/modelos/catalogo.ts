import { DatosDeTarjeta } from '../../shared/tarjeta-producto/tarjeta-producto';
import { Caracteristica, UnidadVenta } from './producto';

// Espejo de ProductoTarjetaResponse, PortadaResponse y de los parámetros de
// GET /api/productos (HU-09). La API nunca dice cuánto stock hay: solo si hay.
// conModelo, para la marca «3D» de la tarjeta (HU-16)

export type OrdenCatalogo = 'RECIENTES' | 'PRECIO_ASC' | 'PRECIO_DESC' | 'NOMBRE';

export const ORDENES: readonly OrdenCatalogo[] = ['RECIENTES', 'PRECIO_ASC', 'PRECIO_DESC', 'NOMBRE'];

export const NOMBRES_DE_ORDEN: Record<OrdenCatalogo, string> = {
	RECIENTES: 'Más recientes',
	PRECIO_ASC: 'Precio: menor a mayor',
	PRECIO_DESC: 'Precio: mayor a menor',
	NOMBRE: 'Nombre (A-Z)',
};

// El mismo límite que CatalogoService.BUSQUEDA_MAXIMA
export const BUSQUEDA_MAXIMA = 100;

export interface ProductoEnTienda {
	idProducto: number;
	nombre: string;
	marca: string | null;
	precio: number;
	unidad: UnidadVenta;
	imagenPrincipal: string | null;
	disponible: boolean;
	conModelo: boolean;
}

export interface Seccion {
	idCategoria: number;
	nombre: string;
	productos: ProductoEnTienda[];
}

// Una lista vacía es una sección que no se muestra
export interface Portada {
	destacados: ProductoEnTienda[];
	recientes: ProductoEnTienda[];
	categorias: Seccion[];
}

// null es «todos»; la página empieza en 0, como en el backend
export interface FiltrosDeCatalogo {
	q: string;
	categoria: number | null;
	marca: string | null;
	precioMin: number | null;
	precioMax: number | null;
	disponibles: boolean;
	orden: OrdenCatalogo;
	pagina: number;
}

export const SIN_FILTROS: FiltrosDeCatalogo = {
	q: '',
	categoria: null,
	marca: null,
	precioMin: null,
	precioMax: null,
	disponibles: false,
	orden: 'RECIENTES',
	pagina: 0,
};

// Espejo de ProductoDetalleResponse (HU-10): como la tarjeta, sin la cantidad
// en stock. imagenes son nombres de archivo; la primera es la principal. modelo
// es el nombre del archivo del modelo 3D, o null (HU-16)
export interface ProductoEnDetalle {
	idProducto: number;
	codigo: string;
	nombre: string;
	marca: string | null;
	idCategoria: number;
	categoria: string;
	precio: number;
	unidad: UnidadVenta;
	descripcion: string | null;
	ficha: Caracteristica[];
	imagenes: string[];
	disponible: boolean;
	modelo: string | null;
	relacionados: ProductoEnTienda[];
}


// La única regla para la dirección de una foto: la usan la tienda y la gestión
export function urlDeImagen(idProducto: number, imagen: string): string {
	return `/api/productos/${idProducto}/imagenes/${encodeURIComponent(imagen)}`;
}

// La única regla para la dirección de un modelo 3D (HU-15, HU-16), sin sesión como las fotos
export function urlDeModelo(idProducto: number, archivo: string): string {
	return `/api/productos/${idProducto}/modelo/${encodeURIComponent(archivo)}`;
}

// La ruta del detalle (HU-10)
export function rutaDelProducto(idProducto: number): string {
	return `/producto/${idProducto}`;
}

// Lo que pinta la tarjeta de shared/. «Unidad» no se escribe junto al precio
export function aTarjeta(producto: ProductoEnTienda): DatosDeTarjeta {
	return {
		nombre: producto.nombre,
		marca: producto.marca,
		precio: producto.precio,
		unidad: producto.unidad === 'UNIDAD' ? null : producto.unidad.toLowerCase(),
		imagen: producto.imagenPrincipal === null ? null : urlDeImagen(producto.idProducto, producto.imagenPrincipal),
		agotado: !producto.disponible,
		enlace: rutaDelProducto(producto.idProducto),
		conModelo: producto.conModelo,
	};
}
