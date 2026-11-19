import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { form, FormField, required, schema, submit, validate } from '@angular/forms/signals';

import { CategoriasService } from '../../core/api/categorias.service';
import { DescuentosService } from '../../core/api/descuentos.service';
import { mensajeDelCampo, mensajeDelRechazo } from '../../core/api/mensajes-error';
import { ProductosService } from '../../core/api/productos.service';
import {
	DIAS_MAXIMOS,
	Descuento,
	DescuentoGuardado,
	NOMBRES_DE_ESTADO_DESCUENTO,
	delObjetivo,
	objetivoDe,
	PORCENTAJE_MAXIMO,
	PORCENTAJE_MINIMO,
} from '../../core/modelos/descuento';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { hoyEnBolivia } from '../../core/modelos/reportes';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { fechaCorta } from '../../shared/formato/fechas';
import { enfocarCampo, primerCampoConError, primerCampoRechazado } from '../../shared/formularios/primer-campo-con-error';
import { ultimoValor } from '../../shared/recursos/ultimo-valor';

type Destino = 'producto' | 'categoria';

// Lo que edita el formulario: el porcentaje como se escribe, las fechas como AAAA-MM-DD
interface DatosDeDescuento {
	porcentaje: string;
	inicio: string;
	fin: string;
}

const CAMPOS = ['porcentaje', 'inicio', 'fin'] as const;
const UN_DIA = 24 * 60 * 60 * 1000;

function diasEntre(desde: string, hasta: string): number {
	return Math.round((Date.parse(`${hasta}T00:00:00Z`) - Date.parse(`${desde}T00:00:00Z`)) / UN_DIA);
}

// Las mismas reglas que DescuentoRequest y DescuentoService (RF-1); el backend sigue decidiendo. hoy, el de Bolivia
function esquemaDesde(hoy: () => string) {
	return schema<DatosDeDescuento>((ruta) => {
		required(ruta.porcentaje, { message: 'Escriba el porcentaje.' });
		validate(ruta.porcentaje, ({ value }) => {
			const texto = value().trim();
			if (texto === '') {
				return null;
			}
			const numero = Number(texto);
			return /^\d+$/.test(texto) && numero >= PORCENTAJE_MINIMO && numero <= PORCENTAJE_MAXIMO
				? null
				: {
						kind: 'porcentaje',
						message: `El porcentaje es un número entero de ${PORCENTAJE_MINIMO} a ${PORCENTAJE_MAXIMO}.`,
					};
		});
		required(ruta.inicio, { message: 'Elija la fecha de inicio.' });
		validate(ruta.inicio, ({ value }) =>
			value() !== '' && value() < hoy()
				? { kind: 'pasado', message: 'La fecha de inicio no puede ser anterior a hoy.' }
				: null,
		);
		required(ruta.fin, { message: 'Elija la fecha de fin.' });
		validate(ruta.fin, ({ value, valueOf }) => {
			const inicio = valueOf(ruta.inicio);
			if (value() === '' || inicio === '') {
				return null;
			}
			if (value() < inicio) {
				return { kind: 'antes', message: 'La fecha de fin no puede ser anterior a la de inicio.' };
			}
			return diasEntre(inicio, value()) > DIAS_MAXIMOS
				? { kind: 'largo', message: `Un descuento dura como máximo ${DIAS_MAXIMOS} días.` }
				: null;
		});
	});
}

