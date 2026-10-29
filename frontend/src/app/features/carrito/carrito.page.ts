import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
	LucideCircleSlash,
	LucideClock,
	LucideMail,
	LucideShoppingCart,
	LucideTrash2,
	LucideTriangleAlert,
	LucideWrench,
} from '@lucide/angular';

import { CarritoService } from '../../core/api/carrito.service';
import { mensajeDeError, mensajeDelRechazo } from '../../core/api/mensajes-error';
import {
	AVISOS_DE_LINEA,
	CANTIDAD_MAXIMA_EN_CARRITO,
	cantidadConPasoEnCarrito,
	LineaDeCarrito,
} from '../../core/modelos/carrito';
import { urlDeImagen } from '../../core/modelos/catalogo';
import { comoErrorApi, ErrorApi } from '../../core/modelos/error-api';
import { DECIMALES_DE_UNIDAD } from '../../core/modelos/inventario';
import { reenvioDeVerificacion } from '../../core/sesion/reenvio-de-verificacion';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { leerCantidad, problemaDeCantidad } from '../../shared/formato/cantidades';
import { cantidad, precio } from '../../shared/formato/precios';
import { PasoDeCantidad } from '../../shared/paso-de-cantidad/paso-de-cantidad';

// El carrito del Cliente (HU-11): cada línea con su foto, el precio de hoy, la
// cantidad que se cambia en el lugar y el subtotal; lo que ya no se puede
// comprar queda marcado y fuera del total. Con todo comprable, lleva a confirmar
// el pedido (HU-12)
@Component({
	selector: 'app-carrito',
	imports: [
		RouterLink,
		DialogoConfirmacion,
		PasoDeCantidad,
		LucideCircleSlash,
		LucideClock,
		LucideMail,
		LucideShoppingCart,
		LucideTrash2,
		LucideTriangleAlert,
		LucideWrench,
	],
	templateUrl: './carrito.page.html',
	styleUrl: './carrito.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CarritoPage {
	private readonly servicio = inject(CarritoService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	private readonly titulo = viewChild.required<ElementRef<HTMLElement>>('titulo');

	protected readonly recurso = this.servicio.carrito;
	protected readonly carrito = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	// Confirmar se rechazaría con alguna línea que no se puede comprar (HU-12 RF-3)
	protected readonly todoComprable = computed(
		() => this.carrito()?.lineas.every((linea) => linea.estado === 'DISPONIBLE') ?? false,
	);
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// El motivo por línea (una cantidad inválida o rechazada) y el de quitar o vaciar
	protected readonly errores = signal<Readonly<Record<number, string>>>({});
	protected readonly aviso = signal('');
	// Lo que se quitó o se vació, para los lectores de pantalla
	protected readonly anuncio = signal('');
	// «Reenviar el correo» de la verificación (HU-20 RF-7, RF-9)
	// Ya verificado en otra pestaña: el carrito pedido de nuevo ofrece confirmar (RF-11)
	protected readonly reenvio = reenvioDeVerificacion(() => this.recurso.reload());

	// La última cantidad pedida por línea que todavía no volvió de la API, con su turno
	private readonly pendientes = signal<Readonly<Record<number, { cantidad: number; turno: number }>>>({});
	private turnos = 0;

	protected readonly avisos = AVISOS_DE_LINEA;
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;

	constructor() {
		// El carrito se cargó al entrar: al abrir la página se pide de nuevo, con el precio y la
		// disponibilidad de hoy y lo agregado desde otro dispositivo (RF-8, RF-9, RF-13)
		this.recurso.reload();
	}

	protected foto(linea: LineaDeCarrito): string | null {
		return linea.imagen === null ? null : urlDeImagen(linea.idProducto, linea.imagen, 400);
	}

	// «Unidad» no se escribe, como en el detalle
	protected unidad(linea: LineaDeCarrito): string | null {
		return linea.unidad === 'UNIDAD' ? null : linea.unidad.toLowerCase();
	}

	// Se guarda al confirmar el campo (Enter o al salir). Si la cantidad no
	// sirve, no se manda; si la API la rechaza, vuelve la anterior (RF-10)
	protected async cambiar(linea: LineaDeCarrito, evento: Event): Promise<void> {
		const campo = evento.target as HTMLInputElement;
		const texto = campo.value;
		const problema =
			texto.trim() === ''
				? 'Escriba la cantidad.'
				: problemaDeCantidad(texto, {
						decimales: DECIMALES_DE_UNIDAD[linea.unidad],
						maximo: CANTIDAD_MAXIMA_EN_CARRITO,
						positiva: true,
					});
		if (problema !== null) {
			this.ponerError(linea.idProducto, problema);
			return;
		}
		const nueva = leerCantidad(texto) ?? 0;
		if (nueva === this.mostrada(linea)) {
			this.ponerError(linea.idProducto, '');
			return;
		}
		// Si la API la rechaza, el campo vuelve a la guardada
		if (!(await this.guardar(linea, nueva))) {
			campo.value = cantidad(this.mostrada(linea));
		}
	}

	protected mostrada(linea: LineaDeCarrito): number {
		return this.pendientes()[linea.idProducto]?.cantidad ?? linea.cantidad;
	}

	protected conPaso(linea: LineaDeCarrito, paso: 1 | -1): number | null {
		return cantidadConPasoEnCarrito(this.mostrada(linea), paso, linea.unidad);
	}

	// − y + guardan en el acto, sin esperar la respuesta del toque anterior (RF-15)
	protected async pasar(linea: LineaDeCarrito, paso: 1 | -1): Promise<void> {
		const nueva = this.conPaso(linea, paso);
		// Se anuncia solo la última: las intermedias ya no son la cantidad
		if (nueva !== null && (await this.guardar(linea, nueva)) && this.pendientes()[linea.idProducto] === undefined) {
			this.anuncio.set(`Cantidad de ${linea.nombre}: ${cantidad(nueva)}`);
		}
	}

	// Lo escrito y lo de − y + pasan por acá. El servicio manda las escrituras en
	// orden; mientras alguna está en camino, la pantalla muestra la última pedida
	// y desde ella sigue el próximo cambio. Solo cuenta la respuesta de la última:
	// si se guardó, no queda error; si la rechazó, vuelve la cantidad guardada con
	// el motivo
	private async guardar(linea: LineaDeCarrito, nueva: number): Promise<boolean> {
		const id = linea.idProducto;
		const turno = ++this.turnos;
		this.pendientes.update((actuales) => ({ ...actuales, [id]: { cantidad: nueva, turno } }));
		this.ponerError(id, '');
		this.aviso.set('');
		const esLaUltima = (): boolean => this.pendientes()[id]?.turno === turno;
		try {
			await this.servicio.cambiarCantidad(id, nueva);
			if (esLaUltima()) {
				this.ponerError(id, '');
			}
			return true;
		} catch (rechazo: unknown) {
			const error = rechazo as ErrorApi;
			// La línea ya no existe (se quitó en otro dispositivo): el carrito se pide de nuevo
			if (error.status === 404) {
				this.recurso.reload();
			} else if (esLaUltima()) {
				this.ponerError(id, mensajeDelRechazo(error));
			}
			return false;
		} finally {
			if (esLaUltima()) {
				this.pendientes.update(({ [id]: _, ...resto }) => resto);
			}
		}
	}

	protected async quitar(linea: LineaDeCarrito): Promise<void> {
		this.aviso.set('');
		try {
			await this.servicio.quitar(linea.idProducto);
			this.ponerError(linea.idProducto, '');
			this.despuesDeQuitar(`Se quitó ${linea.nombre} del carrito.`);
		} catch (rechazo: unknown) {
			this.aviso.set(mensajeDeError(rechazo as ErrorApi));
		}
	}

	protected async vaciar(): Promise<void> {
		const confirmado = await this.dialogo().abrir({
			titulo: '¿Vaciar el carrito?',
			mensaje: 'Se quitan todos los productos del carrito.',
			confirmar: 'Vaciar',
		});
		if (!confirmado) {
			return;
		}
		this.aviso.set('');
		try {
			await this.servicio.vaciar();
			this.errores.set({});
			this.despuesDeQuitar('Se vació el carrito.');
		} catch (rechazo: unknown) {
			this.aviso.set(mensajeDeError(rechazo as ErrorApi));
		}
	}

	// El botón que tenía el foco ya no existe: el foco va al título, sin desplazar
	private despuesDeQuitar(anuncio: string): void {
		this.anuncio.set(anuncio);
		this.titulo().nativeElement.focus({ preventScroll: true });
	}

	private ponerError(idProducto: number, mensaje: string): void {
		this.errores.update((actuales) => ({ ...actuales, [idProducto]: mensaje }));
	}
}
