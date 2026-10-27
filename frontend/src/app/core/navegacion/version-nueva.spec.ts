import { TestBed } from '@angular/core/testing';
import { NavigationError, provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { CARGAR_PAGINA, provideVersionPublicada, recargarSiFaltaElFragmento, VersionPublicada } from './version-nueva';

// Revisión total (26/10/2026): una pestaña abierta antes de publicar una versión nueva
describe('VersionPublicada', () => {
	const falta = (url: string) =>
		new NavigationError(1, url, new TypeError('Failed to fetch dynamically imported module: /chunk-ABC.js'));

	function preparar() {
		const cargar = vi.fn();
		TestBed.configureTestingModule({
			providers: [
				provideRouter([{ path: 'carrito', children: [] }]),
				provideVersionPublicada(),
				{ provide: CARGAR_PAGINA, useValue: cargar },
			],
		});
		return cargar;
	}

	const alFallar = (error: NavigationError) =>
		TestBed.runInInjectionContext(() => recargarSiFaltaElFragmento(error));

	beforeEach(() => sessionStorage.clear());
	afterEach(() => vi.restoreAllMocks());

	it('carga la página de destino entera si falta el fragmento', () => {
		const cargar = preparar();

		alFallar(falta('/carrito'));

		expect(cargar).toHaveBeenCalledWith('/carrito');
	});

	it('con cualquier otro error no recarga', () => {
		const cargar = preparar();

		alFallar(new NavigationError(1, '/carrito', new Error('Algo más')));

		expect(cargar).not.toHaveBeenCalled();
	});

	// /code-review: un fragmento roto de verdad no deja la página recargando sin fin, y la página que también la
	// pide al fallar la navegación no la carga dos veces
	it('una dirección se carga una sola vez hasta que una navegación termina bien', async () => {
		const cargar = preparar();
		const version = TestBed.inject(VersionPublicada);

		alFallar(falta('/carrito'));
		version.cargar('/carrito');
		alFallar(falta('/mis-pedidos'));
		expect(cargar.mock.calls).toEqual([['/carrito'], ['/mis-pedidos']]);

		// La próxima publicación, con la pestaña ya funcionando, vuelve a poder recargar
		await TestBed.inject(Router).navigateByUrl('/carrito');
		alFallar(falta('/mis-pedidos'));
		expect(cargar).toHaveBeenCalledTimes(3);
	});

	// /code-review: sin red sale el mismo mensaje, y recargar perdería la página
	it('sin conexión no recarga', () => {
		const cargar = preparar();
		vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);

		alFallar(falta('/carrito'));
		TestBed.inject(VersionPublicada).cargar('/carrito');

		expect(cargar).not.toHaveBeenCalled();
	});
});
