import { cantidad } from '../../shared/formato/precios';
import { UnidadVenta } from './producto';

// Espejo de InventarioResponse, MovimientoResponse y de los pedidos de InventarioController (HU-08)

// RESERVA y LIBERACION los registra un pedido al confirmarse y al cancelarse o vencer (HU-12 RF-14)
export type TipoMovimiento = 'ENTRADA' | 'AJUSTE' | 'RESERVA' | 'LIBERACION';

export type MotivoAjuste = 'CONTEO' | 'MERMA' | 'DEVOLUCION' | 'OTRO';

export const MOTIVOS: readonly MotivoAjuste[] = ['CONTEO', 'MERMA', 'DEVOLUCION', 'OTRO'];

// Cómo se nombran en pantalla; en el código siguen siendo los del backend
export const NOMBRES_DE_MOTIVO: Record<MotivoAjuste, string> = {
	CONTEO: 'Conteo físico',
	MERMA: 'Daño o merma',
	DEVOLUCION: 'Devolución',
	OTRO: 'Otro',
};

// Los decimales que admite una cantidad en cada unidad (UnidadVenta.decimales() del backend)
export const DECIMALES_DE_UNIDAD: Record<UnidadVenta, number> = {
	UNIDAD: 0,
	METRO: 2,
	KILO: 2,
	LITRO: 2,
	CAJA: 0,
	PAR: 0,
	ROLLO: 0,
};

// Los mismos límites que EntradaRequest, AjusteRequest y MinimoRequest
export const ENTRADA_MAXIMA = 99_999.99;
export const STOCK_MAXIMO = 9_999_999.99;

export interface ProductoEnInventario {
	idProducto: number;
	codigo: string;
	nombre: string;
	categoria: string;
	unidad: UnidadVenta;
	activo: boolean;
	// El disponible para vender
	stock: number;
	stockMinimo: number | null;
	stockBajo: boolean;
	// Lo que sigue en el estante por pedidos pendientes o pagados sin retirar (HU-08 RF-15)
	comprometido: number;
}

export interface Movimiento {
	idMovimiento: number;
	fecha: string;
	tipo: TipoMovimiento;
	motivo: MotivoAjuste | null;
	// Con signo: lo que cambió el stock
	cantidad: number;
	stockResultante: number;
	// Nombre y apellido de quien lo registró
	usuario: string;
	nota: string | null;
	// Solo en la reserva y la liberación
	idPedido: number | null;
	// Solo en los ajustes desde el 27/10/2026: lo contado en el estante y lo comprometido (HU-08 RF-17)
	contado: number | null;
	comprometido: number | null;
}

// Cómo se nombra cada movimiento en el historial (HU-08 RF-11, HU-12 RF-14)
export function descripcionDeMovimiento(movimiento: Movimiento): string {
	switch (movimiento.tipo) {
		case 'ENTRADA':
			return 'Entrada';
		case 'AJUSTE': {
			const motivo = `Ajuste · ${movimiento.motivo === null ? '' : NOMBRES_DE_MOTIVO[movimiento.motivo]}`;
			return movimiento.contado === null || movimiento.comprometido === null
				? motivo
				: `${motivo} · en el estante ${cantidad(movimiento.contado)}, comprometido ${cantidad(movimiento.comprometido)}`;
		}
		case 'RESERVA':
			return `Reserva por pedido N.º ${movimiento.idPedido}`;
		case 'LIBERACION':
			return `Liberación por pedido N.º ${movimiento.idPedido}`;
	}
}

// Los filtros de la lista. null en categoría o estado es "todos"; la página
// empieza en 0, como en el backend.
export interface FiltrosDeInventario {
	texto: string;
	categoria: number | null;
	activo: boolean | null;
	soloBajo: boolean;
	pagina: number;
}
