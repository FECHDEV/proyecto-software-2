import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { LucideTag } from '@lucide/angular';

import { CategoriasService } from '../../core/api/categorias.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { CategoriaGestion, DatosDeCategoria } from '../../core/modelos/categoria';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { ArchivoOcupado } from '../../shared/archivo/archivo-ocupado';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { enfocarCampo, primerCampoConError } from '../../shared/formularios/primer-campo-con-error';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';

const VACIO: DatosDeCategoria = { nombre: '', descripcion: '' };

// Las mismas reglas que valida el backend en CategoriaRequest; el backend sigue decidiendo
const esquema = schema<DatosDeCategoria>((ruta) => {
	required(ruta.nombre, { message: 'Escriba el nombre de la categoría.' });
	sinEspaciosSolos(ruta.nombre, 'Escriba el nombre de la categoría.');
	maxLength(ruta.nombre, 60, { message: 'El nombre admite hasta 60 caracteres.' });
	maxLength(ruta.descripcion, 300, { message: 'La descripción admite hasta 300 caracteres.' });
});

// Gestión de las categorías del catálogo (HU-06). Solo la ve el Administrador:
// el backend lo valida en cada petición, la ruta solo evita mostrarla a otros.
@Component({
	selector: 'app-categorias',
	imports: [FormField, ArchivoOcupado, DialogoConfirmacion, LucideTag],
	templateUrl: './categorias.page.html',
	styleUrl: './categorias.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoriasPage {
	private readonly categorias = inject(CategoriasService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

	protected readonly recurso = this.categorias.gestion();
	protected readonly lista = computed(() => (this.recurso.hasValue() ? (this.recurso.value() ?? []) : []));

	private readonly datos = signal<DatosDeCategoria>({ ...VACIO });
	protected readonly formulario = form(this.datos, esquema);
	// La categoría que se está editando; null mientras se crea una nueva
	protected readonly editando = signal<CategoriaGestion | null>(null);
	protected readonly guardando = signal(false);
	protected readonly errorDelNombre = signal('');
	protected readonly aviso = signal('');
	// Cada imagen se pide con un sufijo nuevo después de cambiarla, para no ver la anterior guardada en el navegador
	protected readonly versionDeImagen = signal(Date.now());
	// La categoría cuya imagen se está subiendo
	protected readonly subiendoImagen = signal<number | null>(null);
	// Las categorías con una operación en curso: un segundo toque no manda otra (revisión total). El botón no se
	// deshabilita, para no perder el foco
	private readonly enCurso = signal<ReadonlySet<number>>(new Set());

	protected urlDeImagen(categoria: CategoriaGestion): string {
		return `${this.categorias.urlDeImagen(categoria.idCategoria)}?v=${this.versionDeImagen()}`;
	}

	protected mensajeDelNombre(): string {
		const campo = this.formulario.nombre();
		if (campo.touched() && campo.invalid()) {
			return campo.errors()[0]?.message ?? '';
		}
		return this.errorDelNombre();
	}

	protected editar(categoria: CategoriaGestion): void {
		this.editando.set(categoria);
		this.datos.set({ nombre: categoria.nombre, descripcion: categoria.descripcion ?? '' });
		this.limpiarAvisos();
	}

	protected cancelarEdicion(): void {
		this.editando.set(null);
		this.formulario().reset({ ...VACIO });
		this.limpiarAvisos();
	}

	protected async guardar(evento: Event): Promise<void> {
		evento.preventDefault();
		if (this.guardando()) {
			return;
		}
		this.limpiarAvisos();
		this.guardando.set(true);
		await submit(this.formulario, {
			action: async (formulario) => {
				const datos = formulario().value();
				const editando = this.editando();
				try {
					if (editando === null) {
						await this.categorias.crear(datos);
					} else {
						await this.categorias.editar(editando.idCategoria, datos);
					}
					this.cancelarEdicion();
					this.recurso.reload();
				} catch (error: unknown) {
					this.mostrarError(comoError(error));
				}
				return undefined;
			},
		});
		this.guardando.set(false);
		// Como en los demás formularios: el foco va al primer campo con error (revisión total)
		if (this.formulario().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(['nombre', 'descripcion'] as const, {
					nombre: this.formulario.nombre().invalid(),
					descripcion: this.formulario.descripcion().invalid(),
				}),
			);
		}
	}

	protected ocupada(categoria: CategoriaGestion): boolean {
		return this.enCurso().has(categoria.idCategoria);
	}

	protected async cambiarVisibilidad(categoria: CategoriaGestion): Promise<void> {
		await this.operar(categoria, () => this.categorias.cambiarVisibilidad(categoria.idCategoria, !categoria.visible));
	}

	// Mientras sube, el campo queda ocupado (ArchivoOcupado) y un change se ignora: dos subidas a la vez dejarían la
	// imagen de la que termine última
	protected async elegirImagen(categoria: CategoriaGestion, evento: Event): Promise<void> {
		const entrada = evento.target as HTMLInputElement;
		const archivo = entrada.files?.[0];
		if (archivo === undefined || this.subiendoImagen() === categoria.idCategoria) {
			return;
		}
		// Otra operación de la categoría en curso: no se sube, y el campo queda para elegir de nuevo
		if (this.ocupada(categoria)) {
			entrada.value = '';
			return;
		}
		this.subiendoImagen.set(categoria.idCategoria);
		// Solo si se cambió: una subida rechazada no hace volver a pedir las imágenes
		if (await this.operar(categoria, () => this.categorias.cambiarImagen(categoria.idCategoria, archivo))) {
			this.versionDeImagen.set(Date.now());
		}
		this.subiendoImagen.set(null);
		// El mismo archivo se puede volver a elegir después de un error
		entrada.value = '';
	}

	protected async quitarImagen(categoria: CategoriaGestion): Promise<void> {
		await this.operar(categoria, () => this.categorias.quitarImagen(categoria.idCategoria));
	}

	// Una categoría con productos no se borra: se ofrece ocultarla (HU-07 RF-12, HU-06 RF-10). El conteo de la
	// lista puede estar viejo, así que el rechazo del backend lleva al mismo ofrecimiento
	protected async borrar(categoria: CategoriaGestion): Promise<void> {
		if (this.ocupada(categoria)) {
			return;
		}
		if (categoria.productos > 0) {
			await this.ofrecerOcultar(categoria);
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: `¿Borrar «${categoria.nombre}»?`,
			mensaje: 'La categoría y su imagen se borran para siempre.',
			confirmar: 'Borrar',
		});
		if (!confirmado) {
			return;
		}
		this.aviso.set('');
		this.marcar(categoria, true);
		try {
			await this.categorias.borrar(categoria.idCategoria);
			this.recurso.reload();
			// Si era la que se estaba editando, ya no hay nada que editar
			if (this.editando()?.idCategoria === categoria.idCategoria) {
				this.cancelarEdicion();
			}
			this.marcar(categoria, false);
		} catch (error: unknown) {
			this.marcar(categoria, false);
			const problema = comoError(error);
			if (problema.codigo === 'CATEGORIA_CON_PRODUCTOS') {
				this.recurso.reload();
				await this.ofrecerOcultar(categoria);
			} else {
				this.aviso.set(mensajeDeError(problema));
			}
		}
	}

	private async ofrecerOcultar(categoria: CategoriaGestion): Promise<void> {
		if (!categoria.visible) {
			this.aviso.set(`«${categoria.nombre}» tiene productos y no se puede borrar. Ya está oculta.`);
			return;
		}
		const ocultar = await this.dialogo().abrir({
			titulo: `«${categoria.nombre}» tiene productos`,
			mensaje:
				'Una categoría con productos no se borra, para no perder su historial. ' +
				'Puede ocultarla: sus productos se conservan.',
			confirmar: 'Ocultar',
		});
		if (ocultar) {
			await this.operar(categoria, () => this.categorias.cambiarVisibilidad(categoria.idCategoria, false));
		}
	}

	// Una operación sobre una fila, una a la vez: si sale bien se vuelve a pedir la lista; si no, se dice por qué.
	// Responde si salió bien
	private async operar(categoria: CategoriaGestion, operacion: () => Promise<unknown>): Promise<boolean> {
		if (this.ocupada(categoria)) {
			return false;
		}
		this.aviso.set('');
		this.marcar(categoria, true);
		try {
			await operacion();
			this.recurso.reload();
			return true;
		} catch (error: unknown) {
			this.aviso.set(mensajeDeError(comoError(error)));
			return false;
		} finally {
			this.marcar(categoria, false);
		}
	}

	private marcar(categoria: CategoriaGestion, ocupada: boolean): void {
		this.enCurso.update((actual) => {
			const nuevo = new Set(actual);
			if (ocupada) {
				nuevo.add(categoria.idCategoria);
			} else {
				nuevo.delete(categoria.idCategoria);
			}
			return nuevo;
		});
	}

	private mostrarError(error: ErrorApi): void {
		if (error.codigo === 'CATEGORIA_EXISTENTE') {
			this.errorDelNombre.set(mensajeDeError(error));
			enfocarCampo(this.elemento.nativeElement, 'nombre');
		} else {
			this.aviso.set(mensajeDeError(error));
		}
	}

	private limpiarAvisos(): void {
		this.errorDelNombre.set('');
		this.aviso.set('');
	}
}
