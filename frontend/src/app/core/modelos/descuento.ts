// Los descuentos de HU-24, espejo de DescuentoResponse y de sus solicitudes. Las fechas van como AAAA-MM-DD

export type EstadoDescuento = 'PROGRAMADO' | 'VIGENTE' | 'VENCIDO';

export const NOMBRES_DE_ESTADO_DESCUENTO: Record<EstadoDescuento, string> = {
	PROGRAMADO: 'Programado',
	VIGENTE: 'Vigente',
	VENCIDO: 'Vencido',
};

// Los mismos límites que DescuentoRequest y DescuentoService (RF-1)
export const PORCENTAJE_MINIMO = 1;
export const PORCENTAJE_MAXIMO = 90;
export const DIAS_MAXIMOS = 366;

// De un producto o de una categoría: el otro par va en null
export interface Descuento {
	idDescuento: number;
	idProducto: number | null;
	producto: string | null;
	idCategoria: number | null;
	categoria: string | null;
	porcentaje: number;
	inicio: string;
	fin: string;
	estado: EstadoDescuento;
}

export interface SolicitudDeDescuento {
	idProducto: number | null;
	idCategoria: number | null;
	porcentaje: number;
	inicio: string;
	fin: string;
}

// Al editar no cambia a qué se aplica (RF-4)
export type CambioDeDescuento = Pick<SolicitudDeDescuento, 'porcentaje' | 'inicio' | 'fin'>;

// Se guarda igual aunque deje productos bajo su costo; se avisa cuáles (hasta 10) y cuántos (RF-6)
export interface DescuentoGuardado {
	descuento: Descuento;
	bajoElCosto: string[];
	totalBajoElCosto: number;
}

// A qué se aplica, para la lista
export function objetivoDe(descuento: Descuento): string {
	return descuento.producto ?? `Categoría «${descuento.categoria ?? ''}»`;
}

// Dentro de una frase («el descuento de…»): la categoría en minúscula
export function delObjetivo(descuento: Descuento): string {
	return descuento.producto ?? `la categoría «${descuento.categoria ?? ''}»`;
}
