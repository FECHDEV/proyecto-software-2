import { EstadoDePedido, LineaDePedido } from './pedido';

// Espejo de PedidoDespachoResumenResponse y PedidoDespachoResponse del backend (HU-17)

// TODOS no filtra; sin filtro en la dirección, la lista es la de «Pagado» (RF-2)
export type EstadoDeDespacho = EstadoDePedido | 'TODOS';

// En el orden del selector: lo que espera retiro primero
export const ESTADOS_DE_DESPACHO: readonly EstadoDeDespacho[] = [
	'PAGADO',
	'ENTREGADO',
	'PENDIENTE_PAGO',
	'PAGO_A_DEVOLVER',
	'CANCELADO',
	'VENCIDO',
	'TODOS',
];

export interface FiltrosDeDespacho {
	estado: EstadoDeDespacho;
	texto: string;
	pagina: number;
}

// Lo que filtra, para la dirección y para la API: «Pagado» es lo de entrada y
// no viaja, como el texto vacío y la primera página
export function consultaDeDespacho(filtros: FiltrosDeDespacho): Record<string, string | number> {
	const texto = filtros.texto.trim();
	return {
		...(filtros.estado === 'PAGADO' ? {} : { estado: filtros.estado }),
		...(texto === '' ? {} : { texto }),
		...(filtros.pagina > 0 ? { pagina: filtros.pagina } : {}),
	};
}

// Un pedido en la lista del personal (RF-1)
export interface PedidoEnDespacho {
	idPedido: number;
	estado: EstadoDePedido;
	confirmado: string;
	pagado: string | null;
	nombreRetira: string;
	cantidadProductos: number;
	total: number;
}

// La ficha (RF-4). entregadoPor: nombre y apellido de quien registró la entrega
export interface FichaDePedido {
	idPedido: number;
	estado: EstadoDePedido;
	lineas: LineaDePedido[];
	total: number;
	nombreRetira: string;
	telefonoRetira: string;
	nota: string | null;
	confirmado: string;
	pagado: string | null;
	entregado: string | null;
	entregadoPor: string | null;
	clienteNombre: string;
	clienteCorreo: string;
}
