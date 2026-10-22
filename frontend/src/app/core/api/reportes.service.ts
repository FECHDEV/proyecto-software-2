import { HttpClient, HttpErrorResponse, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { comoErrorApi } from '../modelos/error-api';

import { Pagina } from '../modelos/pagina';
import {
	ClienteInactivo,
	FiltrosDeReportes,
	ProductoPerdido,
	ProductoSinVenta,
	ProductoVendido,
	ResumenDePerdidos,
	VentasDelPeriodo,
} from '../modelos/reportes';

const REPORTES = '/api/reportes';

export type FormatoDeExportacion = 'xlsx' | 'pdf';

export interface ArchivoExportado {
	archivo: Blob;
	nombre: string;
}

// Los reportes del Contador y el Administrador (HU-18): solo lectura. Cada
// sección de la pantalla pide solo lo suyo; el backend valida el rol en cada
// petición. httpResource necesita un contexto de inyección: se llama al
// construir cada sección
@Injectable({ providedIn: 'root' })
export class ReportesService {
	private readonly http = inject(HttpClient);

	// HU-19: todo el reporte en un archivo, con el período, el tipo y los días de
	// la pantalla en cualquier sección (RF-2). Es una acción, no una lectura: con
	// HttpClient. Si falla, rechaza con el ErrorApi, como las escrituras
	async exportar(filtros: FiltrosDeReportes, formato: FormatoDeExportacion): Promise<ArchivoExportado> {
		try {
			const respuesta = await firstValueFrom(
				this.http.get(`${REPORTES}/exportar`, {
					params: { formato, ...periodo(filtros), ...tipo(filtros), dias: filtros.dias },
					responseType: 'blob',
					observe: 'response',
				}),
			);
			const nombre = /filename="([^"]+)"/.exec(respuesta.headers.get('Content-Disposition') ?? '')?.[1];
			return {
				archivo: respuesta.body ?? new Blob(),
				// El nombre lo pone el backend (RF-4); sin él, uno genérico, sin repetir su formato
				nombre: nombre ?? `reporte-ventas.${formato}`,
			};
		} catch (error) {
			throw comoErrorApi(await conCuerpoLeido(error));
		}
	}
	ventas(filtros: Signal<FiltrosDeReportes>): HttpResourceRef<VentasDelPeriodo | undefined> {
		return httpResource<VentasDelPeriodo>(() => ({
			url: `${REPORTES}/ventas`,
			params: { ...periodo(filtros()), ...tipo(filtros()) },
		}));
	}

	productos(filtros: Signal<FiltrosDeReportes>): HttpResourceRef<Pagina<ProductoVendido> | undefined> {
		return httpResource<Pagina<ProductoVendido>>(() => {
			const actuales = filtros();
			return {
				url: `${REPORTES}/productos`,
				params: {
					...periodo(actuales),
					...tipo(actuales),
					...(actuales.orden === 'MENOS' ? { orden: 'MENOS' } : {}),
					...pagina(actuales),
				},
			};
		});
	}

	sinVenta(filtros: Signal<FiltrosDeReportes>): HttpResourceRef<Pagina<ProductoSinVenta> | undefined> {
		return httpResource<Pagina<ProductoSinVenta>>(() => ({
			url: `${REPORTES}/sin-venta`,
			params: { dias: filtros().dias, ...pagina(filtros()) },
		}));
	}

	clientesInactivos(filtros: Signal<FiltrosDeReportes>): HttpResourceRef<Pagina<ClienteInactivo> | undefined> {
		return httpResource<Pagina<ClienteInactivo>>(() => ({
			url: `${REPORTES}/clientes-inactivos`,
			params: { dias: filtros().dias, ...tipo(filtros()), ...pagina(filtros()) },
		}));
	}

	resumenDePerdidos(filtros: Signal<FiltrosDeReportes>): HttpResourceRef<ResumenDePerdidos | undefined> {
		return httpResource<ResumenDePerdidos>(() => ({
			url: `${REPORTES}/perdidos/resumen`,
			params: { ...periodo(filtros()), ...tipo(filtros()) },
		}));
	}

	perdidos(filtros: Signal<FiltrosDeReportes>): HttpResourceRef<Pagina<ProductoPerdido> | undefined> {
		return httpResource<Pagina<ProductoPerdido>>(() => ({
			url: `${REPORTES}/perdidos`,
			params: { ...periodo(filtros()), ...tipo(filtros()), ...pagina(filtros()) },
		}));
	}
}

// Con responseType blob, el ProblemDetail del error también llega como Blob: se
// lee para que comoErrorApi encuentre su código (RF-12)
async function conCuerpoLeido(error: unknown): Promise<unknown> {
	if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
		return error;
	}
	try {
		const cuerpo: unknown = JSON.parse(await error.error.text());
		return new HttpErrorResponse({ error: cuerpo, status: error.status, statusText: error.statusText, url: error.url ?? undefined });
	} catch {
		return error;
	}
}

// Las fechas viajan siempre: así el período es el que se ve, aunque el del
// backend por defecto sea el mismo
function periodo(filtros: FiltrosDeReportes): Record<string, string> {
	return { desde: filtros.desde, hasta: filtros.hasta };
}

function tipo(filtros: FiltrosDeReportes): Record<string, string> {
	return filtros.tipo === null ? {} : { tipo: filtros.tipo };
}

function pagina(filtros: FiltrosDeReportes): Record<string, number> {
	return filtros.pagina > 0 ? { pagina: filtros.pagina } : {};
}
