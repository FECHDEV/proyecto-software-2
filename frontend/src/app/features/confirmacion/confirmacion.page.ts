import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	signal,
	viewChild,
} from '@angular/core';
import { form, FormField, schema, submit, validate } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideCircleSlash, LucideClock, LucideMapPin, LucideStore } from '@lucide/angular';

import { CarritoService } from '../../core/api/carrito.service';
import { EstadoDelCampo, mensajeDeError, mensajeDelCampo, mensajeDelRechazo } from '../../core/api/mensajes-error';
import { PedidosService } from '../../core/api/pedidos.service';
import { LineaDeCarrito } from '../../core/modelos/carrito';
import { comoError, comoErrorApi, ErrorApi } from '../../core/modelos/error-api';
import { AVISOS_DE_RECHAZO, MotivoDeRechazo } from '../../core/modelos/pedido';
import { SesionService } from '../../core/sesion/sesion.service';
import { cantidad, precio } from '../../shared/formato/precios';
import { enfocarCampo, primerCampoConError, primerCampoRechazado } from '../../shared/formularios/primer-campo-con-error';

interface DatosDeRetiro {
	nombreRetira: string;
	telefonoRetira: string;
	nota: string;
}

type Campo = keyof DatosDeRetiro;

const CAMPOS: readonly Campo[] = ['nombreRetira', 'telefonoRetira', 'nota'];

// Las de ConfirmacionRequest (HU-12 RF-6): los espacios de alrededor no cuentan
const esquema = schema<DatosDeRetiro>((ruta) => {
	validate(ruta.nombreRetira, ({ value }) => {
		const nombre = value().trim();
		if (nombre === '') {
			return { kind: 'required', message: 'Escriba el nombre de quien retira.' };
		}
		return nombre.length > 160 ? { kind: 'maxLength', message: 'El nombre admite hasta 160 caracteres.' } : null;
	});
	validate(ruta.telefonoRetira, ({ value }) => {
		const telefono = value().trim();
		if (telefono === '') {
			return { kind: 'required', message: 'Escriba un teléfono para avisarle.' };
		}
		return telefono.length > 20 ? { kind: 'maxLength', message: 'El teléfono admite hasta 20 caracteres.' } : null;
	});
	validate(ruta.nota, ({ value }) =>
		value().trim().length > 200 ? { kind: 'maxLength', message: 'La nota admite hasta 200 caracteres.' } : null,
	);
});

