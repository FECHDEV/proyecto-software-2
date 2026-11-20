import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';

import { ReportesService } from '../../core/api/reportes.service';
import { ClienteQueCompra, FiltrosDeReportes } from '../../core/modelos/reportes';
import { NOMBRES_DE_TIPO } from '../../core/modelos/usuario';
import { fechaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';
import { errorDeLectura, ultimoValor } from './lectura';
import { Paginador } from './paginador';

// Los clientes que compraron en el período, del que más gastó al que menos, con el contacto para atenderlos
// (HU-18 RF-23 a RF-25). El consumidor final del mostrador no tiene cuenta y no aparece
@Component({
	selector: 'app-reportes-compradores',
	imports: [Paginador],
	templateUrl: './reportes-compradores.html',
	styleUrl: './reportes-compradores.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesCompradores {
	readonly filtros = input.required<FiltrosDeReportes>();
	readonly cambiar = output<Partial<FiltrosDeReportes>>();

	protected readonly recurso = inject(ReportesService).compradores(this.filtros);
	protected readonly pagina = ultimoValor(this.recurso);
	protected readonly errorDeCarga = errorDeLectura(this.recurso);

	protected readonly nombresDeTipo = NOMBRES_DE_TIPO;
	protected readonly precio = precio;

	// RF-23: en qué canal compró en el período
	protected canal(fila: ClienteQueCompra): string {
		if (fila.enLinea > 0 && fila.enTienda > 0) {
			return 'Los dos';
		}
		return fila.enTienda > 0 ? 'En tienda' : 'En línea';
	}

	protected readonly fecha = fechaCorta;

	// RF-24
	protected cadaCuanto(fila: ClienteQueCompra): string {
		if (fila.diasEntreCompras === null) {
			return 'Una sola compra';
		}
		if (fila.diasEntreCompras === 0) {
			return 'El mismo día';
		}
		return fila.diasEntreCompras === 1 ? 'Cada día' : `Cada ${cantidad(fila.diasEntreCompras)} días`;
	}
}
