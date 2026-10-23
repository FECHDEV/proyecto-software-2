import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { urlDeImagen, urlDeModelo } from '../modelos/catalogo';
import { Pagina } from '../modelos/pagina';
import {
	Caracteristica,
	DatosDeProducto,
	FiltrosDeProductos,
	Producto,
	ProductoResumen,
	UnidadVenta,
} from '../modelos/producto';
import { leerPrecio } from '../../shared/formato/precios';
import { escribir } from './escribir';

const GESTION = '/api/gestion/productos';

// Lo que recibe ProductoRequest
interface SolicitudDeProducto {
	nombre: string;
	marca: string | null;
	descripcion: string | null;
	idCategoria: number;
	unidad: UnidadVenta;
	precio: number | null;
	costo: number | null;
	ficha: Caracteristica[];
	destacado: boolean;
}

// Llamadas a /api/gestion/productos (HU-07). Solo el administrador llega: el
// backend lo valida en cada petición.
@Injectable({ providedIn: 'root' })
export class ProductosService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la
	// pantalla. Cada cambio de filtros cancela la petición anterior y pide otra.
	listado(filtros: Signal<FiltrosDeProductos>): HttpResourceRef<Pagina<ProductoResumen> | undefined> {
		return httpResource<Pagina<ProductoResumen>>(() => ({ url: GESTION, params: parametros(filtros()) }));
	}

	// Sin id (un producto nuevo) no pide nada
	producto(idProducto: Signal<number | null>): HttpResourceRef<Producto | undefined> {
		return httpResource<Producto>(() => {
			const id = idProducto();
			return id === null ? undefined : `${GESTION}/${id}`;
		});
	}

	crear(datos: DatosDeProducto): Promise<Producto> {
		return escribir(this.http.post<Producto>(GESTION, solicitud(datos)));
	}

	editar(idProducto: number, datos: DatosDeProducto): Promise<Producto> {
		return escribir(this.http.put<Producto>(`${GESTION}/${idProducto}`, solicitud(datos)));
	}

	cambiarActivo(idProducto: number, activo: boolean): Promise<Producto> {
		return escribir(this.http.put<Producto>(`${GESTION}/${idProducto}/activo`, { activo }));
	}

	agregarImagen(idProducto: number, archivo: File): Promise<Producto> {
		const cuerpo = new FormData();
		cuerpo.append('imagen', archivo);
		return escribir(this.http.post<Producto>(`${GESTION}/${idProducto}/imagenes`, cuerpo));
	}

	// Todas las imágenes en el orden nuevo: la primera pasa a ser la principal
	reordenar(idProducto: number, imagenes: readonly string[]): Promise<Producto> {
		return escribir(this.http.put<Producto>(`${GESTION}/${idProducto}/imagenes`, { imagenes }));
	}

	quitarImagen(idProducto: number, imagen: string): Promise<Producto> {
		return escribir(
			this.http.delete<Producto>(`${GESTION}/${idProducto}/imagenes/${encodeURIComponent(imagen)}`),
		);
	}

	// La imagen se sirve sin sesión, como la verá el catálogo
	urlDeImagen(idProducto: number, imagen: string): string {
		return urlDeImagen(idProducto, imagen);
	}

	// HU-15: subir el modelo reemplaza el que hubiera
	subirModelo(idProducto: number, archivo: File): Promise<Producto> {
		const cuerpo = new FormData();
		cuerpo.append('modelo', archivo);
		return escribir(this.http.put<Producto>(`${GESTION}/${idProducto}/modelo`, cuerpo));
	}

	quitarModelo(idProducto: number): Promise<Producto> {
		return escribir(this.http.delete<Producto>(`${GESTION}/${idProducto}/modelo`));
	}

	// Sin sesión, como las imágenes; la regla vive en el catálogo (HU-16)
	urlDeModelo(idProducto: number, archivo: string): string {
		return urlDeModelo(idProducto, archivo);
	}
}

// El backend vuelve a recortar y validar; acá se manda limpio: sin textos
// opcionales vacíos, el precio y el costo como números y sin pares vacíos en la ficha
function solicitud(datos: DatosDeProducto): SolicitudDeProducto {
	return {
		nombre: datos.nombre.trim(),
		marca: opcional(datos.marca),
		descripcion: opcional(datos.descripcion),
		idCategoria: Number(datos.idCategoria),
		unidad: datos.unidad,
		precio: leerPrecio(datos.precio),
		costo: datos.costo.trim() === '' ? null : leerPrecio(datos.costo),
		ficha: datos.ficha
			.map((par) => ({ nombre: par.nombre.trim(), valor: par.valor.trim() }))
			.filter((par) => par.nombre !== '' || par.valor !== ''),
		destacado: datos.destacado,
	};
}

function opcional(texto: string): string | null {
	const limpio = texto.trim();
	return limpio === '' ? null : limpio;
}

// Solo viaja lo que filtra: sin categoría ni estado son todos, un texto de solo
// espacios no filtra y la página 0 es la que el backend usa por defecto
function parametros(filtros: FiltrosDeProductos): Record<string, string> {
	const resultado: Record<string, string> = {};
	if (filtros.categoria !== null) {
		resultado['categoria'] = String(filtros.categoria);
	}
	if (filtros.activo !== null) {
		resultado['activo'] = String(filtros.activo);
	}
	const texto = filtros.texto.trim();
	if (texto !== '') {
		resultado['texto'] = texto;
	}
	if (filtros.pagina > 0) {
		resultado['pagina'] = String(filtros.pagina);
	}
	return resultado;
}
