import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { PedidosService } from '../../core/api/pedidos.service';
import { MARCA } from '../../core/marca';
import { comoErrorApi } from '../../core/modelos/error-api';
import { ahorroDelTicket, fechaYHora, lineasDelTicket, pagosDelTicket } from '../../core/modelos/ticket';
import { precio } from '../../shared/formato/precios';
import { DatosDelTicket, Ticket } from '../../shared/ticket/ticket';

// El comprobante de una compra del Cliente (HU-14 RF-10 a RF-12), en línea o en el mostrador: el mismo ticket del
// mostrador, que se imprime o se guarda como PDF con el navegador
@Component({
	selector: 'app-comprobante-cliente',
	imports: [RouterLink, Ticket],
	templateUrl: './comprobante-cliente.page.html',
	styleUrl: './comprobante-cliente.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComprobanteClientePage {
	private readonly pedidos = inject(PedidosService);

	// Solo dígitos: lo demás es una dirección que no sirve (casos límite)
	protected readonly id = toSignal(
		inject(ActivatedRoute).paramMap.pipe(map((parametros) => idDe(parametros.get('id')))),
		{ requireSync: true },
	);
	protected readonly recurso = this.pedidos.comprobante(this.id);

	// RF-12: el de otro cliente, el que no existe y el que no fue una compra se ven igual
	protected readonly sinComprobante = computed(() => {
		if (this.id() === null) {
			return true;
		}
		const error = this.recurso.error();
		return error !== undefined && comoErrorApi(error).status === 404;
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.sinComprobante() ? '' : mensajeDeError(comoErrorApi(error));
	});

	// RF-10: sin chip (los dos estados son una compra hecha); «Atendió» solo en el mostrador
	protected readonly ticket = computed((): DatosDelTicket | null => {
		const comprobante = this.recurso.hasValue() ? this.recurso.value() : undefined;
		if (comprobante === undefined) {
			return null;
		}
		const datos = [{ etiqueta: 'Cliente', valor: comprobante.cliente }];
		if (comprobante.vendidoPor !== null) {
			datos.push({ etiqueta: 'Atendió', valor: comprobante.vendidoPor });
		}
		return {
			marca: MARCA.nombre,
			titulo: `Pedido N.º\u00a0${comprobante.idPedido}`,
			fecha: fechaYHora(comprobante.fecha),
			estado: null,
			datos,
			lineas: lineasDelTicket(comprobante.lineas),
			total: precio(comprobante.total),
			ahorro: ahorroDelTicket(comprobante.lineas),
			pagos: pagosDelTicket(comprobante.medio, comprobante.recibido, comprobante.cambio),
			anulacion: null,
		};
	});

	// RF-11: el navegador imprime solo el ticket, o lo guarda como PDF
	protected imprimir(): void {
		window.print();
	}
}

function idDe(texto: string | null): number | null {
	return texto !== null && /^\d{1,15}$/.test(texto) ? Number(texto) : null;
}
