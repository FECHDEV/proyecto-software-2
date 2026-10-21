import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { Cancelacion, DatosDeConfirmacion, MisPedidos, Pedido, ResultadoSimulado, Retiro } from '../modelos/pedido';
import { CarritoService } from './carrito.service';
import { escribir } from './escribir';

const PEDIDOS = '/api/pedidos';

// Los pedidos del Cliente (HU-12). Confirmar y cancelar cambian el carrito
// (se vacía, o le vuelven las líneas): después se pide otra vez, así el
// encabezado y el aviso del pedido pendiente quedan al día
@Injectable({ providedIn: 'root' })
export class PedidosService {
	private readonly http = inject(HttpClient);
	private readonly carrito = inject(CarritoService);

	// httpResource necesita un contexto de inyección: se llama al construir la pantalla
	retiro(): HttpResourceRef<Retiro | undefined> {
		return httpResource<Retiro>(() => `${PEDIDOS}/retiro`);
	}

	// «Mis pedidos» (HU-14): la página pedida; el backend acomoda una fuera de rango
	misPedidos(pagina: Signal<number>): HttpResourceRef<MisPedidos | undefined> {
		return httpResource<MisPedidos>(() => ({ url: PEDIDOS, params: { pagina: pagina() } }));
	}

	// Sin id válido no pide nada
	pedido(idPedido: Signal<number | null>): HttpResourceRef<Pedido | undefined> {
		return httpResource<Pedido>(() => {
			const id = idPedido();
			return id === null ? undefined : `${PEDIDOS}/${id}`;
		});
	}

	// El backend vuelve a validar y a recortar: acá se manda limpio, y la nota
	// vacía viaja como null
	async confirmar(datos: DatosDeConfirmacion): Promise<Pedido> {
		const nota = datos.nota.trim();
		const pedido = await escribir(
			this.http.post<Pedido>(PEDIDOS, {
				nombreRetira: datos.nombreRetira.trim(),
				telefonoRetira: datos.telefonoRetira.trim(),
				nota: nota === '' ? null : nota,
				totalEsperado: datos.totalEsperado,
			}),
		);
		this.carrito.carrito.reload();
		return pedido;
	}

	async cancelar(idPedido: number): Promise<Cancelacion> {
		const cancelacion = await escribir(this.http.post<Cancelacion>(`${PEDIDOS}/${idPedido}/cancelacion`, null));
		this.carrito.carrito.reload();
		return cancelacion;
	}

	// Genera el cobro, o devuelve el que ya tiene (HU-13 RF-1, RF-2)
	pagar(idPedido: number): Promise<Pedido> {
		return escribir(this.http.post<Pedido>(`${PEDIDOS}/${idPedido}/cobro`, null));
	}

	// El servidor pregunta al sistema de pago y responde cómo quedó (HU-13 RF-4).
	// Si cambió el pedido, la página del pedido pide el carrito de nuevo
	consultarPago(idPedido: number): Promise<Pedido> {
		return escribir(this.http.post<Pedido>(`${PEDIDOS}/${idPedido}/cobro/consulta`, null));
	}

	// Solo con el simulador (HU-13 RF-12)
	async simular(idPedido: number, resultado: ResultadoSimulado): Promise<void> {
		await escribir(this.http.post<void>(`${PEDIDOS}/${idPedido}/cobro/simulacion`, { resultado }));
	}
}
