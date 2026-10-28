import {
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	input,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { LucideBox, LucideChevronLeft, LucideChevronRight, LucideImage, LucideWrench, LucideX } from '@lucide/angular';

import { Visor3d } from '../visor-3d/visor-3d';

// Una foto en sus versiones, ya armadas por la pantalla (HU-22): la grande (src y srcset), la miniatura y el original
// para la pantalla completa
export interface FotoDeGaleria {
	src: string;
	srcset: string;
	miniatura: string;
	original: string;
}

// Lo que hay que deslizar con el dedo para pasar de foto; menos es un toque
const DESLIZAMIENTO = 40;

// Las fotos de un producto (HU-10 RF-5, RF-6): la elegida grande, miniaturas
// para cambiar y pantalla completa en un <dialog> con showModal(), que atrapa
// el foco y se cierra con Escape. No da la vuelta: se detiene en la primera y
// en la última. Recibe las direcciones ya armadas: shared/ no conoce la API.
// Con modelo 3D (HU-16), «Ver en 3D» cambia la foto grande por el visor
@Component({
	selector: 'app-galeria',
	imports: [LucideBox, LucideChevronLeft, LucideChevronRight, LucideImage, LucideWrench, LucideX, Visor3d],
	templateUrl: './galeria.html',
	styleUrl: './galeria.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Galeria {
	readonly fotos = input.required<FotoDeGaleria[]>();
	// Para el texto alternativo: «Taladro, foto 1 de 3»
	readonly nombre = input.required<string>();
	// La dirección del modelo 3D, o null sin modelo (HU-16)
	readonly modelo = input<string | null>(null);

	private readonly dialogo = viewChild<ElementRef<HTMLDialogElement>>('dialogo');
	private readonly grande = viewChild<ElementRef<HTMLButtonElement>>('grande');

	// Las fotos por su contenido: la misma lista leída otra vez no es otro producto (/code-review, ronda 2)
	private readonly mismasFotos = computed(() => this.fotos(), {
		equal: (antes, ahora) =>
			antes.length === ahora.length && antes.every((foto, indice) => foto.original === ahora[indice].original),
	});

	// Con otras fotos (otro producto), vuelve a la primera y olvida las rotas
	protected readonly actual = linkedSignal({ source: this.mismasFotos, computation: () => 0 });
	protected readonly rotas = linkedSignal<FotoDeGaleria[], ReadonlySet<number>>({
		source: this.mismasFotos,
		computation: () => new Set<number>(),
	});
	// Los originales que ya bajaron: mientras tanto, la pantalla completa muestra la de 1200, que ya está en caché
	private readonly originalesCargados = linkedSignal<FotoDeGaleria[], ReadonlySet<number>>({
		source: this.mismasFotos,
		computation: () => new Set<number>(),
	});

	// Con otro producto vuelve a las fotos (HU-16 RF-6). La dirección del modelo lleva el producto: la misma lista
	// de fotos con otra identidad no saca al visitante del modelo (/code-review)
	protected readonly vista = linkedSignal<string | null, 'fotos' | '3d'>({
		source: this.modelo,
		computation: () => 'fotos',
	});

	protected readonly total = computed(() => this.fotos().length);
	// El ancho de la foto grande en pantalla, para elegir entre sus versiones (HU-22): toda la pantalla hasta que el
	// detalle pasa a dos columnas (detalle.page.scss, 860 px)
	protected readonly tamanos = '(max-width: 859px) 100vw, 620px';
	// Hasta ahí, la pantalla completa usa la de 1200 en lugar del original (HU-22 RF-11)
	protected readonly pantallaAngosta = '(max-width: 859px)';
	// El original pesa hasta 2 MB: la foto de la pantalla completa entra a la página recién al abrirla (HU-22)
	protected readonly ampliada = signal(false);
	protected readonly alInicio = computed(() => this.actual() === 0);
	protected readonly alFinal = computed(() => this.actual() >= this.total() - 1);

	// El dedo (o el puntero) que está deslizando; con otro dedo, no cuenta
	private inicioDelDedo: { id: number; x: number } | null = null;
	// Un deslizamiento termina con un clic: no es tocar el fondo
	private recienDeslizo = false;

	protected alt(indice: number): string {
		return `${this.nombre()}, foto ${indice + 1} de ${this.total()}`;
	}

	protected rota(indice: number): boolean {
		return this.rotas().has(indice);
	}

	protected originalCargado(indice: number): boolean {
		return this.originalesCargados().has(indice);
	}

	// El respaldo se quita cuando el original ya se puede pintar: load avisa que bajó, decode() que está decodificado
	// (una foto de 2 MB tarda en decodificarse y quedaría el fondo vacío unos cuadros; /code-review)
	protected alCargarOriginal(evento: Event, indice: number): void {
		const imagen = evento.target as HTMLImageElement;
		const lista = typeof imagen.decode === 'function' ? imagen.decode() : Promise.resolve();
		lista.catch(() => undefined).finally(() => this.marcarCargado(indice));
	}

	private marcarCargado(indice: number): void {
		this.originalesCargados.update((cargados) => new Set(cargados).add(indice));
	}

	protected marcarRota(indice: number): void {
		this.rotas.update((rotas) => new Set(rotas).add(indice));
	}

	// Una miniatura, también desde el modelo, muestra esa foto (HU-16 RF-6)
	protected elegir(indice: number): void {
		this.actual.set(indice);
		this.vista.set('fotos');
	}

	protected cambiarVista(): void {
		this.vista.update((vista) => (vista === 'fotos' ? '3d' : 'fotos'));
	}

	protected ampliar(): void {
		this.ampliada.set(true);
		// «Cerrar» es el primer botón: showModal() le da el foco una sola vez
		this.dialogo()?.nativeElement.showModal();
	}

	protected cerrar(): void {
		this.dialogo()?.nativeElement.close();
	}

	// Por cualquier cierre (botón, fondo o Escape): el foco vuelve a la foto
	protected alCerrar(): void {
		this.ampliada.set(false);
		this.grande()?.nativeElement.focus();
	}

	protected anterior(): void {
		if (!this.alInicio()) {
			this.actual.update((indice) => indice - 1);
		}
	}

	protected siguiente(): void {
		if (!this.alFinal()) {
			this.actual.update((indice) => indice + 1);
		}
	}

	protected alTeclear(evento: KeyboardEvent): void {
		if (evento.key === 'ArrowLeft') {
			evento.preventDefault();
			this.anterior();
		} else if (evento.key === 'ArrowRight') {
			evento.preventDefault();
			this.siguiente();
		}
	}

	// Lo oscuro alrededor de la foto es el fondo: todo lo que no es la foto ni
	// un botón cierra
	protected alTocar(evento: MouseEvent): void {
		if (this.recienDeslizo) {
			this.recienDeslizo = false;
			return;
		}
		const destino = evento.target;
		if (destino instanceof Element && destino.closest('img, button, .icono-foto') === null) {
			this.cerrar();
		}
	}

	protected alApoyar(evento: PointerEvent): void {
		if (evento.isPrimary === false) {
			return;
		}
		this.inicioDelDedo = { id: evento.pointerId, x: evento.clientX };
		// El final llega aunque el dedo salga de la foto
		const zona = evento.currentTarget;
		if (zona instanceof Element && typeof zona.setPointerCapture === 'function' && evento.pointerId !== undefined) {
			zona.setPointerCapture(evento.pointerId);
		}
	}

	protected alSoltar(evento: PointerEvent): void {
		if (this.inicioDelDedo === null || evento.pointerId !== this.inicioDelDedo.id) {
			return;
		}
		const recorrido = evento.clientX - this.inicioDelDedo.x;
		this.inicioDelDedo = null;
		if (Math.abs(recorrido) < DESLIZAMIENTO) {
			return;
		}
		this.recienDeslizo = true;
		if (recorrido < 0) {
			this.siguiente();
		} else {
			this.anterior();
		}
	}

	// Un toque nuevo, en cualquier parte: el deslizamiento anterior ya terminó
	// (en el celular no siempre deja un clic que borre la marca)
	protected alTocarLaPantalla(evento: PointerEvent): void {
		// Un segundo dedo (un pellizco) no es un toque nuevo, como en alApoyar
		if (evento.isPrimary !== false) {
			this.recienDeslizo = false;
		}
	}

	protected alCancelar(): void {
		this.inicioDelDedo = null;
	}
}
