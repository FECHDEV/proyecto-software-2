import {
	ChangeDetectionStrategy,
	Component,
	CUSTOM_ELEMENTS_SCHEMA,
	effect,
	inject,
	InjectionToken,
	input,
	linkedSignal,
	signal,
	untracked,
} from '@angular/core';

// Prepara <model-viewer>: true si se puede mostrar, false si el navegador no
// tiene WebGL. Un token, para que las pruebas sobre jsdom no lo importen
export const CARGAR_VISOR_3D = new InjectionToken<() => Promise<boolean>>('CARGAR_VISOR_3D', {
	providedIn: 'root',
	factory: () => cargarVisor3d,
});

// El visor pesa unos 300 kB: se descarga en un fragmento aparte, la primera vez
// que se abre uno (decisiones.md → Visor 3D y modelos)
const cargarVisor3d = cargadorDeVisor(
	() => import('@google/model-viewer'),
	() => {
		const lienzo = document.createElement('canvas');
		return (lienzo.getContext('webgl2') ?? lienzo.getContext('webgl')) !== null;
	},
);

// Recuerda la respuesta, pero no un fallo de la descarga: un corte de red no
// deja el visor roto hasta recargar la página (/code-review)
export function cargadorDeVisor(importar: () => Promise<unknown>, hayWebgl: () => boolean): () => Promise<boolean> {
	let carga: Promise<boolean> | undefined;
	return () => {
		carga ??= (async () => {
			if (!hayWebgl()) {
				return false;
			}
			await importar();
			return true;
		})().catch((error: unknown) => {
			carga = undefined;
			throw error;
		});
		return carga;
	};
}

// Un modelo .glb que se gira, se acerca y se mueve con el mouse o los dedos
// (HU-15, y el detalle del producto en HU-16). Sin WebGL, o con un archivo que
// no se puede mostrar, lo dice en lugar del visor
@Component({
	selector: 'app-visor-3d',
	templateUrl: './visor-3d.html',
	styleUrl: './visor-3d.scss',
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Visor3d {
	readonly src = input.required<string>();
	readonly alt = input.required<string>();

	// null mientras se prepara el visor
	protected readonly disponible = signal<boolean | null>(null);
	// Cada modelo nuevo vuelve a cargar
	protected readonly modelo = linkedSignal<string, 'cargando' | 'listo' | 'error'>({
		source: this.src,
		computation: () => 'cargando',
	});

	// Con cada modelo, si el visor todavía no está: una descarga que falló se vuelve a intentar con el siguiente
	// (/code-review, ronda 2)
	constructor() {
		const cargar = inject(CARGAR_VISOR_3D);
		effect(() => {
			this.src();
			if (untracked(this.disponible) !== true) {
				cargar().then(
					(disponible) => this.disponible.set(disponible),
					() => this.disponible.set(false),
				);
			}
		});
	}
}
