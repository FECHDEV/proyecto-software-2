import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';

import { ReportesService } from '../../core/api/reportes.service';
import { FiltrosDeReportes } from '../../core/modelos/reportes';
import { precio } from '../../shared/formato/precios';
import { cantidadConUnidad, porcentaje } from './formato';
import { errorDeLectura, ultimoValor } from './lectura';
import { Paginador } from './paginador';

// Los pedidos que vencieron o se cancelaron en el período, la parte de los
// confirmados que se vendió y los productos que tenían (HU-18 RF-17, RF-18)
@Component({
	selector: 'app-reportes-perdidos',
	imports: [Paginador],
	templateUrl: './reportes-perdidos.html',
	styleUrl: './reportes-perdidos.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesPerdidos {
	readonly filtros = input.required<FiltrosDeReportes>();
	readonly cambiar = output<Partial<FiltrosDeReportes>>();

	private readonly reportes = inject(ReportesService);
	// Las cifras no cambian de página: solo se vuelven a pedir con otro período o tipo
	private readonly periodo = computed(() => this.filtros(), {
		equal: (a, b) => a.desde === b.desde && a.hasta === b.hasta && a.tipo === b.tipo,
	});
	protected readonly recursoDelResumen = this.reportes.resumenDePerdidos(this.periodo);
	protected readonly resumen = ultimoValor(this.recursoDelResumen);
	protected readonly recurso = this.reportes.perdidos(this.filtros);
	protected readonly pagina = ultimoValor(this.recurso);
	// Si falla cualquiera de las dos lecturas, se dice (RF-22)
	protected readonly errorDelResumen = errorDeLectura(this.recursoDelResumen);
	protected readonly errorDeLaLista = errorDeLectura(this.recurso);
	protected readonly errorDeCarga = computed(() => this.errorDelResumen() || this.errorDeLaLista());

	// Solo lo que falló: la otra lectura ya está
	protected reintentar(): void {
		if (this.errorDelResumen()) {
			this.recursoDelResumen.reload();
		}
		if (this.errorDeLaLista()) {
			this.recurso.reload();
		}
	}

	protected readonly precio = precio;
	protected readonly porcentaje = porcentaje;
	protected readonly cantidad = cantidadConUnidad;
}
