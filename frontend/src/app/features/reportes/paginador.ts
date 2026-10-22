import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Pagina } from '../../core/modelos/pagina';
import { cantidad } from '../../shared/formato/precios';

// «Anterior» y «Siguiente» de las listas de los reportes (HU-18), de a 10. Solo
// aparece si hay más de una página
@Component({
	selector: 'app-paginador',
	template: `
		@if (pagina().totalPaginas > 1) {
			<nav class="paginacion" aria-label="Páginas de la lista">
				<button
					type="button"
					class="secundario"
					[disabled]="pagina().pagina === 0 || cargando()"
					(click)="irA.emit(pagina().pagina - 1)"
				>
					Anterior
				</button>
				<p class="numero">
					Página {{ pagina().pagina + 1 }} de {{ pagina().totalPaginas }} · {{ cantidad(pagina().totalElementos) }}
					{{ nombre() }}
				</p>
				<button
					type="button"
					class="secundario"
					[disabled]="pagina().pagina + 1 >= pagina().totalPaginas || cargando()"
					(click)="irA.emit(pagina().pagina + 1)"
				>
					Siguiente
				</button>
			</nav>
		}
	`,
	styleUrl: './paginador.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Paginador {
	readonly pagina = input.required<Pagina<unknown>>();
	readonly cargando = input(false);
	// Lo que se cuenta, en plural: «productos», «clientes»
	readonly nombre = input.required<string>();
	readonly irA = output<number>();
	protected readonly cantidad = cantidad;
}
