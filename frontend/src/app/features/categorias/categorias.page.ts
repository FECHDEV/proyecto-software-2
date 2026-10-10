import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { LucideTag } from '@lucide/angular';

import { CategoriasService } from '../../core/api/categorias.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { CategoriaGestion, DatosDeCategoria } from '../../core/modelos/categoria';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';

const VACIO: DatosDeCategoria = { nombre: '', descripcion: '' };

// Las mismas reglas que valida el backend en CategoriaRequest; el backend sigue decidiendo
const esquema = schema<DatosDeCategoria>((ruta) => {
	required(ruta.nombre, { message: 'Escribe el nombre de la categoría.' });
	sinEspaciosSolos(ruta.nombre, 'Escribe el nombre de la categoría.');
	maxLength(ruta.nombre, 60, { message: 'El nombre admite hasta 60 caracteres.' });
	maxLength(ruta.descripcion, 300, { message: 'La descripción admite hasta 300 caracteres.' });
});

// Gestión de las categorías del catálogo (HU-06). Solo la ve el Administrador:
// el backend lo valida en cada petición, la ruta solo evita mostrarla a otros.
@Component({
	selector: 'app-categorias',
	imports: [FormField, DialogoConfirmacion, LucideTag],
	templateUrl: './categorias.page.html',
	styleUrl: './categorias.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoriasPage {
	private readonly categorias = inject(CategoriasService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);

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
	}

	protected async cambiarVisibilidad(categoria: CategoriaGestion): Promise<void> {
		await this.operar(() => this.categorias.cambiarVisibilidad(categoria.idCategoria, !categoria.visible));
	}

	protected async elegirImagen(categoria: CategoriaGestion, evento: Event): Promise<void> {
		const entrada = evento.target as HTMLInputElement;
		const archivo = entrada.files?.[0];
		if (archivo === undefined) {
			return;
		}
		// Solo si se cambió: una subida rechazada no hace volver a pedir las imágenes
		if (await this.operar(() => this.categorias.cambiarImagen(categoria.idCategoria, archivo))) {
			this.versionDeImagen.set(Date.now());
		}
		// El mismo archivo se puede volver a elegir después de un error
		entrada.value = '';
	}

	protected async quitarImagen(categoria: CategoriaGestion): Promise<void> {
		await this.operar(() => this.categorias.quitarImagen(categoria.idCategoria));
	}

	protected async borrar(categoria: CategoriaGestion): Promise<void> {
		const confirmado = await this.dialogo().abrir({
			titulo: `¿Borrar «${categoria.nombre}»?`,
			mensaje: 'La categoría y su imagen se borran para siempre.',
			confirmar: 'Borrar',
		});
		if (confirmado && (await this.operar(() => this.categorias.borrar(categoria.idCategoria)))) {
			// Si era la que se estaba editando, ya no hay nada que editar
			if (this.editando()?.idCategoria === categoria.idCategoria) {
				this.cancelarEdicion();
			}
		}
	}

	// Una operación sobre una fila: si sale bien se vuelve a pedir la lista; si no, se dice por qué.
	// Responde si salió bien
	private async operar(operacion: () => Promise<unknown>): Promise<boolean> {
		this.aviso.set('');
		try {
			await operacion();
			this.recurso.reload();
			return true;
		} catch (error: unknown) {
			this.aviso.set(mensajeDeError(comoError(error)));
			return false;
		}
	}

	private mostrarError(error: ErrorApi): void {
		if (error.codigo === 'CATEGORIA_EXISTENTE') {
			this.errorDelNombre.set(mensajeDeError(error));
		} else {
			this.aviso.set(mensajeDeError(error));
		}
	}

	private limpiarAvisos(): void {
		this.errorDelNombre.set('');
		this.aviso.set('');
	}
}
