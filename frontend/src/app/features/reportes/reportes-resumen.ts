import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { ReportesService } from '../../core/api/reportes.service';
import { FiltrosDeReportes, Totales, VentasDelPeriodo } from '../../core/modelos/reportes';
import { NOMBRES_DE_TIPO } from '../../core/modelos/usuario';
import { fechaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';
import { comparacion, hora, NOMBRES_DE_DIA, nombreDelMes, porcentaje } from './formato';
import { errorDeLectura, ultimoValor } from './lectura';
import { FilaDeVentas, TablaDeVentas } from './tabla-de-ventas';

interface Cifra {
	nombre: string;
	valor: string;
	detalle: string;
	comparacion: string;
	negativo: boolean;
}

// El resumen del período (HU-18 RF-6 a RF-10, RF-14, RF-15): las cifras
// comparadas con el período anterior y las ventas agrupadas
@Component({
	selector: 'app-reportes-resumen',
	imports: [TablaDeVentas],
	templateUrl: './reportes-resumen.html',
	styleUrl: './reportes-resumen.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesResumen {
	readonly filtros = input.required<FiltrosDeReportes>();

	protected readonly recurso = inject(ReportesService).ventas(this.filtros);
	protected readonly ventas = ultimoValor(this.recurso);
	protected readonly errorDeCarga = errorDeLectura(this.recurso);

	protected readonly cifras = computed(() => {
		const ventas = this.ventas();
		return ventas === undefined ? [] : cifras(ventas.actual, ventas.anterior);
	});
	protected readonly anterior = computed(() => {
		const periodo = this.ventas()?.periodo;
		return periodo === undefined ? '' : `${fechaCorta(periodo.anteriorDesde)} al ${fechaCorta(periodo.anteriorHasta)}`;
	});

	protected readonly porFecha = computed<FilaDeVentas[]>(() => {
		const ventas = this.ventas();
		if (ventas === undefined) {
			return [];
		}
		const porMes = ventas.periodo.agrupado === 'MES';
		return ventas.porFecha.map((fila) => ({
			etiqueta: porMes ? nombreDelMes(fila.fecha) : fechaCorta(fila.fecha),
			pedidos: fila.pedidos,
			ingresos: fila.ingresos,
		}));
	});
	protected readonly porDiaSemana = computed<FilaDeVentas[]>(() =>
		(this.ventas()?.porDiaSemana ?? []).map((fila) => ({
			etiqueta: NOMBRES_DE_DIA[fila.dia - 1],
			pedidos: fila.pedidos,
			ingresos: fila.ingresos,
		})),
	);
	protected readonly porHora = computed<FilaDeVentas[]>(() =>
		(this.ventas()?.porHora ?? []).map((fila) => ({
			etiqueta: hora(fila.hora),
			pedidos: fila.pedidos,
			ingresos: fila.ingresos,
		})),
	);
	protected readonly porCategoria = computed<FilaDeVentas[]>(() =>
		(this.ventas()?.porCategoria ?? []).map((fila) => ({
			etiqueta: fila.categoria,
			pedidos: fila.pedidos,
			ingresos: fila.ingresos,
			extra: fila.margen === null ? 'Sin costo' : precio(fila.margen),
			negativo: fila.margen !== null && fila.margen < 0,
		})),
	);
	protected readonly porTipo = computed<FilaDeVentas[]>(() =>
		(this.ventas()?.porTipo ?? []).map((fila) => ({
			etiqueta: NOMBRES_DE_TIPO[fila.tipo],
			pedidos: fila.pedidos,
			ingresos: fila.ingresos,
			extra: fila.promedio === null ? '' : precio(fila.promedio),
		})),
	);

	protected readonly sinVentas = computed(() => {
		const tipo = this.filtros().tipo;
		return tipo === null
			? 'No hubo ventas en este período.'
			: `No hubo ventas a clientes «${NOMBRES_DE_TIPO[tipo]}» en este período.`;
	});

	// RF-15: no depende del filtro por tipo
	protected readonly hayVentasDeAlgunTipo = computed(() => (this.ventas()?.porTipo ?? []).some((fila) => fila.pedidos > 0));

	protected titulo(ventas: VentasDelPeriodo): string {
		return ventas.periodo.agrupado === 'MES' ? 'Ventas por mes' : 'Ventas por día';
	}
}

// RF-6 a RF-8: cada cifra con la del período anterior
function cifras(actual: Totales, anterior: Totales): Cifra[] {
	const margen =
		actual.margen === null
			? { valor: 'Sin costo cargado', detalle: '', negativo: false }
			: {
					valor: precio(actual.margen),
					detalle: actual.margenPorcentaje === null ? '' : `${porcentaje(actual.margenPorcentaje)} de lo vendido con costo`,
					negativo: actual.margen < 0,
				};
	return [
		{
			nombre: 'Ingresos',
			valor: precio(actual.ingresos),
			detalle: '',
			comparacion: comparacion(actual.ingresos, anterior.ingresos, precio),
			negativo: false,
		},
		{
			nombre: 'Pedidos vendidos',
			valor: cantidad(actual.pedidos),
			detalle: '',
			comparacion: comparacion(actual.pedidos, anterior.pedidos, cantidad),
			negativo: false,
		},
		{
			nombre: 'Promedio por pedido',
			valor: actual.promedio === null ? '—' : precio(actual.promedio),
			detalle: '',
			comparacion: comparacion(actual.promedio, anterior.promedio, precio),
			negativo: false,
		},
		{
			nombre: 'Margen',
			...margen,
			detalle:
				actual.vendidoSinCosto > 0
					? [margen.detalle, `${precio(actual.vendidoSinCosto)} vendidos sin costo cargado`].filter(Boolean).join(' · ')
					: margen.detalle,
			comparacion: comparacion(actual.margen, anterior.margen, precio, 'sin margen antes', false),
		},
		{
			nombre: 'Pagos a devolver',
			valor: precio(actual.devolverImporte),
			detalle: actual.devolverCantidad === 1 ? '1 pedido' : `${actual.devolverCantidad} pedidos`,
			comparacion: comparacion(actual.devolverImporte, anterior.devolverImporte, precio, 'sin pagos a devolver antes'),
			negativo: false,
		},
	];
}
