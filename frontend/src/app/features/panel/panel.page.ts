import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { puedeEntrar } from '../../core/sesion/modulos';
import { SesionService } from '../../core/sesion/sesion.service';

// La bienvenida con los accesos de cada rol: es la portada con sesión. Cada
// módulo nuevo suma su acceso acá, en el encabezado (app.component.ts) y sus
// roles en core/sesion/modulos.ts.
@Component({
	selector: 'app-panel',
	imports: [RouterLink],
	templateUrl: './panel.page.html',
	styleUrl: './panel.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelPage {
	private readonly sesion = inject(SesionService);

	protected readonly nombre = computed(() => this.sesion.usuario()?.nombre ?? '');
	protected readonly esCliente = computed(() => this.sesion.rol() === 'CLIENTE');
	protected readonly esAdministrador = computed(() => this.sesion.rol() === 'ADMINISTRADOR');
	// Los roles de cada módulo, en core/sesion/modulos.ts
	protected readonly esPersonal = computed(() => puedeEntrar(this.sesion.rol(), 'inventario'));
	protected readonly veReportes = computed(() => puedeEntrar(this.sesion.rol(), 'reportes'));
}
