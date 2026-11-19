import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { CambioDeDescuento, Descuento, DescuentoGuardado, SolicitudDeDescuento } from '../modelos/descuento';
import { Pagina } from '../modelos/pagina';
import { escribir } from './escribir';

const GESTION = '/api/gestion/descuentos';

// Llamadas a /api/gestion/descuentos (HU-24 RF-1 a RF-6). Solo el Administrador llega: el backend lo valida (RF-7)
@Injectable({ providedIn: 'root' })
export class DescuentosService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la pantalla
	listar(pagina: Signal<number>): HttpResourceRef<Pagina<Descuento> | undefined> {
		return httpResource<Pagina<Descuento>>(() => ({
			url: GESTION,
			params: pagina() > 0 ? { pagina: String(pagina()) } : ({} as Record<string, string>),
		}));
	}

	crear(solicitud: SolicitudDeDescuento): Promise<DescuentoGuardado> {
		return escribir(this.http.post<DescuentoGuardado>(GESTION, solicitud));
	}

	cambiar(idDescuento: number, cambio: CambioDeDescuento): Promise<DescuentoGuardado> {
		return escribir(this.http.put<DescuentoGuardado>(`${GESTION}/${idDescuento}`, cambio));
	}

	async borrar(idDescuento: number): Promise<void> {
		await escribir(this.http.delete<void>(`${GESTION}/${idDescuento}`));
	}

	async terminar(idDescuento: number): Promise<void> {
		await escribir(this.http.post<void>(`${GESTION}/${idDescuento}/terminar`, null));
	}
}
