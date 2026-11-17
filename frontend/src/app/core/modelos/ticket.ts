import { fechaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';
import type { DatosDelTicket } from '../../shared/ticket/ticket';
import type { LineaDePedido } from './pedido';
import { unidadPara } from './producto';
import { MedioPago, NOMBRES_DE_CANAL, NOMBRES_DE_MEDIO } from './venta';

// Lo que comparten el comprobante del mostrador (HU-23 RF-15) y el del Cliente (HU-14 RF-10), escrito una sola vez
// para que los dos tickets digan lo mismo

// «17/11/2026 10:30», en la hora que manda el servidor
export function fechaYHora(iso: string): string {
	return `${fechaCorta(iso)} ${iso.slice(11, 16)}`;
}

// Cada producto con su cantidad, su unidad y su precio, y el importe
export function lineasDelTicket(lineas: LineaDePedido[]): DatosDelTicket['lineas'] {
	return lineas.map((linea) => ({
		nombre: linea.nombre,
		detalle: `${cantidad(linea.cantidad)} ${unidadPara(linea.unidad, linea.cantidad)} × ${precio(linea.precio)}`,
		importe: precio(linea.subtotal),
	}));
}

// El medio de pago y, en efectivo, lo recibido y el cambio. Sin medio, «En línea»: la tienda no sabe con qué se pagó
// en el sistema de pago (HU-14 RF-10)
export function pagosDelTicket(
	medio: MedioPago | null,
	recibido: number | null,
	cambio: number | null,
): DatosDelTicket['pagos'] {
	const pagos = [{ etiqueta: 'Pago', valor: medio === null ? NOMBRES_DE_CANAL.EN_LINEA : NOMBRES_DE_MEDIO[medio] }];
	if (recibido !== null) {
		pagos.push({ etiqueta: 'Recibido', valor: precio(recibido) });
	}
	if (cambio !== null) {
		pagos.push({ etiqueta: 'Cambio', valor: precio(cambio) });
	}
	return pagos;
}
