import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';

import { ReportesService } from '../../core/api/reportes.service';
import { DIAS, FiltrosDeReportes } from '../../core/modelos/reportes';
import { NOMBRES_DE_TIPO } from '../../core/modelos/usuario';
import { fechaCorta } from '../../shared/formato/fechas';
import { precio } from '../../shared/formato/precios';
import { errorDeLectura, ultimoValor } from './lectura';
import { Paginador } from './paginador';

// Los clientes que dejaron de comprar, con el contacto para llamarlos (HU-18
// RF-16). La tienda no les manda correos: el enlace abre el programa de correo
// de quien mira el reporte
@Component({
	selector: 'app-reportes-clientes',
	imports: [Paginador],
	templateUrl: './reportes-clientes.html',
	styleUrl: './reportes-clientes.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesClientes {
	readonly filtros = input.required<FiltrosDeReportes>();
	readonly cambiar = output<Partial<FiltrosDeReportes>>();

	protected readonly recurso = inject(ReportesService).clientesInactivos(this.filtros);
	protected readonly pagina = ultimoValor(this.recurso);
	protected readonly errorDeCarga = errorDeLectura(this.recurso);

	protected readonly dias = DIAS;
	protected readonly nombresDeTipo = NOMBRES_DE_TIPO;
	protected readonly precio = precio;
	protected readonly fecha = fechaCorta;
}
