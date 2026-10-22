import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';

import { ReportesService } from '../../core/api/reportes.service';
import { DIAS, FiltrosDeReportes } from '../../core/modelos/reportes';
import { fechaCorta } from '../../shared/formato/fechas';
import { precio } from '../../shared/formato/precios';
import { cantidadConUnidad } from './formato';
import { errorDeLectura, ultimoValor } from './lectura';
import { Paginador } from './paginador';

// Los productos que llevan más tiempo sin venderse, para elegir qué rebajar
// (HU-18 RF-13). No depende del período ni del tipo de cliente
@Component({
	selector: 'app-reportes-sin-venta',
	imports: [Paginador],
	templateUrl: './reportes-sin-venta.html',
	styleUrl: './reportes-sin-venta.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesSinVenta {
	readonly filtros = input.required<FiltrosDeReportes>();
	readonly cambiar = output<Partial<FiltrosDeReportes>>();

	protected readonly recurso = inject(ReportesService).sinVenta(this.filtros);
	protected readonly pagina = ultimoValor(this.recurso);
	protected readonly errorDeCarga = errorDeLectura(this.recurso);

	protected readonly dias = DIAS;
	protected readonly precio = precio;
	protected readonly fecha = fechaCorta;
	protected readonly cantidad = cantidadConUnidad;
}
