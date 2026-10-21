import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	effect,
	ElementRef,
	inject,
	Injector,
	linkedSignal,
	NgZone,
	signal,
	untracked,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideCircleCheck, LucideClock, LucideMapPin, LucideStore } from '@lucide/angular';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { CarritoService } from '../../core/api/carrito.service';
import { PedidosService } from '../../core/api/pedidos.service';
import { comoError, comoErrorApi } from '../../core/modelos/error-api';
import { EstadoDePedido, LineaDePedido, NOMBRES_DE_ESTADO, Pedido } from '../../core/modelos/pedido';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { fechaCorta, horaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';
import { comoReloj } from '../../shared/formato/tiempo';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';
import { PagoDelPedido } from './pago/pago-del-pedido';

// El pedido del Cliente (HU-12): lo que se confirmó, quién lo retira y dónde,
// y mientras espera el pago, el tiempo que queda, el pago con QR (HU-13) y
// «Cancelar pedido». El tiempo lo cuenta el servidor; la página solo lo descuenta
@Component({
	selector: 'app-pedido',
	imports: [RouterLink, DialogoConfirmacion, PagoDelPedido, LucideCircleCheck, LucideClock, LucideMapPin, LucideStore],
	templateUrl: './pedido.page.html',
	styleUrl: './pedido.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PedidoPage {
	private readonly pedidos = inject(PedidosService);
	private readonly carrito = inject(CarritoService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
	private readonly injector = inject(Injector);

	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });
	// Lo que no es un número de pedido no llega a la API (RF-13)
	protected readonly idPedido = computed(() => idDeLaUrl(this.parametros().get('id')));

	protected readonly recurso = this.pedidos.pedido(this.idPedido);
	protected readonly pedido = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	private readonly estadoDeError = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? null : comoErrorApi(error);
	});
	// De otro cliente o inexistente responde igual (RF-13)
	protected readonly noEncontrado = computed(() => this.idPedido() === null || this.estadoDeError()?.status === 404);
	protected readonly errorDeCarga = computed(() => {
		const error = this.estadoDeError();
		return error === null || error.status === 404 ? '' : mensajeDeError(error);
	});

	// --- El tiempo para pagar (RF-8, RF-10) ---

	// Cuándo llegó la respuesta, en el reloj de este dispositivo. El contador
	// parte de esa misma hora y la adelanta cada segundo
	private readonly recibido = linkedSignal({ source: this.pedido, computation: () => Date.now() });
	private readonly ahora = linkedSignal({ source: this.recibido, computation: (recibido) => recibido });
	private readonly limite = computed(() => {
		const pedido = this.pedido();
		return pedido?.estado === 'PENDIENTE_PAGO' ? this.recibido() + pedido.segundosParaPagar * 1000 : null;
	});
	protected readonly segundos = computed(() => {
		const limite = this.limite();
		return limite === null ? null : Math.max(0, Math.ceil((limite - this.ahora()) / 1000));
	});
	protected readonly reloj = computed(() => comoReloj(this.segundos() ?? 0));
	// Cuándo se pidió de nuevo por última vez al llegar a cero
	private ultimoPedido = Number.NEGATIVE_INFINITY;
	// El pedido que se mostraba, para ver si dejó de esperar el pago
	private anterior: { idPedido: number; estado: EstadoDePedido } | null = null;

	// --- Cancelar (RF-9, RF-16) ---

	protected readonly cancelando = signal(false);
	// Se reinician con otro pedido en la misma pantalla
	protected readonly aviso = linkedSignal({ source: this.idPedido, computation: () => '' });
	protected readonly noVolvieron = linkedSignal({ source: this.idPedido, computation: () => 0 });

	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;

	constructor() {
		// Fuera de la zona: el reloj no dispara la detección de cambios de toda la
		// aplicación cada segundo; la señal ya redibuja lo que la usa
		const intervalo = inject(NgZone).runOutsideAngular(() => setInterval(() => this.ahora.set(Date.now()), 1000));
		inject(DestroyRef).onDestroy(() => clearInterval(intervalo));
		// Al llegar a cero se pide otra vez: el servidor lo vence en el acto y responde «Vencido».
		// Si le faltaba menos de un segundo y todavía responde «Pendiente», se reintenta cada segundo.
		// Con un QR no: el bloque del pago ya pregunta cada 5 segundos, y el servidor lo vence después
		// de que el sistema de pago dijo que no se pagó (HU-13 RF-7)
		effect(() => {
			const ahora = this.ahora();
			if (this.pedido()?.cobro == null && this.segundos() === 0 && ahora - this.ultimoPedido >= 1000) {
				this.ultimoPedido = ahora;
				this.recurso.reload();
			}
		});
		// Si dejó de esperar el pago (pagado o vencido, aquí, por la tarea o en
		// otra pestaña), el carrito cambió o ya no tiene un pedido pendiente: el
		// encabezado se pide de nuevo. Cancelar ya lo recarga (PedidosService)
		effect(() => {
			const pedido = this.pedido();
			if (pedido === undefined) {
				return;
			}
			const anterior = this.anterior;
			this.anterior = { idPedido: pedido.idPedido, estado: pedido.estado };
			if (
				anterior?.idPedido === pedido.idPedido &&
				anterior.estado === 'PENDIENTE_PAGO' &&
				pedido.estado !== 'PENDIENTE_PAGO' &&
				pedido.estado !== 'CANCELADO'
			) {
				untracked(() => this.carrito.carrito.reload());
			}
		});
	}

	protected hora(fechaHora: string): string {
		return horaCorta(fechaHora.slice(11));
	}

	protected fecha(fechaHora: string): string {
		return fechaCorta(fechaHora);
	}

	// Lo que respondió el pago (HU-13). Si el Cliente estaba en el bloque del
	// pago y este desaparece (pagado), el foco va al resultado
	protected actualizar(pedido: Pedido): void {
		if (pedido.idPedido !== this.idPedido()) {
			return;
		}
		const enElPago = this.elemento.nativeElement.querySelector('app-pago-del-pedido')?.contains(document.activeElement);
		this.recurso.value.set(pedido);
		if (pedido.estado !== 'PENDIENTE_PAGO' && enElPago) {
			this.enfocar('.cerrado');
		}
	}


	// «Unidad» no se escribe, como en el carrito
	protected unidad(linea: LineaDePedido): string {
		return linea.unidad === 'UNIDAD' ? '' : ` / ${linea.unidad.toLowerCase()}`;
	}

	protected async cancelar(): Promise<void> {
		const pedido = this.pedido();
		if (pedido === undefined || this.cancelando()) {
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: '¿Cancelar el pedido?',
			mensaje: 'Los productos dejan de estar reservados y vuelven a su carrito.',
			confirmar: 'Cancelar pedido',
			desistir: 'Volver',
		});
		if (!confirmado) {
			return;
		}
		this.aviso.set('');
		this.cancelando.set(true);
		try {
			const cancelacion = await this.pedidos.cancelar(pedido.idPedido);
			// Mientras tanto se abrió otro pedido: la respuesta ya no es de esta pantalla
			if (this.idPedido() !== pedido.idPedido) {
				return;
			}
			this.recurso.value.set(cancelacion.pedido);
			this.noVolvieron.set(cancelacion.lineasQueNoVolvieron);
			// El botón ya no está: el foco va al resultado, también si quedó fuera de la vista
			this.enfocar('.cerrado');
		} catch (rechazo: unknown) {
			const error = comoError(rechazo);
			if (this.idPedido() !== pedido.idPedido) {
				return;
			}
			this.aviso.set(mensajeDeError(error));
			// Se canceló o venció en otra pestaña: se muestra como está ahora (RF-12)
			if (error.codigo === 'PEDIDO_NO_PENDIENTE') {
				this.recurso.reload();
			}
			this.enfocar('.aviso');
		} finally {
			this.cancelando.set(false);
		}
	}

	// Después de dibujar el cambio
	private enfocar(selector: string): void {
		afterNextRender(() => this.elemento.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
			injector: this.injector,
		});
	}
}
