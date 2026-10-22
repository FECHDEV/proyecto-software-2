import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';

import { ReportesService } from '../../core/api/reportes.service';
import { FiltrosDeReportes, Orden } from '../../core/modelos/reportes';
import { precio } from '../../shared/formato/precios';
import { anchoDeBarra, cantidadConUnidad } from './formato';
import { errorDeLectura, ultimoValor } from './lectura';
import { Paginador } from './paginador';

// Los productos más y menos vendidos del período (HU-18 RF-11, RF-12)
@Component({
	selector: 'app-reportes-productos',
	imports: [Paginador],
	templateUrl: './reportes-productos.html',
	styleUrl: './reportes-productos.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesProductos {
	readonly filtros = input.required<FiltrosDeReportes>();
	readonly cambiar = output<Partial<FiltrosDeReportes>>();

	protected readonly recurso = inject(ReportesService).productos(this.filtros);
	protected readonly pagina = ultimoValor(this.recurso);
	protected readonly errorDeCarga = errorDeLectura(this.recurso);
	// La barra compara con el más alto de la página
	protected readonly maximo = computed(() => Math.max(0, ...(this.pagina()?.contenido ?? []).map((fila) => fila.importe)));

	protected readonly ordenes: readonly { valor: Orden; nombre: string }[] = [
		{ valor: 'MAS', nombre: 'Más vendidos' },
		{ valor: 'MENOS', nombre: 'Menos vendidos' },
	];
	protected readonly precio = precio;
	protected readonly cantidad = cantidadConUnidad;

	protected ancho(importe: number): number {
		return anchoDeBarra(importe, this.maximo());
	}
}
