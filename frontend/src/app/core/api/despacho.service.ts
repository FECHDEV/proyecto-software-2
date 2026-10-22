import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { consultaDeDespacho, FichaDePedido, FiltrosDeDespacho, PedidoEnDespacho } from '../modelos/despacho';
import { Pagina } from '../modelos/pagina';
import { escribir } from './escribir';

const DESPACHO = '/api/despacho/pedidos';

// Los pedidos de todos los clientes, para el personal (HU-17). Leen el
// Empleado, el Administrador y el Contador; entregan los dos primeros: el
// backend lo valida en cada petición
@Injectable({ providedIn: 'root' })
export class DespachoService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la pantalla
	lista(filtros: Signal<FiltrosDeDespacho>): HttpResourceRef<Pagina<PedidoEnDespacho> | undefined> {
		return httpResource<Pagina<PedidoEnDespacho>>(() => ({ url: DESPACHO, params: consultaDeDespacho(filtros()) }));
	}

	// Sin id válido no pide nada
	ficha(idPedido: Signal<number | null>): HttpResourceRef<FichaDePedido | undefined> {
		return httpResource<FichaDePedido>(() => {
			const id = idPedido();
			return id === null ? undefined : `${DESPACHO}/${id}`;
		});
	}

	entregar(idPedido: number): Promise<FichaDePedido> {
		return escribir(this.http.post<FichaDePedido>(`${DESPACHO}/${idPedido}/entrega`, null));
	}
}
