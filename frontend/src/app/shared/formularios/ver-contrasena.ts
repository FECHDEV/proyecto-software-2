import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { LucideEye, LucideEyeOff } from '@lucide/angular';

// El botón para mostrar u ocultar una contraseña, que empieza oculta (HU-02
// RF-13, HU-04 RF-12; decisiones.md → Datos personales). Va dentro de un
// `.con-boton`, junto a su campo, y cambia el tipo de ese campo: la pantalla no
// guarda estado, y dos campos no se pisan.
//
//   <div class="con-boton">
//     <input #clave type="password" … />
//     <button appVerContrasena [campo]="clave"></button>
//   </div>
@Component({
	selector: 'button[appVerContrasena]',
	imports: [LucideEye, LucideEyeOff],
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: {
		type: 'button',
		class: 'ver-contrasena',
		'[attr.aria-label]': 'etiqueta()',
		'[attr.title]': 'etiqueta()',
		// Con dos campos en la misma pantalla, cada botón dice cuál controla
		'[attr.aria-controls]': 'campo().id || null',
		'(click)': 'alternar()',
	},
	template: `
		@if (visible()) {
			<svg lucideEyeOff class="icono" aria-hidden="true"></svg>
		} @else {
			<svg lucideEye class="icono" aria-hidden="true"></svg>
		}
	`,
	styles: `
		:host {
			position: absolute;
			top: 0;
			right: 0;
			display: grid;
			place-items: center;
			width: var(--app-area-tocable);
			height: var(--app-area-tocable);
			padding: 0;
			border: none;
			border-radius: var(--app-radio-control);
			background: transparent;
			color: var(--app-texto-suave);
			cursor: pointer;
			transition: color var(--app-transicion);
		}

		:host(:hover) {
			color: var(--app-texto);
		}

		.icono {
			width: var(--app-icono);
			height: var(--app-icono);
		}
	`,
})
export class VerContrasena {
	readonly campo = input.required<HTMLInputElement>();
	protected readonly visible = signal(false);
	protected readonly etiqueta = computed(() => (this.visible() ? 'Ocultar contraseña' : 'Mostrar contraseña'));

	protected alternar(): void {
		this.mostrar(!this.visible());
	}

	// Para la pantalla que vacía el campo después de usarlo: la próxima contraseña
	// que se escriba no tiene que quedar a la vista
	ocultar(): void {
		this.mostrar(false);
	}

	private mostrar(visible: boolean): void {
		this.visible.set(visible);
		this.campo().type = visible ? 'text' : 'password';
	}
}
