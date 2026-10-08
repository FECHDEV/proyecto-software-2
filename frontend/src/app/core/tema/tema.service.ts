import { DOCUMENT, effect, inject, Injectable, signal } from '@angular/core';

import { guardarTema, leerTema, Tema } from './almacen-tema';

// El color de la barra del navegador en el celular: el fondo de cada modo
// (--app-fondo en _tokens.scss)
const COLOR_DE_LA_BARRA: Record<Tema, string> = { oscuro: '#0b1020', claro: '#f6f7fb' };

// El modo de la interfaz. El oscuro va sin marca en <html> porque es el de
// :root; el claro se activa con data-tema="claro" (styles/_tokens.scss)
@Injectable({ providedIn: 'root' })
export class TemaService {
	private readonly documento = inject(DOCUMENT);
	private readonly actual = signal<Tema>(leerTema());

	readonly tema = this.actual.asReadonly();

	constructor() {
		effect(() => {
			const tema = this.actual();
			const raiz = this.documento.documentElement;
			if (tema === 'claro') {
				raiz.dataset['tema'] = 'claro';
			} else {
				delete raiz.dataset['tema'];
			}
			this.documento.querySelector('meta[name="theme-color"]')?.setAttribute('content', COLOR_DE_LA_BARRA[tema]);
			guardarTema(tema);
		});
	}

	alternar(): void {
		this.actual.update((tema) => (tema === 'oscuro' ? 'claro' : 'oscuro'));
	}
}
