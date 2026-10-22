import { httpResource, HttpResourceRef } from '@angular/common/http';
import { Injectable, Signal } from '@angular/core';

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

// Los reportes del Contador y el Administrador (HU-18): solo lectura. Cada
// sección de la pantalla pide solo lo suyo; el backend valida el rol en cada
// petición. httpResource necesita un contexto de inyección: se llama al
// construir cada sección
@Injectable({ providedIn: 'root' })
export class ReportesService {
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