// La confirmación del pedido (HU-12): lo que se compra con el precio de hoy,
// dónde retirarlo y quién lo retira. Se confirma todo o nada; si el backend
// rechaza, la pantalla dice por qué y cómo seguir
@Component({
	selector: 'app-confirmacion',
	imports: [FormField, RouterLink, LucideArrowLeft, LucideCircleSlash, LucideClock, LucideMapPin, LucideStore],
	templateUrl: './confirmacion.page.html',
	styleUrl: './confirmacion.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmacionPage {
	private readonly servicioCarrito = inject(CarritoService);
	private readonly pedidos = inject(PedidosService);
	private readonly router = inject(Router);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly injector = inject(Injector);
	private readonly avisoDeRechazo = viewChild<ElementRef<HTMLElement>>('rechazo');

	protected readonly recurso = this.servicioCarrito.carrito;
	protected readonly carrito = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});
	// Solo se ofrece confirmar lo que el backend aceptaría (RF-3, RF-4, RF-5)
	protected readonly comprable = computed(() => {
		const carrito = this.carrito();
		return (
			carrito !== undefined &&
			carrito.idPedidoPendiente === null &&
			carrito.correoVerificado !== false &&
			carrito.lineas.length > 0 &&
			carrito.lineas.every((linea) => linea.estado === 'DISPONIBLE')
		);
	});

	protected readonly recursoDeRetiro = this.pedidos.retiro();
	protected readonly datosDeRetiro = computed(() =>
		this.recursoDeRetiro.hasValue() ? this.recursoDeRetiro.value() : undefined,
	);

	// Llenos con los de la cuenta; se pueden cambiar por los de otra persona (RF-1)
	private readonly datos = signal<DatosDeRetiro>(datosDeLaCuenta(inject(SesionService)));
	protected readonly formulario = form(this.datos, esquema);

	protected readonly confirmando = signal(false);
	private readonly rechazo = signal<ErrorApi | null>(null);
	// Las líneas que el backend no aceptó, con su motivo (RF-3)
	protected readonly rechazadas = computed(() => {
		const marcadas: Partial<Record<number, MotivoDeRechazo>> = {};
		for (const linea of this.rechazo()?.lineas ?? []) {
			marcadas[linea.idProducto] = linea.motivo;
		}
		return marcadas;
	});
	protected readonly codigoDeRechazo = computed(() => this.rechazo()?.codigo);
	// Confirmado en otra pestaña o dispositivo (RF-5)
	protected readonly pedidoPendiente = computed(() =>
		this.rechazo()?.codigo === 'PEDIDO_PENDIENTE' ? (this.rechazo()?.idPedido ?? null) : null,
	);
	protected readonly mensajeDeRechazo = computed(() => {
		const rechazo = this.rechazo();
		if (rechazo === null) {
			return '';
		}
		// El total nuevo ya está en pantalla: el carrito se pidió otra vez (RF-15). No siempre es un precio: también
		// pudieron volver las líneas de un pedido que venció
		if (rechazo.codigo === 'TOTAL_CAMBIADO' && rechazo.total !== undefined) {
			return `El total cambió: ahora es ${precio(rechazo.total)}. Revise el pedido y confirme otra vez.`;
		}
		return mensajeDelRechazo(rechazo);
	});

	protected readonly avisos = AVISOS_DE_RECHAZO;
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;

	constructor() {
		// Con el precio y la disponibilidad de hoy, como al abrir el carrito
		this.recurso.reload();
	}

	// «Unidad» no se escribe, como en el carrito
	protected unidad(linea: LineaDeCarrito): string {
		return linea.unidad === 'UNIDAD' ? '' : ` / ${linea.unidad.toLowerCase()}`;
	}

	protected mensaje(estado: EstadoDelCampo, campo: Campo): string {
		return mensajeDelCampo(estado, campo, this.rechazo());
	}

	// Mientras confirma, el botón no se deshabilita (no pierde el foco): un
	// segundo toque no manda otra confirmación
	protected async confirmar(evento: Event): Promise<void> {
		evento.preventDefault();
		const carrito = this.carrito();
		// Mientras el carrito recarga, el total que se ve puede ser el viejo (RF-15)
		if (this.confirmando() || this.recurso.isLoading() || carrito === undefined) {
			return;
		}
		this.rechazo.set(null);
		this.confirmando.set(true);
		await submit(this.formulario, {
			action: async (campos) => {
				try {
					const pedido = await this.pedidos.confirmar({ ...campos().value(), totalEsperado: carrito.total });
					await this.router.navigate(['/mis-pedidos', pedido.idPedido]);
				} catch (error: unknown) {
					this.rechazar(comoError(error));
				}
				return undefined;
			},
		});
		this.confirmando.set(false);
		if (this.formulario().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(CAMPOS, {
					nombreRetira: this.formulario.nombreRetira().invalid(),
					telefonoRetira: this.formulario.telefonoRetira().invalid(),
					nota: this.formulario.nota().invalid(),
				}),
			);
		}
	}

	private rechazar(error: ErrorApi): void {
		this.rechazo.set(error);
		// Con el carrito pedido de nuevo queda el aviso de verificar el correo (HU-20 RF-6, RF-7)
		if (error.codigo === 'TOTAL_CAMBIADO' || error.codigo === 'CORREO_SIN_VERIFICAR') {
			this.recurso.reload();
		}
		const campo = primerCampoRechazado(CAMPOS, error.errores);
		if (campo !== null) {
			enfocarCampo(this.elemento.nativeElement, campo);
		} else {
			// El aviso se dibuja después de este cambio; con el foco, lleva al enlace para seguir
			afterNextRender(() => this.avisoDeRechazo()?.nativeElement.focus(), { injector: this.injector });
		}
	}
}

function datosDeLaCuenta(sesion: SesionService): DatosDeRetiro {
	const usuario = sesion.usuario();
	return {
		// Nombre y apellido admiten 80 cada uno: juntos pueden pasar de los 160 del pedido
		// (sin partir un emoji al cortar)
		nombreRetira:
			usuario === null
				? ''
				: `${usuario.nombre} ${usuario.apellido}`
						.slice(0, 160)
						.replace(/[\uD800-\uDBFF]$/, '')
						.trim(),
		telefonoRetira: usuario?.telefono ?? '',
		nota: '',
	};
}
