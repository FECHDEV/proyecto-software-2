import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
	applyEach,
	form,
	FormField,
	maxLength,
	required,
	schema,
	submit,
	validate,
} from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideArrowLeft, LucidePlus, LucideTrash2 } from '@lucide/angular';

import { CategoriasService } from '../../core/api/categorias.service';
import { EstadoDelCampo, mensajeDeError, mensajeDelCampo, primerMensaje } from '../../core/api/mensajes-error';
import { ProductosService } from '../../core/api/productos.service';
import { ConCambiosSinGuardar, SALIR_SIN_GUARDAR } from '../../core/guards/cambios-sin-guardar.guard';
import { comoError, comoErrorApi, ErrorApi } from '../../core/modelos/error-api';
import {
	DatosDeProducto,
	MAXIMO_CARACTERISTICAS,
	MAXIMO_IMAGENES,
	NOMBRES_DE_UNIDAD,
	Producto,
	UNIDADES,
} from '../../core/modelos/producto';
import { ArchivoOcupado } from '../../shared/archivo/archivo-ocupado';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { problemaDelPrecio } from '../../shared/formato/precios';
import {
	enfocarCampo,
	primerCampoConError,
	primerCampoRechazado,
} from '../../shared/formularios/primer-campo-con-error';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';
import { ModeloDeProducto } from './modelo-de-producto';

type Campo = 'nombre' | 'marca' | 'idCategoria' | 'unidad' | 'precio' | 'costo' | 'descripcion';

// En el orden de la pantalla, para llevar el foco al primero con error
const CAMPOS: readonly Campo[] = ['nombre', 'marca', 'idCategoria', 'unidad', 'precio', 'costo', 'descripcion'];

const VACIO: DatosDeProducto = {
	nombre: '',
	marca: '',
	descripcion: '',
	idCategoria: '',
	unidad: 'UNIDAD',
	precio: '',
	costo: '',
	ficha: [],
	destacado: false,
};

