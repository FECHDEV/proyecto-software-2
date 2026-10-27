import { DOCUMENT, EnvironmentProviders, inject, Injectable, InjectionToken, provideAppInitializer } from '@angular/core';
import { NavigationEnd, NavigationError, Router } from '@angular/router';

// Carga una dirección como página entera. Un token, para las pruebas
export const CARGAR_PAGINA = new InjectionToken<(url: string) => void>('CARGAR_PAGINA', {
	providedIn: 'root',
	factory: () => {
		const documento = inject(DOCUMENT);
		return (url) => documento.location.assign(url);
	},
});

// Lo que dicen los navegadores cuando no encuentran el fragmento de una pantalla: Chrome y Edge, Firefox y Safari
const FRAGMENTO_QUE_FALTA = [
	'Failed to fetch dynamically imported module',
	'error loading dynamically imported module',
	'Importing a module script failed',
];

// La dirección que ya se cargó entera en esta pestaña sin que después se pudiera navegar
const YA_CARGADA = 'tienda.recarga-por-version';

// Cuando la navegación no se puede completar, carga la dirección como página entera, que trae la versión publicada
// de la tienda (revisión total, 26/10/2026). Una sola vez por dirección hasta que una navegación termine bien, así un
// fragmento roto de verdad no deja la página recargando sin fin; y sin conexión no, porque recargar perdería la página
@Injectable({ providedIn: 'root' })
export class VersionPublicada {
	private readonly ventana = inject(DOCUMENT).defaultView;
	private readonly cargarPagina = inject(CARGAR_PAGINA);

	cargar(url: string): void {
		if (this.ventana?.navigator.onLine === false) {
			return;
		}
		try {
			if (this.ventana?.sessionStorage.getItem(YA_CARGADA) === url) {
				return;
			}
			this.ventana?.sessionStorage.setItem(YA_CARGADA, url);
		} catch {
			// Sin almacenamiento (navegación privada estricta): se carga igual
		}
		this.cargarPagina(url);
	}

	// Una navegación que termina bien muestra que la versión cargada funciona: la próxima publicación puede recargar
	olvidar(): void {
		try {
			this.ventana?.sessionStorage.removeItem(YA_CARGADA);
		} catch {
			// Sin almacenamiento no hay nada que olvidar
		}
	}
}

// Una pestaña abierta antes de publicar una versión nueva pide los fragmentos viejos, que ya no están (nginx responde
// 404): sin esto el clic no hace nada. Va en withNavigationErrorHandler, así que corre en un contexto de inyección
export function recargarSiFaltaElFragmento(error: NavigationError): void {
	const mensaje = error.error instanceof Error ? error.error.message : String(error.error);
	if (FRAGMENTO_QUE_FALTA.some((texto) => mensaje.includes(texto))) {
		inject(VersionPublicada).cargar(error.url);
	}
}

// Al arrancar: cada navegación que termina bien olvida la última recarga
export function provideVersionPublicada(): EnvironmentProviders {
	return provideAppInitializer(() => {
		const version = inject(VersionPublicada);
		inject(Router).events.subscribe((evento) => {
			if (evento instanceof NavigationEnd) {
				version.olvidar();
			}
		});
	});
}
