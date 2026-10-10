import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { CategoriaGestion, DatosDeCategoria } from '../modelos/categoria';
import { escribir } from './escribir';

const GESTION = '/api/gestion/categorias';

// Llamadas a /api/gestion/categorias (HU-06). Solo el administrador llega: el
// backend lo valida en cada petición.
@Injectable({ providedIn: 'root' })
export class CategoriasService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la pantalla
	gestion(): HttpResourceRef<CategoriaGestion[] | undefined> {
		return httpResource<CategoriaGestion[]>(() => GESTION);
	}

	crear(datos: DatosDeCategoria): Promise<CategoriaGestion> {
		return escribir(this.http.post<CategoriaGestion>(GESTION, limpio(datos)));
	}

	editar(idCategoria: number, datos: DatosDeCategoria): Promise<CategoriaGestion> {
		return escribir(this.http.put<CategoriaGestion>(`${GESTION}/${idCategoria}`, limpio(datos)));
	}

	cambiarVisibilidad(idCategoria: number, visible: boolean): Promise<CategoriaGestion> {
		return escribir(this.http.put<CategoriaGestion>(`${GESTION}/${idCategoria}/visible`, { visible }));
	}

	cambiarImagen(idCategoria: number, archivo: File): Promise<CategoriaGestion> {
		const cuerpo = new FormData();
		cuerpo.append('imagen', archivo);
		return escribir(this.http.post<CategoriaGestion>(`${GESTION}/${idCategoria}/imagen`, cuerpo));
	}

	quitarImagen(idCategoria: number): Promise<CategoriaGestion> {
		return escribir(this.http.delete<CategoriaGestion>(`${GESTION}/${idCategoria}/imagen`));
	}

	async borrar(idCategoria: number): Promise<void> {
		await escribir(this.http.delete<void>(`${GESTION}/${idCategoria}`));
	}

	// La imagen se sirve sin sesión, como la verá el catálogo
	urlDeImagen(idCategoria: number): string {
		return `/api/categorias/${idCategoria}/imagen`;
	}
}

// El backend vuelve a recortar y validar; acá se manda limpio y sin descripción vacía
function limpio(datos: DatosDeCategoria): { nombre: string; descripcion: string | null } {
	const descripcion = datos.descripcion.trim();
	return { nombre: datos.nombre.trim(), descripcion: descripcion === '' ? null : descripcion };
}
