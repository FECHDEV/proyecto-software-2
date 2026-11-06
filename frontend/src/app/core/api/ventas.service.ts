import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { ResultadoSimulado } from '../modelos/pedido';
import { Comprador, ProductoParaVenta, SolicitudDeVenta, Venta } from '../modelos/venta';
import { escribir } from './escribir';

const VENTAS = '/api/ventas';

// La venta del mostrador (HU-23). Leen el Empleado, el Administrador y el
// Contador; registran, consultan y cancelan los dos primeros; anula solo el
// Administrador: el backend lo valida en cada petición
@Injectable({ providedIn: 'root' })
export class VentasService {
	private readonly http = inject(HttpClient);

	// RF-1, RF-3: sin texto no pide nada. httpResource necesita un contexto de
	// inyección: se llama al construir la pantalla
	productos(texto: Signal<string>): HttpResourceRef<ProductoParaVenta[] | undefined> {
		return httpResource<ProductoParaVenta[]>(() => {
			const buscado = texto().trim();
			return buscado === '' ? undefined : { url: `${VENTAS}/productos`, params: { texto: buscado } };
		});
	}

	// RF-15: el comprobante. Sin id válido no pide nada
	venta(idPedido: Signal<number | null>): HttpResourceRef<Venta | undefined> {
		return httpResource<Venta>(() => {
			const id = idPedido();
			return id === null ? undefined : `${VENTAS}/${id}`;
		});
	}

	// RF-4: una acción de quien vende, no una lectura que se repite: con HttpClient.
	// 404 si no es una cuenta de Cliente activa
	buscarCliente(correo: string): Promise<Comprador> {
		return escribir(this.http.get<Comprador>(`${VENTAS}/clientes`, { params: { correo: correo.trim() } }));
	}

	registrar(solicitud: SolicitudDeVenta): Promise<Venta> {
		return escribir(this.http.post<Venta>(VENTAS, solicitud));
	}

	// RF-8: el servidor pregunta al sistema de pago y responde cómo quedó
	consultar(idPedido: number): Promise<Venta> {
		return escribir(this.http.post<Venta>(`${VENTAS}/${idPedido}/consulta`, null));
	}

	cancelar(idPedido: number): Promise<Venta> {
		return escribir(this.http.post<Venta>(`${VENTAS}/${idPedido}/cancelacion`, null));
	}

	// RF-18, RF-19: el motivo va sin los espacios de alrededor
	anular(idPedido: number, motivo: string): Promise<Venta> {
		return escribir(this.http.post<Venta>(`${VENTAS}/${idPedido}/anulacion`, { motivo: motivo.trim() }));
	}

	// RF-14: el precio de hoy de un producto, para volver a mostrar la venta si cambió mientras se armaba
	async precioDeHoy(idProducto: number): Promise<number> {
		const producto = await escribir(this.http.get<{ precio: number }>(`/api/productos/${idProducto}`));
		return producto.precio;
	}

	// Solo con el simulador, como en el pago en línea (HU-13 RF-12)
	async simular(idPedido: number, resultado: ResultadoSimulado): Promise<void> {
		await escribir(this.http.post<void>(`${VENTAS}/${idPedido}/simulacion`, { resultado }));
	}
}
