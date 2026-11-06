import { EstadoDePedido, LineaDePedido } from './pedido';
import { Canal, MedioPago } from './venta';

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
	'ANULADO',
	'TODOS',
];

// TODOS no filtra (HU-23 RF-16)
export type CanalDeDespacho = Canal | 'TODOS';
export const CANALES_DE_DESPACHO: readonly CanalDeDespacho[] = ['TODOS', 'EN_LINEA', 'TIENDA'];

export interface FiltrosDeDespacho {
	estado: EstadoDeDespacho;
	canal: CanalDeDespacho;
	texto: string;
	pagina: number;
}

// Lo que filtra, para la dirección y para la API: «Pagado» es lo de entrada y
// no viaja, como todos los canales, el texto vacío y la primera página
export function consultaDeDespacho(filtros: FiltrosDeDespacho): Record<string, string | number> {
	const texto = filtros.texto.trim();
	return {
		...(filtros.estado === 'PAGADO' ? {} : { estado: filtros.estado }),
		...(filtros.canal === 'TODOS' ? {} : { canal: filtros.canal }),
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
	// null en una venta del mostrador (HU-23 RF-16)
	nombreRetira: string | null;
	cantidadProductos: number;
	total: number;
	canal: Canal;
}

// La ficha (RF-4). entregadoPor: nombre y apellido de quien registró la entrega
export interface FichaDePedido {
	idPedido: number;
	estado: EstadoDePedido;
	lineas: LineaDePedido[];
	total: number;
	nombreRetira: string | null;
	telefonoRetira: string | null;
	nota: string | null;
	confirmado: string;
	pagado: string | null;
	entregado: string | null;
	entregadoPor: string | null;
	// null en el consumidor final de una venta del mostrador
	clienteNombre: string | null;
	clienteCorreo: string | null;
	// Solo en el mostrador (HU-23 RF-16): el medio, quién vendió y la anulación
	canal: Canal;
	medioPago: MedioPago | null;
	vendidoPor: string | null;
	anulado: string | null;
	anuladoPor: string | null;
	motivoAnulacion: string | null;
}
