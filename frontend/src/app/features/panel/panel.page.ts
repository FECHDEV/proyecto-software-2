import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SesionService } from '../../core/sesion/sesion.service';

// La bienvenida con los accesos de cada rol: es la portada con sesión. Cada
// módulo nuevo suma su acceso acá y en el encabezado (app.component.ts).
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
	// HU-08, HU-17: el Contador ve el inventario y los pedidos sin poder registrar
	protected readonly esPersonal = computed(() => {
		const rol = this.sesion.rol();
		return rol === 'ADMINISTRADOR' || rol === 'EMPLEADO' || rol === 'CONTADOR';
	});
}
