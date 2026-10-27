import {
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	linkedSignal,
	signal,
	viewChild,
	WritableSignal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
	FieldTree,
	form,
	FormField,
	maxLength,
	required,
	SchemaPath,
	submit,
	validate,
} from '@angular/forms/signals';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideCircleSlash, LucideTriangleAlert } from '@lucide/angular';

import { InventarioService } from '../../core/api/inventario.service';
import { mensajeDeError, mensajeDelRechazo, primerMensaje } from '../../core/api/mensajes-error';
import { ConCambiosSinGuardar, SALIR_SIN_GUARDAR } from '../../core/guards/cambios-sin-guardar.guard';
import { comoError, comoErrorApi, ErrorApi } from '../../core/modelos/error-api';
import {
	DECIMALES_DE_UNIDAD,
	descripcionDeMovimiento,
	ENTRADA_MAXIMA,
	MotivoAjuste,
	MOTIVOS,
	Movimiento,
	NOMBRES_DE_MOTIVO,
	ProductoEnInventario,
	STOCK_MAXIMO,
} from '../../core/modelos/inventario';
import { pluralDeUnidad, unidadPara } from '../../core/modelos/producto';
import { SesionService } from '../../core/sesion/sesion.service';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { leerCantidad, problemaDeCantidad, ReglasDeCantidad } from '../../shared/formato/cantidades';
import { fechaCorta, horaCorta } from '../../shared/formato/fechas';
import { cantidad } from '../../shared/formato/precios';
import { enfocarCampo } from '../../shared/formularios/primer-campo-con-error';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';

interface DatosDeEntrada {
	cantidad: string;
	nota: string;
}

interface DatosDeAjuste {
	contada: string;
	motivo: MotivoAjuste;
	nota: string;
}

interface DatosDeMinimo {
	minimo: string;
}

const SIN_ENTRADA: DatosDeEntrada = { cantidad: '', nota: '' };
const SIN_AJUSTE: DatosDeAjuste = { contada: '', motivo: 'CONTEO', nota: '' };

