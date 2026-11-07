import { DOCUMENT, inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { MARCA } from '../marca';

// Al navegar, el título de la ruta y la descripción que muestran los
// buscadores: la de la ruta (`data.descripcion`, la de su sección si no
// tiene) o la general. El detalle del producto pone después la suya. Las
// tarjetas de WhatsApp o Facebook no la ven: no ejecutan JavaScript y leen la
// de index.html (docs/decisiones.md → «Buscadores y enlaces compartidos»).
// También la dirección canónica, el noindex de las rutas con
// `data.indexar: false` y, al salir del detalle, sus datos estructurados
// (plan 2026-11-07-seo)
@Injectable({ providedIn: 'root' })
export class TituloYDescripcion extends TitleStrategy {
	private readonly titulo = inject(Title);
	private readonly meta = inject(Meta);
	private readonly documento = inject(DOCUMENT);

	override updateTitle(estado: RouterStateSnapshot): void {
		const titulo = this.buildTitle(estado);
		if (titulo !== undefined) {
			this.titulo.setTitle(titulo);
		}
		ponerDescripcion(this.meta, descripcionDe(estado.root) ?? MARCA.descripcion);
		ponerCanonica(this.documento, estado.url);
		indexar(this.meta, seIndexa(estado.root));
		ponerDatosEstructurados(this.documento, null);
	}
}

// Sin la consulta ni el fragmento: los filtros y las páginas del catálogo no
// cuentan como otras páginas. El origen es el de la página que se ve
export function ponerCanonica(documento: Document, url: string): void {
	const camino = url.split(/[?#]/)[0] || '/';
	let enlace = documento.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
	if (enlace === null) {
		enlace = documento.createElement('link');
		enlace.rel = 'canonical';
		documento.head.appendChild(enlace);
	}
	enlace.href = `${documento.location.origin}${camino}`;
}

// «Página no encontrada» y el producto que no existe responden 200, porque las
// arma el navegador: con noindex, Google no las toma por páginas de la tienda
export function indexar(meta: Meta, si: boolean): void {
	if (si) {
		meta.removeTag('name="robots"');
	} else {
		meta.updateTag({ name: 'robots', content: 'noindex' });
	}
}

// Los datos estructurados (schema.org) de la página, o null para sacarlos.
// Google los lee aunque los ponga JavaScript. Con textContent el texto nunca se
// lee como HTML, y la política de contenido no los bloquea: no se ejecutan
export function ponerDatosEstructurados(documento: Document, datos: object | null): void {
	documento.head.querySelector('script[type="application/ld+json"]')?.remove();
	if (datos !== null) {
		const bloque = documento.createElement('script');
		bloque.type = 'application/ld+json';
		bloque.textContent = JSON.stringify({ '@context': 'https://schema.org', ...datos });
		documento.head.appendChild(bloque);
	}
}

// Una ruta con `data.indexar: false`, en cualquier nivel, no se indexa
function seIndexa(raiz: ActivatedRouteSnapshot): boolean {
	for (let ruta: ActivatedRouteSnapshot | null = raiz; ruta !== null; ruta = ruta.firstChild) {
		if (ruta.data['indexar'] === false) {
			return false;
		}
	}
	return true;
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
