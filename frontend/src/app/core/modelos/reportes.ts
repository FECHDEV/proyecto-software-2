import { UnidadVenta } from './producto';
import { TipoCliente, TIPOS_DE_CLIENTE } from './usuario';

// Espejo de los DTO de /api/reportes (HU-18) y la lectura de la dirección de /reportes

export type Seccion = 'resumen' | 'productos' | 'sin-venta' | 'clientes' | 'perdidos';

export const SECCIONES: readonly { valor: Seccion; nombre: string }[] = [
	{ valor: 'resumen', nombre: 'Resumen' },
	{ valor: 'productos', nombre: 'Productos' },
	{ valor: 'sin-venta', nombre: 'Sin venderse' },
	{ valor: 'clientes', nombre: 'Clientes' },
	{ valor: 'perdidos', nombre: 'Pedidos perdidos' },
];

// Más o menos vendidos (RF-11, RF-12)
export type Orden = 'MAS' | 'MENOS';

// «Más de 30, 60 o 90 días» (RF-13, RF-16)
export type Dias = 30 | 60 | 90;
export const DIAS: readonly Dias[] = [30, 60, 90];

export interface FiltrosDeReportes {
	seccion: Seccion;
	// AAAA-MM-DD, en días de Bolivia
	desde: string;
	hasta: string;
	// null son todos los tipos
	tipo: TipoCliente | null;
	orden: Orden;
	dias: Dias;
	pagina: number;
}

export type Atajo = 'hoy' | 'semana' | 'mes' | 'mes-anterior';

export const ATAJOS: readonly { valor: Atajo; nombre: string }[] = [
	{ valor: 'hoy', nombre: 'Hoy' },
	{ valor: 'semana', nombre: 'Últimos 7 días' },
	{ valor: 'mes', nombre: 'Este mes' },
	{ valor: 'mes-anterior', nombre: 'Mes anterior' },
];

// Como el backend (Periodo.MAXIMO_DIAS)
const MAXIMO_DIAS = 366;

const DIA_EN_BOLIVIA = new Intl.DateTimeFormat('en-CA', {
	timeZone: 'America/La_Paz',
	year: 'numeric',
	month: '2-digit',
	day: '2-digit',
});

// El día de hoy en Bolivia, AAAA-MM-DD. El backend corta los períodos con la
// hora de La Paz: con el día del navegador, en otra zona, un «hasta» de mañana
// sería un 400 (RF-3)
export function hoyEnBolivia(ahora = new Date()): string {
	return DIA_EN_BOLIVIA.format(ahora);
}

export function periodoDeAtajo(atajo: Atajo, hoy: string): { desde: string; hasta: string } {
	switch (atajo) {
		case 'hoy':
			return { desde: hoy, hasta: hoy };
		case 'semana':
			return { desde: sumarDias(hoy, -6), hasta: hoy };
		case 'mes':
			return { desde: primeroDelMes(hoy), hasta: hoy };
		case 'mes-anterior': {
			const ultimo = sumarDias(primeroDelMes(hoy), -1);
			return { desde: primeroDelMes(ultimo), hasta: ultimo };
		}
	}
}

// La dirección la puede escribir cualquiera (RF-3): lo que no sirve se lee
// como lo más cercano, sin llegar al backend como un 400. ajustado dice si se
// corrigió un período escrito, para avisarlo
export function leerConsultaDeReportes(
	valor: (clave: string) => string | null,
	hoy: string,
): { filtros: FiltrosDeReportes; ajustado: boolean } {
	const { desde, hasta, ajustado } = leerPeriodo(valor('desde'), valor('hasta'), hoy);
	const seccion = SECCIONES.find((cada) => cada.valor === valor('seccion'))?.valor ?? 'resumen';
	const tipo = TIPOS_DE_CLIENTE.find((cada) => cada === valor('tipo')) ?? null;
	const dias = DIAS.find((cada) => String(cada) === valor('dias')) ?? 30;
	const pagina = valor('pagina');
	return {
		filtros: {
			seccion,
			desde,
			hasta,
			tipo,
			orden: valor('orden') === 'MENOS' ? 'MENOS' : 'MAS',
			dias,
			pagina: pagina !== null && /^\d{1,6}$/.test(pagina) ? Number(pagina) : 0,
		},
		ajustado,
	};
}

// Solo lo que filtra queda en la dirección: lo de entrada (el resumen de este
// mes, todos los tipos, los más vendidos, 30 días, la primera página) no
export function consultaDeReportes(filtros: FiltrosDeReportes, hoy: string): Record<string, string | number> {
	const esteMes = periodoDeAtajo('mes', hoy);
	const mismoPeriodo = filtros.desde === esteMes.desde && filtros.hasta === esteMes.hasta;
	return {
		...(filtros.seccion === 'resumen' ? {} : { seccion: filtros.seccion }),
		...(mismoPeriodo ? {} : { desde: filtros.desde, hasta: filtros.hasta }),
		...(filtros.tipo === null ? {} : { tipo: filtros.tipo }),
		...(filtros.orden === 'MAS' ? {} : { orden: filtros.orden }),
		...(filtros.dias === 30 ? {} : { dias: filtros.dias }),
		...(filtros.pagina > 0 ? { pagina: filtros.pagina } : {}),
	};
}

