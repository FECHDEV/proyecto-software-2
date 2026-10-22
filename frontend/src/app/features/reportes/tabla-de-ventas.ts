import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { cantidad, precio } from '../../shared/formato/precios';
import { anchoDeBarra } from './formato';

export interface FilaDeVentas {
	etiqueta: string;
	pedidos: number;
	ingresos: number;
	// El margen o el promedio, ya con formato; vacío no se muestra
	extra?: string;
	negativo?: boolean;
}

// Una tabla de ventas agrupadas (por fecha, día, hora, categoría o tipo) con
// una barra proporcional a los ingresos (HU-18 RF-9, RF-10, RF-14, RF-15). La
// barra es decorativa: el número va al lado y la tabla es la vista accesible
@Component({
	selector: 'app-tabla-de-ventas',
	templateUrl: './tabla-de-ventas.html',
	styleUrl: './tabla-de-ventas.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablaDeVentas {
	readonly titulo = input.required<string>();
	readonly columna = input.required<string>();
	readonly filas = input.required<readonly FilaDeVentas[]>();
	readonly columnaExtra = input<string | null>(null);

	private readonly maximo = computed(() => Math.max(0, ...this.filas().map((fila) => fila.ingresos)));
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;

	protected ancho(ingresos: number): number {
		return anchoDeBarra(ingresos, this.maximo());
	}
}
