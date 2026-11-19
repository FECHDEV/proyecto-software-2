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

// Espacios que no se cortan al final de la línea (U+00A0 y U+202F)
const ESPACIO_DURO = String.fromCharCode(0xa0);
const ESPACIO_FINO_DURO = String.fromCharCode(0x202f);

// Cada producto con su cantidad, su unidad y su precio, y el importe. Con descuento, «(−15 %)» al final (HU-24 RF-18)
export function lineasDelTicket(lineas: LineaDePedido[]): DatosDelTicket['lineas'] {
	return lineas.map((linea) => ({
		nombre: linea.nombre,
		detalle:
			`${cantidad(linea.cantidad)} ${unidadPara(linea.unidad, linea.cantidad)} × ${precio(linea.precio)}` +
			// Sin cortes: al imprimir en 80 mm, «(−53 %)» se partía antes del «%» (revisión de interfaz)
			(linea.descuento == null ? '' : `${ESPACIO_DURO}(−${linea.descuento}${ESPACIO_FINO_DURO}%)`),
		importe: precio(linea.subtotal),
	}));
}

// «Usted ahorró»: lo descontado en todas las líneas, sumado en centavos enteros para no arrastrar errores de coma
// flotante; null si ninguna tuvo descuento (HU-24 RF-18)
export function ahorroDelTicket(lineas: LineaDePedido[]): string | null {
	const conDescuento = lineas.filter((linea) => linea.descontado != null);
	if (conDescuento.length === 0) {
		return null;
	}
	const centavos = conDescuento.reduce((suma, linea) => suma + Math.round(linea.descontado! * 100), 0);
	return precio(centavos / 100);
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
