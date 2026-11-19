import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// Lo que el ticket muestra, ya escrito por quien lo usa: el ticket no sabe de ventas ni de pedidos
export interface DatosDelTicket {
	marca: string;
	titulo: string;
	fecha: string;
	// El chip del estado; null si no hace falta
	estado: { texto: string; tono: string } | null;
	// Cliente, Atendió…
	datos: { etiqueta: string; valor: string }[];
	lineas: { nombre: string; detalle: string; importe: string }[];
	total: string;
	// «Usted ahorró», bajo el total; null sin descuentos (HU-24 RF-18)
	ahorro: string | null;
	// Pago, Recibido, Cambio…
	pagos: { etiqueta: string; valor: string }[];
	anulacion: string | null;
}

// El ticket de un comprobante (HU-23 RF-15, HU-14 RF-10): en pantalla, una placa de lona como las de la tienda; al
// imprimir, 80 mm en negro sobre blanco, con «No válido como factura» (sin SIAT). Lo demás de la página (botones,
// encabezado) lo esconde styles.scss al imprimir
@Component({
	selector: 'app-ticket',
	templateUrl: './ticket.html',
	styleUrl: './ticket.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Ticket {
	readonly datos = input.required<DatosDelTicket>();
}
