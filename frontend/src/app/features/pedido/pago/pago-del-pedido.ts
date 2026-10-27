import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	effect,
	ElementRef,
	inject,
	Injector,
	input,
	NgZone,
	output,
	signal,
} from '@angular/core';
import { LucideQrCode } from '@lucide/angular';

import { mensajeDeError } from '../../../core/api/mensajes-error';
import { PedidosService } from '../../../core/api/pedidos.service';
import { comoError } from '../../../core/modelos/error-api';
import { Pedido, ResultadoSimulado } from '../../../core/modelos/pedido';
import { CodigoQr } from '../../../shared/codigo-qr/codigo-qr';
import { precio } from '../../../shared/formato/precios';

// RF-6: el sistema de pago informó un error y el cobro se descartó
const PAGO_FALLIDO = 'El pago no se pudo completar. Genere otro QR para intentar de nuevo.';
// «Ya pagué» antes de que el sistema de pago lo confirme
const TODAVIA_NO = 'Todavía no recibimos el pago. Si ya pagó, espere unos segundos.';

// Cada cuánto pregunta la página si llegó el pago mientras el QR está a la vista (HU-13 RF-4)
export const INTERVALO_DE_CONSULTA = 5000;

// Pagar el pedido con QR (HU-13): «Pagar con QR» genera el cobro; con el QR a
// la vista, la página pregunta sola cada 5 segundos y «Ya pagué» pregunta en el
// acto. El pago lo confirma el servidor consultando al sistema de pago, nunca
// esta pantalla. Cada respuesta sube a la página del pedido con «cambio»
@Component({
	selector: 'app-pago-del-pedido',
	imports: [CodigoQr, LucideQrCode],
	templateUrl: './pago-del-pedido.html',
	styleUrl: './pago-del-pedido.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PagoDelPedido {
	private readonly pedidos = inject(PedidosService);
	private readonly zona = inject(NgZone);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
	private readonly injector = inject(Injector);

	// Solo mientras espera el pago
	readonly pedido = input.required<Pedido>();
	// El pedido como quedó después de pagar, consultar o simular
	readonly cambio = output<Pedido>();
	// Ya no espera el pago: la página lo pide de nuevo
	readonly desactualizado = output<void>();

	protected readonly generando = signal(false);
	protected readonly consultando = signal(false);
	// «Ya pagué» espera una respuesta, la de su consulta o la automática que ya estaba en curso (revisión total)
	protected readonly esperaElCliente = signal(false);
	protected readonly simulando = signal(false);
	protected readonly precio = precio;
	// Cada respuesta trae un pedido nuevo: el intervalo solo se rehace cuando el QR aparece o se va
	private readonly hayCobro = computed(() => this.pedido().cobro !== null);
	// Cada mensaje recuerda de qué pedido es: con otro pedido en pantalla no se
	// muestra. Cada respuesta trae un objeto nuevo del mismo pedido, así que no
	// se reinician al cambiar la entrada
	private readonly errorDelPago = signal<Mensaje | null>(null);
	private readonly estadoDelPago = signal<Mensaje | null>(null);
	protected readonly aviso = computed(() => this.delPedido(this.errorDelPago()));
	protected readonly estado = computed(() => this.delPedido(this.estadoDelPago()));

	constructor() {
		// RF-4: solo mientras hay un QR a la vista. Al pagarse, al descartarse
		// el cobro o al salir, el intervalo se limpia. Fuera de la zona: una
		// espera que se repite no es trabajo pendiente de la pantalla, y las
		// señales que cambia la consulta ya la vuelven a dibujar
		effect((alLimpiar) => {
			if (!this.hayCobro()) {
				return;
			}
			const consulta = this.zona.runOutsideAngular(() =>
				setInterval(() => void this.consultar(false), INTERVALO_DE_CONSULTA),
			);
			alLimpiar(() => clearInterval(consulta));
		});
	}

	// RF-1: genera el cobro, o trae el que ya tiene (RF-2)
	protected async pagar(): Promise<void> {
		if (this.generando()) {
			return;
		}
		const id = this.pedido().idPedido;
		this.errorDelPago.set(null);
		this.generando.set(true);
		try {
			const pedido = await this.pedidos.pagar(id);
			if (this.pedido().idPedido === id) {
				// «Pagar con QR» deja de estar: el foco sigue en el bloque, en «Ya pagué»
				const conFoco = this.elemento.nativeElement.contains(document.activeElement);
				this.cambio.emit(pedido);
				if (conFoco) {
					afterNextRender(() => this.elemento.nativeElement.querySelector<HTMLElement>('.ya-pague')?.focus(), {
						injector: this.injector,
					});
				}
			}
		} catch (rechazo: unknown) {
			this.rechazado(id, rechazo);
		} finally {
			this.generando.set(false);
		}
	}

	// «Ya pagué»: pregunta en el acto y dice si todavía no llegó
	protected yaPague(): Promise<void> {
		return this.consultar(true);
	}

	// Herramienta de pruebas (RF-12): lo que haría el banco, y después la consulta
	protected async simular(resultado: ResultadoSimulado): Promise<void> {
		if (this.simulando()) {
			return;
		}
		const id = this.pedido().idPedido;
		this.errorDelPago.set(null);
		this.simulando.set(true);
		try {
			await this.pedidos.simular(id, resultado);
		} catch (rechazo: unknown) {
			this.rechazado(id, rechazo);
			return;
		} finally {
			this.simulando.set(false);
		}
		await this.consultar(false);
	}

	// Una consulta a la vez. Las automáticas no muestran un error de red: se
	// vuelve a preguntar en la próxima vuelta. «Ya pagué» durante una automática
	// espera a que termine y pregunta otra vez: la que corría salió quizá antes
	// de que el Cliente pagara (revisión total)
	private async consultar(pedidaPorElCliente: boolean): Promise<void> {
		if (pedidaPorElCliente) {
			if (this.esperaElCliente()) {
				return;
			}
			this.esperaElCliente.set(true);
			this.errorDelPago.set(null);
			this.estadoDelPago.set(null);
		}
		if (this.consultando()) {
			return;
		}
		const anterior = this.pedido();
		const id = anterior.idPedido;
		this.consultando.set(true);
		// Si sigue esperando el pago al terminar: solo entonces hay algo que volver a preguntar
		let sigue = false;
		try {
			const pedido = await this.pedidos.consultarPago(id);
			if (this.pedido().idPedido !== id) {
				return;
			}
			sigue = pedido.estado === 'PENDIENTE_PAGO' && pedido.cobro !== null;
			// RF-6: el sistema de pago informó un error y el cobro se descartó
			if (pedido.estado === 'PENDIENTE_PAGO' && pedido.cobro === null && anterior.cobro !== null) {
				this.errorDelPago.set({ idPedido: id, texto: PAGO_FALLIDO });
			} else if (pedidaPorElCliente && pedido.estado === 'PENDIENTE_PAGO') {
				this.estadoDelPago.set({ idPedido: id, texto: TODAVIA_NO });
			}
			this.cambio.emit(pedido);
		} catch (rechazo: unknown) {
			sigue = comoError(rechazo).codigo !== 'PEDIDO_NO_PENDIENTE';
			if (pedidaPorElCliente || !sigue) {
				this.rechazado(id, rechazo);
			}
		} finally {
			this.consultando.set(false);
			const alguienEspera = !pedidaPorElCliente && this.esperaElCliente();
			this.esperaElCliente.set(false);
			if (alguienEspera && sigue) {
				void this.consultar(true);
			}
		}
	}

	private delPedido(mensaje: Mensaje | null): string {
		return mensaje !== null && mensaje.idPedido === this.pedido().idPedido ? mensaje.texto : '';
	}

	private rechazado(id: number, rechazo: unknown): void {
		if (this.pedido().idPedido !== id) {
			return;
		}
		const error = comoError(rechazo);
		this.errorDelPago.set({ idPedido: id, texto: mensajeDeError(error) });
		// Se canceló, venció o pagó en otra pestaña: se muestra como está ahora
		if (error.codigo === 'PEDIDO_NO_PENDIENTE') {
			this.desactualizado.emit();
		}
	}
}

interface Mensaje {
	idPedido: number;
	texto: string;
}
