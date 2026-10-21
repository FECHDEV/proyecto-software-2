import {
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	effect,
	inject,
	linkedSignal,
	NgZone,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { PedidosService } from '../../core/api/pedidos.service';
import { comoErrorApi } from '../../core/modelos/error-api';
import { EstadoDePedido, NOMBRES_DE_ESTADO } from '../../core/modelos/pedido';
import { fechaCorta } from '../../shared/formato/fechas';
import { precio } from '../../shared/formato/precios';
import { comoReloj } from '../../shared/formato/tiempo';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';

// El color del chip de cada estado, como en la página del pedido (HU-14 RF-3)
const TONO: Record<EstadoDePedido, string> = {
	PENDIENTE_PAGO: 'tono-aviso',
	PAGADO: 'tono-exito',
	PAGO_A_DEVOLVER: 'tono-aviso',
	CANCELADO: 'tono-neutro',
	VENCIDO: 'tono-neutro',
};

// «Mis pedidos» (HU-14): todos los pedidos del Cliente, el más nuevo primero y
// de a 10; el que espera el pago va arriba, con el tiempo que le queda. La
// página va en la dirección (?pagina=), como en las listas de la gestión
@Component({
	selector: 'app-mis-pedidos',
	imports: [RouterLink],
	templateUrl: './mis-pedidos.page.html',
	styleUrl: './mis-pedidos.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisPedidosPage {
	private readonly pedidos = inject(PedidosService);
	private readonly router = inject(Router);
	private readonly ruta = inject(ActivatedRoute);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	private readonly numeroDePagina = computed(() => paginaDeLaUrl(this.consulta().get('pagina')));

	protected readonly recurso = this.pedidos.misPedidos(this.numeroDePagina);
	protected readonly datos = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// --- El pendiente: el tiempo lo cuenta el servidor; acá solo se descuenta (RF-2) ---
	private readonly pendiente = computed(() => this.datos()?.pendiente ?? null);
	private readonly recibido = linkedSignal({ source: this.pendiente, computation: () => Date.now() });
	private readonly ahora = linkedSignal({ source: this.recibido, computation: (recibido) => recibido });
	protected readonly reloj = computed(() => {
		const pendiente = this.pendiente();
		if (pendiente === null) {
			return '';
		}
		const restantes = pendiente.segundosParaPagar - (this.ahora() - this.recibido()) / 1000;
		return comoReloj(Math.ceil(restantes));
	});

	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly tono = TONO;
	protected readonly fecha = fechaCorta;
	protected readonly precio = precio;

	constructor() {
		// Fuera de la zona, como el reloj de la página del pedido: la señal ya
		// redibuja lo que la usa
		const intervalo = inject(NgZone).runOutsideAngular(() => setInterval(() => this.ahora.set(Date.now()), 1000));
		inject(DestroyRef).onDestroy(() => clearInterval(intervalo));
		// Al llegar a cero se pide la lista una vez: el servidor lo vence si
		// corresponde (o lo deja arriba si espera la consulta de su cobro)
		let pedidoRecargado: number | null = null;
		effect(() => {
			const pendiente = this.pendiente();
			if (pendiente !== null && this.reloj() === '00:00' && pedidoRecargado !== pendiente.idPedido) {
				pedidoRecargado = pendiente.idPedido;
				this.recurso.reload();
			}
		});
	}

	protected productos(cantidad: number): string {
		return cantidad === 1 ? '1 producto' : `${cantidad} productos`;
	}

	protected irAPagina(pagina: number): void {
		void this.router.navigate([], { relativeTo: this.ruta, queryParams: { pagina: pagina === 0 ? null : pagina } });
	}
}
