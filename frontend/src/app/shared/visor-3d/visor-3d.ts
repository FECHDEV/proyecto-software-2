import {
	ChangeDetectionStrategy,
	Component,
	computed,
	CUSTOM_ELEMENTS_SCHEMA,
	effect,
	ElementRef,
	inject,
	InjectionToken,
	input,
	linkedSignal,
	signal,
	untracked,
	viewChild,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { LucideMaximize, LucideMinimize, LucideView } from '@lucide/angular';

// Prepara <model-viewer>: true si se puede mostrar, false si el navegador no
// tiene WebGL. Un token, para que las pruebas sobre jsdom no lo importen
export const CARGAR_VISOR_3D = new InjectionToken<() => Promise<boolean>>('CARGAR_VISOR_3D', {
	providedIn: 'root',
	factory: () => cargarVisor3d,
});

// Si el usuario pidió menos movimiento: sin la mano animada de model-viewer (HU-16 RF-7). Un token, para las pruebas
export const MOVIMIENTO_REDUCIDO = new InjectionToken<() => boolean>('MOVIMIENTO_REDUCIDO', {
	providedIn: 'root',
	factory: () => () => globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
});

// Lo que model-viewer le dice al lector de pantalla, en español (revisión total, 26/10/2026): cómo moverlo y desde
// dónde se mira. Las claves son las suyas (la vista por altura y por lado). La región «Live announcements» de su
// shadow DOM no se puede traducir desde afuera
const LADOS: Record<string, string> = {
	front: 'de frente',
	right: 'desde la derecha',
	back: 'desde atrás',
	left: 'desde la izquierda',
};
const ALTURAS: Record<string, string> = { 'upper-': ', desde arriba', '': '', 'lower-': ', desde abajo' };
export const TEXTOS_DEL_VISOR = JSON.stringify({
	'interaction-prompt': 'Use el mouse, los dedos o las flechas del teclado para moverlo.',
	...Object.fromEntries(
		Object.entries(ALTURAS).flatMap(([altura, alto]) =>
			Object.entries(LADOS).map(([lado, desde]) => [`${altura}${lado}`, `Vista ${desde}${alto}`]),
		),
	),
});

// angular.json (assets) los copia de node_modules/three. Sus nombres no llevan hash: nginx los revalida en cada
// visita (no-cache), así una versión nueva de three no se mezcla con la vieja (/code-review)
const DECODIFICADORES = '/decodificadores/';

// Un modelo comprimido con Draco, KTX2 o meshopt pide su decodificador: de la tienda, no de gstatic.com (HU-16
// RF-10). model-viewer lee ModelViewerElement al cargarse; lo que ya estaba configurado se respeta.
// meshopt ya viene dentro de model-viewer: su dirección solo lo activa, y la carga con un <script> clásico, así que
// no puede ser el módulo de three (daba «Unexpected token 'export'»; revisión de interfaz)
export function configurarDecodificadores(destino: Record<string, unknown>, base: string): void {
	destino['ModelViewerElement'] = {
		dracoDecoderLocation: `${base}draco/`,
		ktx2TranscoderLocation: `${base}basis/`,
		meshoptDecoderLocation: `${base}meshopt-incluido.js`,
		...(destino['ModelViewerElement'] as object | undefined),
	};
}

// El visor pesa unos 300 kB: se descarga en un fragmento aparte, la primera vez
// que se abre uno (decisiones.md → Visor 3D y modelos)
const cargarVisor3d = cargadorDeVisor(
	() => {
		configurarDecodificadores(globalThis as unknown as Record<string, unknown>, DECODIFICADORES);
		return import('@google/model-viewer');
	},
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

// Un modelo .glb que se gira, se acerca y se mueve con el mouse, los dedos o el
// teclado (HU-15, y el detalle del producto en HU-16). Sin WebGL, o con un
// archivo que no se puede mostrar, lo dice en lugar del visor. En la tienda
// lleva póster (la foto principal) y la realidad aumentada; no gira solo. Con la pantalla completa del navegador,
// un botón la abre y la cierra (HU-16 RF-12)
@Component({
	selector: 'app-visor-3d',
	templateUrl: './visor-3d.html',
	styleUrl: './visor-3d.scss',
	imports: [LucideView, LucideMaximize, LucideMinimize],
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '(document:fullscreenchange)': 'alCambiarPantallaCompleta()' },
})
export class Visor3d {
	readonly src = input.required<string>();
	readonly alt = input.required<string>();
	// La foto que se ve mientras descarga el visor y el modelo, o null
	readonly poster = input<string | null>(null);
	// «Ver en su espacio»: model-viewer lo muestra solo donde hay soporte (HU-16 RF-8)
	readonly realidadAumentada = input(false);

	protected readonly movimientoReducido = inject(MOVIMIENTO_REDUCIDO)();
	// El foco de model-viewer vive en su shadow DOM: desde afuera, :focus-visible no distingue el teclado del mouse
	protected readonly focoConTeclado = signal(false);

	private readonly documento = inject(DOCUMENT);
	private readonly marco = viewChild<ElementRef<HTMLElement>>('marco');
	// Safari del iPhone solo la da a los videos: ahí no hay botón (HU-16 RF-12)
	protected readonly pantallaCompletaPermitida = this.documento.fullscreenEnabled === true;
	// Lo dice el navegador, no el clic: así salir con Esc o el gesto atrás deja bien el botón (HU-16 RF-14)
	protected readonly ampliado = signal(false);
	protected readonly etiquetaPantallaCompleta = computed(() =>
		this.ampliado() ? 'Salir de pantalla completa' : 'Pantalla completa',
	);
	protected readonly textosDelVisor = TEXTOS_DEL_VISOR;
	// Con movimiento reducido, en lugar de la mano: debajo del visor, o sobre el modelo en pantalla completa
	protected readonly textoDeGestos =
		'Arrastre para girar el modelo; pellizque o use la rueda para acercarlo. Con el teclado, use las flechas.';

	// null mientras se prepara el visor
	protected readonly disponible = signal<boolean | null>(null);
	// Cada modelo nuevo vuelve a cargar
	protected readonly modelo = linkedSignal<string, 'cargando' | 'listo' | 'error'>({
		source: this.src,
		computation: () => 'cargando',
	});

	// El contorno de la tienda solo con el teclado, como en el resto de la página (revisión de interfaz)
	protected alEnfocar(evento: FocusEvent): void {
		const interno = (evento.currentTarget as HTMLElement).shadowRoot?.activeElement;
		this.focoConTeclado.set(interno?.matches(':focus-visible') ?? false);
	}

	protected alternarPantallaCompleta(): void {
		if (this.ampliado()) {
			this.documento.exitFullscreen().catch(() => undefined);
		} else {
			// Si el navegador la rechaza, el visor queda como estaba (HU-16 RF-13)
			this.marco()?.nativeElement.requestFullscreen().catch(() => undefined);
		}
	}

	protected alCambiarPantallaCompleta(): void {
		this.ampliado.set(this.documento.fullscreenElement === this.marco()?.nativeElement);
	}

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