// Los descuentos por producto o por categoría (HU-24 RF-1 a RF-6). Solo la ve el Administrador: el backend lo valida
// en cada petición (RF-7), la ruta solo evita mostrarla a otros
@Component({
	selector: 'app-descuentos',
	imports: [FormField, DialogoConfirmacion],
	templateUrl: './descuentos.page.html',
	styleUrl: './descuentos.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DescuentosPage {
	private readonly descuentos = inject(DescuentosService);
	private readonly productos = inject(ProductosService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO_DESCUENTO;
	protected readonly objetivoDe = objetivoDe;
	protected readonly delObjetivo = delObjetivo;
	protected readonly fechaCorta = fechaCorta;
	protected readonly hoy = signal(hoyEnBolivia());

	protected readonly pagina = signal(0);
	protected readonly listado = this.descuentos.listar(this.pagina);
	// La página anterior sigue a la vista mientras carga la siguiente
	protected readonly lista = ultimoValor(this.listado);
	// Se borró o terminó el último de una página que no es la primera (o lo hizo otro Administrador): la página quedó
	// vacía y sin navegación; se vuelve a la última que hay (/code-review)
	private readonly volverSiQuedoVacia = effect(() => {
		const lista = this.lista();
		if (lista !== undefined && lista.contenido.length === 0 && lista.pagina > 0) {
			this.pagina.set(Math.max(0, Math.min(lista.pagina - 1, lista.totalPaginas - 1)));
		}
	});

	protected readonly categorias = inject(CategoriasService).gestion();

	// A qué se aplica (solo al crear: al editar no cambia, RF-4)
	protected readonly destino = signal<Destino>('producto');
	protected readonly textoDeProducto = signal('');
	private readonly buscado = signal('');
	protected readonly encontrados = this.productos.buscarActivos(this.buscado);
	protected readonly producto = signal<{ idProducto: number; nombre: string } | null>(null);
	protected readonly idCategoria = signal<number | null>(null);
	protected readonly errorDelDestino = signal('');

	private readonly datos = signal<DatosDeDescuento>({ porcentaje: '', inicio: this.hoy(), fin: '' });
	protected readonly formulario = form(
		this.datos,
		esquemaDesde(() => this.hoy()),
	);
	// El descuento que se está editando; null mientras se crea uno nuevo
	protected readonly editando = signal<Descuento | null>(null);
	protected readonly guardando = signal(false);
	private readonly rechazo = signal<ErrorApi | null>(null);
	protected readonly aviso = signal('');
	// RF-6: se guardó, pero estos productos quedan por debajo de su costo
	protected readonly bajoElCosto = signal<DescuentoGuardado | null>(null);
	// Los descuentos con una operación en curso: un segundo toque no manda otra
	private readonly enCurso = signal<ReadonlySet<number>>(new Set());

	protected readonly titulo = computed(() => {
		const editando = this.editando();
		return editando === null ? 'Nuevo descuento' : `Editar el descuento de ${delObjetivo(editando)}`;
	});

	protected mensajeDe(campo: (typeof CAMPOS)[number]): string {
		return mensajeDelCampo(this.formulario[campo](), campo, this.rechazo());
	}

	protected elegirDestino(destino: Destino): void {
		this.destino.set(destino);
		this.errorDelDestino.set('');
	}

	protected buscarProducto(evento?: Event): void {
		evento?.preventDefault();
		this.buscado.set(this.textoDeProducto());
	}

	protected elegirProducto(producto: { idProducto: number; nombre: string }): void {
		this.producto.set({ idProducto: producto.idProducto, nombre: producto.nombre });
		this.errorDelDestino.set('');
		this.buscado.set('');
		enfocarCampo(this.elemento.nativeElement, 'porcentaje');
	}

	protected cambiarProducto(): void {
		this.producto.set(null);
		enfocarCampo(this.elemento.nativeElement, 'texto-producto');
	}

	protected editar(descuento: Descuento): void {
		this.limpiarAvisos();
		this.editando.set(descuento);
		this.datos.set({ porcentaje: String(descuento.porcentaje), inicio: descuento.inicio, fin: descuento.fin });
		enfocarCampo(this.elemento.nativeElement, 'porcentaje');
	}

	// El foco vuelve al «Editar» de esa fila: «Cancelar» desaparece con la edición (revisión de interfaz)
	protected cancelarEdicion(): void {
		const editado = this.editando();
		this.editando.set(null);
		if (editado !== null) {
			enfocarCampo(this.elemento.nativeElement, `editar-${editado.idDescuento}`);
		}
		this.formulario().reset({ porcentaje: '', inicio: this.hoy(), fin: '' });
		this.rechazo.set(null);
	}

	protected async guardar(evento: Event): Promise<void> {
		evento.preventDefault();
		if (this.guardando()) {
			return;
		}
		this.limpiarAvisos();
		this.hoy.set(hoyEnBolivia());
		const editando = this.editando();
		const destinoValido = editando !== null || this.validarDestino();
		this.guardando.set(true);
		await submit(this.formulario, {
			action: async (formulario) => {
				if (!destinoValido) {
					return undefined;
				}
				const { porcentaje, inicio, fin } = formulario().value();
				const cambio = { porcentaje: Number(porcentaje.trim()), inicio, fin };
				try {
					const guardado =
						editando === null
							? await this.descuentos.crear({
									idProducto: this.destino() === 'producto' ? (this.producto()?.idProducto ?? null) : null,
									idCategoria: this.destino() === 'categoria' ? this.idCategoria() : null,
									...cambio,
								})
							: await this.descuentos.cambiar(editando.idDescuento, cambio);
					this.alGuardar(guardado);
				} catch (error: unknown) {
					this.mostrarRechazo(comoError(error));
				}
				return undefined;
			},
		});
		this.guardando.set(false);
		if (!destinoValido) {
			enfocarCampo(
				this.elemento.nativeElement,
				this.destino() === 'producto' ? 'texto-producto' : 'categoria',
			);
		} else if (this.formulario().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(CAMPOS, {
					porcentaje: this.formulario.porcentaje().invalid(),
					inicio: this.formulario.inicio().invalid(),
					fin: this.formulario.fin().invalid(),
				}),
			);
		}
	}

	protected ocupado(descuento: Descuento): boolean {
		return this.enCurso().has(descuento.idDescuento);
	}

	// RF-4: solo uno programado, que todavía no se cobró
	protected async borrar(descuento: Descuento): Promise<void> {
		const confirmado = await this.dialogo().abrir({
			titulo: `¿Borrar el descuento de ${delObjetivo(descuento)}?`,
			mensaje: `El −${descuento.porcentaje} % del ${fechaCorta(descuento.inicio)} al ${fechaCorta(descuento.fin)} no se aplicará.`,
			confirmar: 'Borrar',
		});
		if (confirmado) {
			await this.operar(descuento, () => this.descuentos.borrar(descuento.idDescuento));
		}
	}

	// RF-5: uno vigente termina ayer y la tienda vuelve al precio normal; lo vendido no cambia
	protected async terminar(descuento: Descuento): Promise<void> {
		const confirmado = await this.dialogo().abrir({
			titulo: `¿Terminar el descuento de ${delObjetivo(descuento)}?`,
			mensaje: 'La tienda vuelve al precio normal en el acto. Lo ya vendido conserva su precio.',
			confirmar: 'Terminar',
		});
		if (confirmado) {
			await this.operar(descuento, () => this.descuentos.terminar(descuento.idDescuento));
		}
	}

	protected irAPagina(pagina: number): void {
		const total = this.lista()?.totalPaginas ?? 0;
		if (this.listado.isLoading() || pagina < 0 || (pagina > 0 && pagina >= total)) {
			return;
		}
		this.pagina.set(pagina);
	}

	private validarDestino(): boolean {
		if (this.destino() === 'producto' && this.producto() === null) {
			this.errorDelDestino.set('Busque y elija el producto.');
			return false;
		}
		if (this.destino() === 'categoria' && this.idCategoria() === null) {
			this.errorDelDestino.set('Elija la categoría.');
			return false;
		}
		return true;
	}

	private alGuardar(guardado: DescuentoGuardado): void {
		if (guardado.totalBajoElCosto > 0) {
			this.bajoElCosto.set(guardado);
		}
		this.cancelarEdicion();
		this.producto.set(null);
		this.textoDeProducto.set('');
		this.idCategoria.set(null);
		this.listado.reload();
		this.aviso.set('Descuento guardado.');
	}

	private mostrarRechazo(error: ErrorApi): void {
		this.rechazo.set(error);
		const campo = primerCampoRechazado(CAMPOS, error.errores);
		if (campo !== null) {
			enfocarCampo(this.elemento.nativeElement, campo);
			return;
		}
		// Ya empezó, terminó o no existe: la lista vieja se vuelve a pedir
		if (error.codigo === 'DESCUENTO_NO_EDITABLE' || error.codigo === 'DESCUENTO_NO_ENCONTRADO') {
			this.cancelarEdicion();
			this.listado.reload();
		}
		this.aviso.set(mensajeDelRechazo(error));
	}

	// Una operación sobre una fila, una a la vez: si sale bien se vuelve a pedir la lista; si no, se dice por qué
	private async operar(descuento: Descuento, operacion: () => Promise<void>): Promise<void> {
		if (this.ocupado(descuento)) {
			return;
		}
		this.limpiarAvisos();
		this.marcar(descuento, true);
		try {
			await operacion();
			if (this.editando()?.idDescuento === descuento.idDescuento) {
				this.cancelarEdicion();
			}
		} catch (error: unknown) {
			this.aviso.set(mensajeDelRechazo(comoError(error)));
		} finally {
			this.marcar(descuento, false);
			this.listado.reload();
		}
	}

	private marcar(descuento: Descuento, ocupado: boolean): void {
		this.enCurso.update((actual) => {
			const nuevo = new Set(actual);
			if (ocupado) {
				nuevo.add(descuento.idDescuento);
			} else {
				nuevo.delete(descuento.idDescuento);
			}
			return nuevo;
		});
	}

	private limpiarAvisos(): void {
		this.aviso.set('');
		this.bajoElCosto.set(null);
		this.rechazo.set(null);
		this.errorDelDestino.set('');
	}
}