// El mismo nombre sin importar mayúsculas, tildes ni espacios alrededor, como lo compara el backend
function clave(nombre: string): string {
	return nombre.trim().normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

// Las reglas de ProductoRequest y ProductoService (HU-07 RF-3, RF-6); el backend sigue decidiendo
const esquema = schema<DatosDeProducto>((ruta) => {
	required(ruta.nombre, { message: 'Escriba el nombre del producto.' });
	sinEspaciosSolos(ruta.nombre, 'Escriba el nombre del producto.');
	maxLength(ruta.nombre, 120, { message: 'El nombre admite hasta 120 caracteres.' });
	maxLength(ruta.marca, 60, { message: 'La marca admite hasta 60 caracteres.' });
	required(ruta.idCategoria, { message: 'Elija una categoría.' });
	required(ruta.precio, { message: 'Escriba el precio.' });
	validate(ruta.precio, ({ value }) => {
		const problema = problemaDelPrecio(value());
		return problema === null ? null : { kind: 'precio', message: problema };
	});
	// Opcional (HU-18 RF-19): vacío no es un error
	validate(ruta.costo, ({ value }) => {
		const problema = problemaDelPrecio(value(), 'costo');
		return problema === null ? null : { kind: 'costo', message: problema };
	});
	maxLength(ruta.descripcion, 1000, { message: 'La descripción admite hasta 1000 caracteres.' });

	// Un par a medio llenar se marca; uno vacío del todo se descarta al enviar
	applyEach(ruta.ficha, (par) => {
		validate(par.nombre, ({ value, valueOf }) =>
			value().trim() === '' && valueOf(par.valor).trim() !== ''
				? { kind: 'required', message: 'Escriba el nombre del dato.' }
				: null,
		);
		validate(par.nombre, ({ value, valueOf }) => {
			const buscado = clave(value());
			const repetidos = valueOf(ruta.ficha).filter((otro) => clave(otro.nombre) === buscado).length;
			return buscado !== '' && repetidos > 1 ? { kind: 'repetido', message: 'Hay otro dato con este nombre.' } : null;
		});
		maxLength(par.nombre, 40, { message: 'El nombre del dato admite hasta 40 caracteres.' });
		validate(par.valor, ({ value, valueOf }) =>
			value().trim() === '' && valueOf(par.nombre).trim() !== ''
				? { kind: 'required', message: 'Escriba el valor.' }
				: null,
		);
		maxLength(par.valor, 100, { message: 'El valor admite hasta 100 caracteres.' });
	});
});

// Alta y edición de un producto (HU-07), en /productos/nuevo y /productos/:id.
// Las fotos se suben con el producto ya creado: necesitan su número.
@Component({
	selector: 'app-producto',
	imports: [FormField, RouterLink, ArchivoOcupado, DialogoConfirmacion, ModeloDeProducto, LucideArrowLeft, LucidePlus, LucideTrash2],
	templateUrl: './producto.page.html',
	styleUrl: './producto.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '(window:beforeunload)': 'alCerrarLaPestana($event)' },
})
export class ProductoPage implements ConCambiosSinGuardar {
	private readonly productos = inject(ProductosService);
	private readonly router = inject(Router);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly injector = inject(Injector);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	// Aparte: salir con «Atrás» mientras otra confirmación está abierta (quitar una foto) no la responde ni la pisa
	private readonly dialogoDeSalida = viewChild.required<DialogoConfirmacion>('salida');

	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });
	protected readonly esNuevo = computed(() => this.parametros().get('id') === 'nuevo');
	protected readonly idProducto = computed(() => idDeLaUrl(this.parametros().get('id')));
	// Ni «nuevo» ni un número: no hay producto que mostrar
	protected readonly direccionInvalida = computed(() => !this.esNuevo() && this.idProducto() === null);

	protected readonly recurso = this.productos.producto(this.idProducto);
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		if (error === undefined) {
			return '';
		}
		return this.noExiste() ? 'Ese producto no existe.' : mensajeDeError(comoErrorApi(error));
	});
	// Un producto que no existe no aparece reintentando (revisión total)
	protected readonly noExiste = computed(() => {
		const error = this.recurso.error();
		return error !== undefined && comoErrorApi(error).status === 404;
	});

	// El producto como lo dejó el backend la última vez: al cargar, al guardar y
	// después de cada cambio de fotos. Mientras se vuelve a pedir el mismo
	// producto (recién creado), se conserva el que ya se tiene.
	protected readonly producto = linkedSignal<{ cargado: Producto | undefined; id: number | null }, Producto | undefined>({
		source: () => ({ cargado: this.recurso.hasValue() ? this.recurso.value() : undefined, id: this.idProducto() }),
		computation: ({ cargado, id }, previo) =>
			cargado ?? (previo?.value !== undefined && previo.value.idProducto === id ? previo.value : undefined),
	});

	// Las categorías para elegir, también las ocultas (HU-07: se les pueden asignar productos)
	protected readonly recursoDeCategorias = inject(CategoriasService).gestion();
	protected readonly categorias = computed(() =>
		this.recursoDeCategorias.hasValue() ? (this.recursoDeCategorias.value() ?? []) : [],
	);

	// Lo que el formulario muestra sin cambios: vacío para uno nuevo, lo guardado
	// para uno existente, null mientras carga. Solo cambia si cambian los datos:
	// subir una foto no pisa lo que se está escribiendo.
	private readonly base = computed<DatosDeProducto | null>(
		() => {
			if (this.esNuevo()) {
				return VACIO;
			}
			const producto = this.producto();
			return producto === undefined ? null : aFormulario(producto);
		},
		{ equal: (a, b) => a === b || (a !== null && b !== null && mismosDatos(a, b)) },
	);
	private readonly datos = linkedSignal<DatosDeProducto>(() => this.base() ?? VACIO);
	protected readonly formulario = form(this.datos, esquema);

	protected readonly guardando = signal(false);
	private readonly error = signal<ErrorApi | null>(null);
	private readonly enviados = signal<DatosDeProducto | null>(null);
	// La respuesta del backend habla de lo que se envió: al corregir un dato deja de valer
	private readonly errorVigente = computed(() => {
		const enviados = this.enviados();
		return enviados !== null && mismosDatos(enviados, this.formulario().value()) ? this.error() : null;
	});
	protected readonly avisoDeError = computed(() => mensajeDeError(this.errorVigente()));
	protected readonly avisoDeExito = signal('');

	protected readonly fotos = computed(() => this.producto()?.imagenes ?? []);
	protected readonly operandoFotos = signal(false);
	protected readonly errorDeFotos = signal('');
	protected readonly exitoDeFotos = signal('');

	protected readonly unidades = UNIDADES;
	protected readonly nombresDeUnidad = NOMBRES_DE_UNIDAD;
	protected readonly maximoFotos = MAXIMO_IMAGENES;
	protected readonly maximoDatos = MAXIMO_CARACTERISTICAS;

	// Mientras se pasa de /productos/nuevo a /productos/:id después de crear, no hay nada sin guardar
	private trasCrear = false;
	private readonly hayCambios = computed(() => {
		const base = this.base();
		return base !== null && !mismosDatos(base, this.formulario().value());
	});

	// Lo consulta cambiosSinGuardarGuard antes de navegar a otra pantalla
	tieneCambiosSinGuardar(): boolean {
		return !this.trasCrear && this.hayCambios();
	}

	confirmarSalida(): Promise<boolean> {
		return this.dialogoDeSalida().abrir(SALIR_SIN_GUARDAR);
	}

	// Cerrar la pestaña o recargar: el navegador muestra su propio aviso
	protected alCerrarLaPestana(evento: BeforeUnloadEvent): void {
		if (this.hayCambios()) {
			evento.preventDefault();
		}
	}

	protected mensaje(estado: EstadoDelCampo, campo: Campo): string {
		return mensajeDelCampo(estado, campo, this.errorVigente());
	}

	protected mensajeDelDato(estado: EstadoDelCampo): string {
		return estado.touched() && estado.invalid() ? primerMensaje(estado.errors()) : '';
	}

	protected urlDeFoto(imagen: string): string {
		const producto = this.producto();
		return producto === undefined ? '' : this.productos.urlDeImagen(producto.idProducto, imagen, 400);
	}

	// Agrega una fila vacía a la ficha y lleva el foco a su nombre
	protected agregarDato(): void {
		const posicion = this.datos().ficha.length;
		if (posicion >= MAXIMO_CARACTERISTICAS) {
			return;
		}
		this.datos.update((datos) => ({ ...datos, ficha: [...datos.ficha, { nombre: '', valor: '' }] }));
		afterNextRender(() => enfocarCampo(this.elemento.nativeElement, `dato-nombre-${posicion}`), {
			injector: this.injector,
		});
	}

	// El foco no se pierde con la fila: va al quitar de la que ocupa su lugar, o de la anterior, o a «Agregar dato»
	protected quitarDato(posicion: number): void {
		this.datos.update((datos) => ({ ...datos, ficha: datos.ficha.filter((_, i) => i !== posicion) }));
		const quedan = this.datos().ficha.length;
		const destino = quedan === 0 ? 'agregar-dato' : `quitar-dato-${Math.min(posicion, quedan - 1)}`;
		afterNextRender(() => enfocarCampo(this.elemento.nativeElement, destino), { injector: this.injector });
	}

	protected async guardar(evento: Event): Promise<void> {
		evento.preventDefault();
		if (this.guardando()) {
			return;
		}
		this.error.set(null);
		this.enviados.set(null);
		this.avisoDeExito.set('');
		this.guardando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				const enviados = formulario().value();
				const id = this.idProducto();
				try {
					if (id === null) {
						await this.creado(await this.productos.crear(enviados));
					} else {
						const guardado = await this.productos.editar(id, enviados);
						// Las fotos y el modelo tienen sus propias operaciones, que pudieron responder antes
						this.producto.update((producto) =>
							producto === undefined
								? guardado
								: { ...guardado, imagenes: producto.imagenes, modelo: producto.modelo },
						);
						// Si el backend solo descartó algo (un par vacío, espacios), base no cambia:
						// el formulario se pone igual a lo guardado
						this.datos.set(aFormulario(guardado));
						this.avisoDeExito.set('Cambios guardados.');
					}
				} catch (error: unknown) {
					const problema = comoError(error);
					this.enviados.set(enviados);
					this.error.set(problema);
					enfocarCampo(this.elemento.nativeElement, primerCampoRechazado(CAMPOS, problema.errores));
				}
				return undefined;
			},
		});

		this.guardando.set(false);

		if (this.formulario().invalid()) {
			const campo = primerCampoConError(CAMPOS, {
				nombre: this.formulario.nombre().invalid(),
				marca: this.formulario.marca().invalid(),
				idCategoria: this.formulario.idCategoria().invalid(),
				unidad: this.formulario.unidad().invalid(),
				precio: this.formulario.precio().invalid(),
				costo: this.formulario.costo().invalid(),
				descripcion: this.formulario.descripcion().invalid(),
			});
			enfocarCampo(this.elemento.nativeElement, campo ?? this.primerDatoConError());
		}
	}

	// Mientras sube, el campo queda ocupado (ArchivoOcupado) y un change se ignora
	protected async elegirFoto(evento: Event): Promise<void> {
		const entrada = evento.target as HTMLInputElement;
		const archivo = entrada.files?.[0];
		const producto = this.producto();
		if (archivo !== undefined && producto !== undefined && !this.operandoFotos()) {
			await this.operarFotos(() => this.productos.agregarImagen(producto.idProducto, archivo), 'Foto agregada.');
		}
		// El mismo archivo se puede volver a elegir después de un error
		entrada.value = '';
	}

	// La elegida pasa al primer lugar; las demás conservan su orden (RF-8)
	protected async hacerPrincipal(imagen: string): Promise<void> {
		const producto = this.producto();
		if (producto === undefined) {
			return;
		}
		const orden = [imagen, ...producto.imagenes.filter((cada) => cada !== imagen)];
		await this.operarFotos(
			() => this.productos.reordenar(producto.idProducto, orden),
			'Esa foto ahora es la principal.',
		);
	}

	protected async quitarFoto(imagen: string, posicion: number): Promise<void> {
		const producto = this.producto();
		if (producto === undefined) {
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: `¿Quitar la foto ${posicion + 1}?`,
			mensaje: 'La foto se borra para siempre.',
			confirmar: 'Quitar',
		});
		if (confirmado) {
			await this.operarFotos(() => this.productos.quitarImagen(producto.idProducto, imagen), 'Foto quitada.');
		}
	}

	// Las fotos y el modelo se cambian por separado y pueden responder en otro orden: cada uno toca solo lo suyo,
	// así una respuesta que llega tarde no deshace la otra (/code-review)
	protected alCambiarModelo({ modelo }: Producto): void {
		this.producto.update((producto) => producto && { ...producto, modelo });
	}

	private async creado(producto: Producto): Promise<void> {
		this.producto.set(producto);
		this.avisoDeExito.set(`Producto creado con el código ${producto.codigo}. Ya puede subir sus fotos.`);
		this.trasCrear = true;
		try {
			await this.router.navigate(['/productos', producto.idProducto], { replaceUrl: true });
		} finally {
			this.trasCrear = false;
		}
	}

	private async operarFotos(operacion: () => Promise<Producto>, exito: string): Promise<void> {
		if (this.operandoFotos()) {
			return;
		}
		this.operandoFotos.set(true);
		this.errorDeFotos.set('');
		this.exitoDeFotos.set('');
		try {
			const { imagenes } = await operacion();
			this.producto.update((producto) => producto && { ...producto, imagenes });
			this.exitoDeFotos.set(exito);
			// El botón usado puede desaparecer (la foto quitada, la que pasó a principal): el foco va al título
			afterNextRender(() => enfocarCampo(this.elemento.nativeElement, 'titulo-fotos'), { injector: this.injector });
		} catch (error: unknown) {
			this.errorDeFotos.set(mensajeDeError(comoError(error)));
		} finally {
			this.operandoFotos.set(false);
		}
	}

	private primerDatoConError(): string | null {
		const ficha = this.formulario.ficha;
		for (let i = 0; i < this.datos().ficha.length; i++) {
			if (ficha[i].nombre().invalid()) {
				return `dato-nombre-${i}`;
			}
			if (ficha[i].valor().invalid()) {
				return `dato-valor-${i}`;
			}
		}
		return null;
	}
}

function aFormulario(producto: Producto): DatosDeProducto {
	return {
		nombre: producto.nombre,
		marca: producto.marca ?? '',
		descripcion: producto.descripcion ?? '',
		idCategoria: String(producto.idCategoria),
		unidad: producto.unidad,
		// Con coma, como se escribe en Bolivia; sin decimales de más
		precio: String(producto.precio).replace('.', ','),
		costo: producto.costo === null ? '' : String(producto.costo).replace('.', ','),
		ficha: producto.ficha.map((par) => ({ ...par })),
		destacado: producto.destacado,
	};
}

function mismosDatos(a: DatosDeProducto, b: DatosDeProducto): boolean {
	return (
		a.nombre === b.nombre &&
		a.marca === b.marca &&
		a.descripcion === b.descripcion &&
		a.idCategoria === b.idCategoria &&
		a.unidad === b.unidad &&
		a.precio === b.precio &&
		a.costo === b.costo &&
		a.destacado === b.destacado &&
		a.ficha.length === b.ficha.length &&
		a.ficha.every((par, i) => par.nombre === b.ficha[i].nombre && par.valor === b.ficha[i].valor)
	);
}