function leerPeriodo(
	desdeEscrito: string | null,
	hastaEscrito: string | null,
	hoy: string,
): { desde: string; hasta: string; ajustado: boolean } {
	const esteMes = periodoDeAtajo('mes', hoy);
	if (desdeEscrito === null && hastaEscrito === null) {
		return { ...esteMes, ajustado: false };
	}
	if (!esFecha(desdeEscrito) || !esFecha(hastaEscrito)) {
		return { ...esteMes, ajustado: true };
	}
	// Las fechas AAAA-MM-DD se comparan como texto
	let desde = desdeEscrito > hoy ? hoy : desdeEscrito;
	let hasta = hastaEscrito > hoy ? hoy : hastaEscrito;
	if (desde > hasta) {
		[desde, hasta] = [hasta, desde];
	}
	if (desde < sumarDias(hasta, -(MAXIMO_DIAS - 1))) {
		desde = sumarDias(hasta, -(MAXIMO_DIAS - 1));
	}
	return { desde, hasta, ajustado: desde !== desdeEscrito || hasta !== hastaEscrito };
}

// Una fecha que existe: «2026-02-30» no
function esFecha(texto: string | null): texto is string {
	return texto !== null && /^\d{4}-\d{2}-\d{2}$/.test(texto) && fecha(texto).toISOString().slice(0, 10) === texto;
}

function fecha(texto: string): Date {
	return new Date(`${texto}T00:00:00Z`);
}

export function sumarDias(texto: string, dias: number): string {
	const resultado = fecha(texto);
	resultado.setUTCDate(resultado.getUTCDate() + dias);
	return resultado.toISOString().slice(0, 10);
}

function primeroDelMes(texto: string): string {
	return `${texto.slice(0, 8)}01`;
}

// GET /api/reportes/ventas

export interface Totales {
	ingresos: number;
	pedidos: number;
	// null si no hay con qué dividir
	promedio: number | null;
	// null si nada vendido tenía costo (RF-8)
	margen: number | null;
	margenPorcentaje: number | null;
	vendidoSinCosto: number;
	devolverCantidad: number;
	devolverImporte: number;
}

export interface VentasDelPeriodo {
	periodo: { desde: string; hasta: string; anteriorDesde: string; anteriorHasta: string; agrupado: 'DIA' | 'MES' };
	actual: Totales;
	anterior: Totales;
	// fecha: AAAA-MM-DD, o AAAA-MM si va por mes
	porFecha: { fecha: string; ingresos: number; pedidos: number }[];
	// dia: 1 lunes … 7 domingo
	porDiaSemana: { dia: number; ingresos: number; pedidos: number }[];
	porHora: { hora: number; ingresos: number; pedidos: number }[];
	porCategoria: { categoria: string; ingresos: number; pedidos: number; margen: number | null }[];
	porTipo: { tipo: TipoCliente; ingresos: number; pedidos: number; promedio: number | null }[];
}

// GET /api/reportes/perdidos/resumen. confirmados no cuenta los que siguen esperando el pago ni los pagos a
// devolver (RF-17)
export interface ResumenDePerdidos {
	cantidad: number;
	importe: number;
	confirmados: number;
	vendidos: number;
	proporcion: number | null;
}

// Las listas, de a 10

export interface ProductoVendido {
	idProducto: number;
	codigo: string | null;
	nombre: string;
	unidad: UnidadVenta;
	activo: boolean;
	cantidad: number;
	importe: number;
	margen: number | null;
	sinCosto: number;
}

export interface ProductoSinVenta {
	idProducto: number;
	codigo: string | null;
	nombre: string;
	unidad: UnidadVenta;
	stock: number;
	precio: number;
	costo: number | null;
	// null si nunca se vendió: los días cuentan desde el alta
	ultimaVenta: string | null;
	diasSinVenta: number;
	inmovilizado: number;
	inmovilizadoAPrecio: boolean;
}

export interface ClienteInactivo {
	idUsuario: number;
	nombre: string;
	apellido: string;
	correo: string;
	telefono: string | null;
	tipo: TipoCliente;
	ultimaCompra: string;
	pedidos: number;
	total: number;
}

export interface ProductoPerdido {
	idProducto: number;
	codigo: string | null;
	nombre: string;
	unidad: UnidadVenta;
	cantidad: number;
	importe: number;
}
