import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { MARCA } from '../marca';

// Al navegar, el título de la ruta y la descripción que muestran los
// buscadores: la de la ruta (`data.descripcion`, la de su sección si no
// tiene) o la general. El detalle del producto pone después la suya. Las
// tarjetas de WhatsApp o Facebook no la ven: no ejecutan JavaScript y leen la
// de index.html (docs/decisiones.md → «Buscadores y enlaces compartidos»)
@Injectable({ providedIn: 'root' })
export class TituloYDescripcion extends TitleStrategy {
	private readonly titulo = inject(Title);
	private readonly meta = inject(Meta);

	override updateTitle(estado: RouterStateSnapshot): void {
		const titulo = this.buildTitle(estado);
		if (titulo !== undefined) {
			this.titulo.setTitle(titulo);
		}
		ponerDescripcion(this.meta, descripcionDe(estado.root) ?? MARCA.descripcion);
	}
}

export function ponerDescripcion(meta: Meta, texto: string): void {
	meta.updateTag({ name: 'description', content: texto });
}

// La de la ruta más profunda que tenga una
function descripcionDe(raiz: ActivatedRouteSnapshot): string | undefined {
	let descripcion: string | undefined;
	for (let ruta: ActivatedRouteSnapshot | null = raiz; ruta !== null; ruta = ruta.firstChild) {
		descripcion = (ruta.data['descripcion'] as string | undefined) ?? descripcion;
	}
	return descripcion;
}

// En una sola línea y sin cortar palabras: Google muestra unos 155 caracteres.
// Vacía si el texto no tiene nada (quien llama pone la general)
export function resumen(texto: string, maximo = 155): string {
	const linea = texto.replace(/\s+/g, ' ').trim();
	if (linea.length <= maximo) {
		return linea;
	}
	const corte = linea.lastIndexOf(' ', maximo - 1);
	// Sin la puntuación que quede antes de los puntos suspensivos («madera,…»)
	return `${linea.slice(0, corte > 0 ? corte : maximo - 1).replace(/[\s.,;:]+$/, '')}…`;
}
