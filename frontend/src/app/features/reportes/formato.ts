import { NOMBRES_DE_UNIDAD, UnidadVenta } from '../../core/modelos/producto';
import { cantidad } from '../../shared/formato/precios';

// Formatos de los reportes (HU-18), en español de Bolivia

const PORCENTAJE = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 1 });

const MES = new Intl.DateTimeFormat('es-BO', { month: 'long', year: 'numeric', timeZone: 'UTC' });

export const NOMBRES_DE_DIA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

// 29,8 %
export function porcentaje(valor: number): string {
	return `${PORCENTAJE.format(valor)} %`;
}

// La cifra del período anterior y la diferencia (RF-7), con palabras: no
// depende del color ni de una flecha. sinAntes es lo que se dice si antes no
// hubo con qué comparar (RF-7: «sin ventas antes»); un cero también lo es,
// salvo que sea un dato (un margen de Bs 0 con ventas): ceroEsSinDatos
export function comparacion(
	actual: number | null,
	anterior: number | null,
	formatear: (valor: number) => string,
	sinAntes = 'sin ventas antes',
	ceroEsSinDatos = true,
): string {
	if (anterior === null || (anterior === 0 && ceroEsSinDatos)) {
		return sinAntes;
	}
	const antes = `antes ${formatear(anterior)}`;
	// Sin cifra ahora, o con un cero antes, no hay porcentaje
	if (actual === null || anterior === 0) {
		return antes;
	}
	const diferencia = Math.round(((actual - anterior) / Math.abs(anterior)) * 100);
	if (diferencia === 0) {
		return `${antes}, igual`;
	}
	return `${antes}, ${diferencia > 0 ? 'subió' : 'bajó'} ${PORCENTAJE.format(Math.abs(diferencia))} %`;
}

// El ancho de una barra proporcional, en %: la más alta llena la celda y una
// que vendió algo se ve aunque sea poco
export function anchoDeBarra(valor: number, maximo: number): number {
	return maximo <= 0 || valor <= 0 ? 0 : Math.max(2, (valor / maximo) * 100);
}

// «octubre de 2025» para un mes (AAAA-MM)
export function nombreDelMes(mes: string): string {
	return MES.format(new Date(`${mes}-01T00:00:00Z`));
}

// 08:00
export function hora(valor: number): string {
	return `${String(valor).padStart(2, '0')}:00`;
}

// 2,5 · Metro: no se suman unidades distintas (RF-11)
export function cantidadConUnidad(valor: number, unidad: UnidadVenta): string {
	return `${cantidad(valor)} · ${NOMBRES_DE_UNIDAD[unidad]}`;
}