// El stock de un producto, sus movimientos y su historial (HU-08), en
// /inventario/:id. El Contador lo ve sin los formularios (RF-1): el backend
// rechaza igual sus movimientos.
@Component({
	selector: 'app-stock',
	imports: [FormField, RouterLink, LucideArrowLeft, LucideCircleSlash, LucideTriangleAlert, DialogoConfirmacion],
	templateUrl: './stock.page.html',
	styleUrl: './stock.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '(window:beforeunload)': 'alCerrarLaPestana($event)' },
})
export class StockPage implements ConCambiosSinGuardar {
	private readonly inventario = inject(InventarioService);
	private readonly sesion = inject(SesionService);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);

	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });
	protected readonly idProducto = computed(() => idDeLaUrl(this.parametros().get('id')));

	protected readonly recurso = this.inventario.producto(this.idProducto);
	protected readonly producto = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
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

	// Otro producto empieza su historial por la página más reciente
	protected readonly paginaDelHistorial = linkedSignal<number | null, number>({
		source: () => this.idProducto(),
		computation: () => 0,
	});
	protected readonly historial = this.inventario.historial(this.idProducto, this.paginaDelHistorial);
	protected readonly movimientos = computed(() => (this.historial.hasValue() ? this.historial.value() : undefined));

	protected readonly puedeRegistrar = computed(() => {
		const rol = this.sesion.rol();
		return rol === 'EMPLEADO' || rol === 'ADMINISTRADOR';
	});

	// Los decimales de la unidad del producto: 0 hasta que carga
	private readonly decimales = computed(() => {
		const producto = this.producto();
		return producto === undefined ? 0 : DECIMALES_DE_UNIDAD[producto.unidad];
	});

	// --- Entrada ---

	private readonly datosDeEntrada = signal<DatosDeEntrada>({ ...SIN_ENTRADA });
	protected readonly entrada = form(this.datosDeEntrada, (ruta) => {
		required(ruta.cantidad, { message: 'Escriba la cantidad que entró.' });
		this.validarCantidad(ruta.cantidad, () => ({ decimales: this.decimales(), maximo: ENTRADA_MAXIMA, positiva: true }));
		maxLength(ruta.nota, 200, { message: 'La nota admite hasta 200 caracteres.' });
	});
	protected readonly registrandoEntrada = signal(false);
	protected readonly errorDeEntrada = signal('');

	// --- Ajuste ---

	private readonly datosDeAjuste = signal<DatosDeAjuste>({ ...SIN_AJUSTE });
	protected readonly ajuste = form(this.datosDeAjuste, (ruta) => {
		required(ruta.contada, { message: 'Escriba cuánto hay.' });
		this.validarCantidad(ruta.contada, () => ({ decimales: this.decimales(), maximo: STOCK_MAXIMO, positiva: false }));
		validate(ruta.nota, ({ value, valueOf }) =>
			valueOf(ruta.motivo) === 'OTRO' && value().trim() === ''
				? { kind: 'required', message: 'Con el motivo «Otro», explique qué pasó.' }
				: null,
		);
		maxLength(ruta.nota, 200, { message: 'La nota admite hasta 200 caracteres.' });
	});
	protected readonly registrandoAjuste = signal(false);
	protected readonly errorDeAjuste = signal('');

	// --- Mínimo ---

	// Se rellena con el mínimo guardado, al cargar y después de cada cambio
	private readonly minimoGuardado = computed(() => textoDeMinimo(this.producto()?.stockMinimo));
	private readonly datosDeMinimo = linkedSignal<DatosDeMinimo>(() => ({ minimo: this.minimoGuardado() }));
	protected readonly minimo = form(this.datosDeMinimo, (ruta) => {
		required(ruta.minimo, { message: 'Escriba el mínimo, o quítelo.' });
		this.validarCantidad(ruta.minimo, () => ({ decimales: this.decimales(), maximo: STOCK_MAXIMO, positiva: false }));
	});
	protected readonly guardandoMinimo = signal(false);
	protected readonly errorDeMinimo = signal('');

	protected readonly avisoDeExito = signal('');

	protected readonly motivos = MOTIVOS;
	protected readonly nombresDeMotivo = NOMBRES_DE_MOTIVO;
	protected readonly cantidad = cantidad;
	protected readonly fecha = fechaCorta;
	protected readonly hora = (fechaHora: string) => horaCorta(fechaHora.slice(11));

	// Junto a una cantidad, en plural salvo con 1
	protected unidad(producto: ProductoEnInventario, cantidad: number): string {
		return unidadPara(producto.unidad, cantidad);
	}

	// En las etiquetas: «Cantidad (unidades)»
	protected plural(producto: ProductoEnInventario): string {
		return pluralDeUnidad(producto.unidad);
	}

	// «+10» o «−2», con el signo menos de verdad
	protected conSigno(movimiento: Movimiento): string {
		return `${movimiento.cantidad > 0 ? '+' : '−'}${cantidad(Math.abs(movimiento.cantidad))}`;
	}

	protected readonly descripcion = descripcionDeMovimiento;

	protected mensaje(estado: { touched(): boolean; invalid(): boolean; errors(): readonly { message?: string }[] }): string {
		return estado.touched() && estado.invalid() ? primerMensaje(estado.errors()) : '';
	}

	// Algo escrito en la entrada o el ajuste, o un mínimo distinto del guardado
	private readonly hayCambios = computed(() => {
		const entrada = this.entrada().value();
		const ajuste = this.ajuste().value();
		return (
			entrada.cantidad.trim() !== '' ||
			entrada.nota.trim() !== '' ||
			ajuste.contada.trim() !== '' ||
			ajuste.nota.trim() !== '' ||
			(this.producto() !== undefined && this.minimo().value().minimo.trim() !== this.minimoGuardado())
		);
	});

	// Lo consulta cambiosSinGuardarGuard antes de navegar a otra pantalla
	tieneCambiosSinGuardar(): boolean {
		return this.hayCambios();
	}

	confirmarSalida(): Promise<boolean> {
		return this.dialogo().abrir(SALIR_SIN_GUARDAR);
	}

	protected alCerrarLaPestana(evento: BeforeUnloadEvent): void {
		if (this.hayCambios()) {
			evento.preventDefault();
		}
	}

	protected irAPaginaDelHistorial(pagina: number): void {
		this.paginaDelHistorial.set(pagina);
	}

	protected async registrarEntrada(evento: Event): Promise<void> {
		evento.preventDefault();
		await this.enviar(this.entrada, this.registrandoEntrada, this.errorDeEntrada, 'entrada-cantidad', async (datos, id) => {
			const producto = await this.inventario.registrarEntrada(id, leerCantidad(datos.cantidad)!, datos.nota);
			this.entrada().reset({ ...SIN_ENTRADA });
			return { producto, exito: `Entrada registrada. Disponible: ${cantidad(producto.stock)}.` };
		});
	}

	protected async registrarAjuste(evento: Event): Promise<void> {
		evento.preventDefault();
		await this.enviar(this.ajuste, this.registrandoAjuste, this.errorDeAjuste, 'ajuste-contada', async (datos, id) => {
			const producto = await this.inventario.registrarAjuste(
				id,
				leerCantidad(datos.contada)!,
				datos.motivo,
				datos.nota,
			);
			this.ajuste().reset({ ...SIN_AJUSTE });
			return { producto, exito: `Ajuste registrado. Disponible: ${cantidad(producto.stock)}.` };
		});
	}

	protected async guardarMinimo(evento: Event): Promise<void> {
		evento.preventDefault();
		await this.enviar(this.minimo, this.guardandoMinimo, this.errorDeMinimo, 'minimo', async (datos, id) => {
			const producto = await this.inventario.cambiarMinimo(id, leerCantidad(datos.minimo));
			// «5,0» con 5 guardado no cambia el texto de referencia: el campo se pone igual a lo guardado
			this.datosDeMinimo.set({ minimo: textoDeMinimo(producto.stockMinimo) });
			return { producto, exito: 'Mínimo guardado.' };
		});
	}

	protected async quitarMinimo(): Promise<void> {
		const id = this.idProducto();
		if (id === null || this.guardandoMinimo()) {
			return;
		}
		const anterior = this.producto()?.stockMinimo;
		this.guardandoMinimo.set(true);
		this.errorDeMinimo.set('');
		this.avisoDeExito.set('');
		try {
			const quitado = anterior === null || anterior === undefined ? '' : ` (era ${cantidad(anterior)})`;
			this.actualizar(await this.inventario.cambiarMinimo(id, null), `Mínimo quitado${quitado}.`);
		} catch (error: unknown) {
			this.errorDeMinimo.set(mensajeDelRechazo(comoError(error)));
		} finally {
			this.guardandoMinimo.set(false);
		}
	}

	// Envía un formulario. Si es válido, la operación devuelve el producto como
	// quedó y el aviso de éxito: con eso se actualiza la cabecera y el historial
	private async enviar<T>(
		formulario: FieldTree<T>,
		enCurso: WritableSignal<boolean>,
		error: WritableSignal<string>,
		primerCampo: string,
		operacion: (datos: T, idProducto: number) => Promise<{ producto: ProductoEnInventario; exito: string }>,
	): Promise<void> {
		const id = this.idProducto();
		if (id === null || enCurso()) {
			return;
		}
		error.set('');
		this.avisoDeExito.set('');
		enCurso.set(true);
		await submit(formulario, {
			action: async (campos) => {
				try {
					const { producto, exito } = await operacion(campos().value(), id);
					this.paginaDelHistorial.set(0);
					this.actualizar(producto, exito);
				} catch (rechazo: unknown) {
					error.set(mensajeDelRechazo(comoError(rechazo)));
				}
				return undefined;
			},
		});
		enCurso.set(false);
		if (formulario().invalid()) {
			enfocarCampo(this.elemento.nativeElement, primerCampo);
		}
	}

	private actualizar(producto: ProductoEnInventario, exito: string): void {
		this.recurso.value.set(producto);
		this.historial.reload();
		this.avisoDeExito.set(exito);
	}

	private validarCantidad(ruta: SchemaPath<string>, reglas: () => ReglasDeCantidad): void {
		validate(ruta, ({ value }) => {
			const problema = problemaDeCantidad(value(), reglas());
			return problema === null ? null : { kind: 'cantidad', message: problema };
		});
	}
}

function textoDeMinimo(minimo: number | null | undefined): string {
	return minimo === null || minimo === undefined ? '' : String(minimo).replace('.', ',');
}
