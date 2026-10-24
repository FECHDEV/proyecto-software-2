import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	input,
	output,
	signal,
} from '@angular/core';

import { mensajeDeError, mensajeDelCodigo } from '../../core/api/mensajes-error';
import { ProductosService } from '../../core/api/productos.service';
import { comoError } from '../../core/modelos/error-api';
import { MAXIMO_MODELO, MODELO_RECOMENDADO, Producto } from '../../core/modelos/producto';
import { ArchivoOcupado } from '../../shared/archivo/archivo-ocupado';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { enfocarCampo } from '../../shared/formularios/primer-campo-con-error';
import { Visor3d } from '../../shared/visor-3d/visor-3d';

// La tarjeta «Modelo 3D» del producto (HU-15): subirlo, verlo en el visor,
// reemplazarlo y quitarlo, con el mismo trato que las fotos. El producto
// actualizado vuelve a la pantalla por «cambiado»
@Component({
	selector: 'app-modelo-de-producto',
	imports: [ArchivoOcupado, Visor3d],
	templateUrl: './modelo-de-producto.html',
	styleUrl: './modelo-de-producto.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModeloDeProducto {
	private readonly productos = inject(ProductosService);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly injector = inject(Injector);

	// undefined mientras el producto no existe: el modelo es de un producto ya creado
	readonly producto = input.required<Producto | undefined>();
	// El de la pantalla, para no abrir dos diálogos
	readonly dialogo = input.required<DialogoConfirmacion>();
	readonly cambiado = output<Producto>();

	protected readonly modelo = computed(() => this.producto()?.modelo ?? null);
	protected readonly operando = signal(false);
	protected readonly error = signal('');
	protected readonly exito = signal('');
	protected readonly avisoDePeso = signal('');

	protected url(archivo: string): string {
		const producto = this.producto();
		return producto === undefined ? '' : this.productos.urlDeModelo(producto.idProducto, archivo);
	}

	protected peso(bytes: number): string {
		return pesoEnMb(bytes);
	}

	// Lo que pasa de 3 MB ni se sube: el backend lo rechazaría, y uno muy grande lo corta el servidor (RF-4). Mientras
	// sube, el campo queda ocupado (ArchivoOcupado) y un change se ignora
	protected async elegir(evento: Event): Promise<void> {
		const entrada = evento.target as HTMLInputElement;
		const archivo = entrada.files?.[0];
		const producto = this.producto();
		// El mismo archivo se puede volver a elegir después de un error
		entrada.value = '';
		if (archivo === undefined || producto === undefined || this.operando()) {
			return;
		}
		if (archivo.size > MAXIMO_MODELO) {
			this.limpiarAvisos();
			this.error.set(mensajeDelCodigo('MODELO_DEMASIADO_GRANDE'));
			return;
		}
		const exito = producto.modelo === null ? 'Modelo subido.' : 'Modelo reemplazado.';
		await this.operar(() => this.productos.subirModelo(producto.idProducto, archivo), exito);
	}

	protected async quitar(): Promise<void> {
		const producto = this.producto();
		if (producto === undefined || this.operando()) {
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: '¿Quitar el modelo 3D?',
			mensaje: 'El modelo se borra para siempre. Los clientes verán solo las fotos.',
			confirmar: 'Quitar',
		});
		if (confirmado) {
			await this.operar(() => this.productos.quitarModelo(producto.idProducto), 'Modelo quitado.');
		}
	}

	// Más de 1 MB se guarda igual, con el aviso (RF-10)
	private async operar(operacion: () => Promise<Producto>, exito: string): Promise<void> {
		this.operando.set(true);
		this.limpiarAvisos();
		try {
			const producto = await operacion();
			this.cambiado.emit(producto);
			this.exito.set(exito);
			if (producto.modelo !== null && producto.modelo.peso > MODELO_RECOMENDADO) {
				this.avisoDePeso.set(
					`El modelo pesa ${pesoEnMb(producto.modelo.peso)}: en un celular con datos puede tardar en cargar. ` +
						'Lo recomendado es menos de 1 MB.',
				);
			}
			// El botón usado puede cambiar («Subir modelo» pasa a «Reemplazar»): el foco va al título
			afterNextRender(() => enfocarCampo(this.elemento.nativeElement, 'titulo-modelo'), { injector: this.injector });
		} catch (error: unknown) {
			this.error.set(mensajeDeError(comoError(error)));
		} finally {
			this.operando.set(false);
		}
	}

	private limpiarAvisos(): void {
		this.error.set('');
		this.exito.set('');
		this.avisoDePeso.set('');
	}
}

// «410 KB» o «2,40 MB», con la coma de Bolivia: un modelo chico no se lee como «0,00 MB» (revisión de interfaz)
function pesoEnMb(bytes: number): string {
	const kb = bytes === 0 ? 0 : Math.max(1, Math.round(bytes / 1024));
	if (kb < 1024) {
		return `${kb.toLocaleString('es-BO')} KB`;
	}
	return (
		(bytes / (1024 * 1024)).toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' MB'
	);
}
