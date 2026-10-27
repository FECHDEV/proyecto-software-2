import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { inicioDe } from '../../core/sesion/inicio';
import { SesionService } from '../../core/sesion/sesion.service';

// Una dirección que no existe (revisión total, 27/10/2026): antes volvía a la portada sin decir nada
@Component({
	selector: 'app-no-encontrada',
	imports: [RouterLink],
	templateUrl: './no-encontrada.page.html',
	styleUrl: './no-encontrada.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoEncontradaPage {
	private readonly sesion = inject(SesionService);

	// El personal vuelve a su panel; el cliente y quien no tiene sesión, a la tienda
	protected readonly inicio = computed(() => inicioDe(this.sesion.rol()));
}
