import { ChangeDetectionStrategy, Component, computed, effect, inject, linkedSignal, NgZone } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { PedidosService } from '../../core/api/pedidos.service';
import { comoErrorApi } from '../../core/modelos/error-api';
import { MisPedidos, NOMBRES_DE_ESTADO, TONO_DE_ESTADO } from '../../core/modelos/pedido';
import { fechaCorta } from '../../shared/formato/fechas';
import { precio } from '../../shared/formato/precios';
import { comoReloj } from '../../shared/formato/tiempo';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';

// Cada cuánto se pide la lista mientras el pendiente espera la consulta de su
// cobro, como la consulta de la página del pedido
const ESPERA_SIN_TIEMPO = 5000;

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
	// Mientras carga otra página queda lo último que se mostró: la lista y el
	// pendiente no parpadean
	protected readonly datos = linkedSignal<MisPedidos | undefined, MisPedidos | undefined>({
		source: () => (this.recurso.hasValue() ? this.recurso.value() : undefined),
		computation: (valor, previo) => valor ?? previo?.value,
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// --- El pendiente: el tiempo lo cuenta el servidor; acá solo se descuenta (RF-2) ---
	private readonly pendiente = computed(() => this.datos()?.pendiente ?? null);
	private readonly hayPendiente = computed(() => this.pendiente() !== null);
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
	// Pasó la hora (el servidor redondea hacia arriba: 0 es que ya pasó) y
	// todavía está arriba: espera la consulta de su cobro (HU-13 RF-7)
	protected readonly sinTiempo = computed(() => this.hayPendiente() && this.reloj() === '00:00');

	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly tono = TONO_DE_ESTADO;
	protected readonly fecha = fechaCorta;
	protected readonly precio = precio;

	constructor() {
		// El reloj corre solo mientras hay un pendiente, y fuera de la zona, como
		// el de la página del pedido: la señal ya redibuja lo que la usa
		const zona = inject(NgZone);
		effect((alLimpiar) => {
			if (!this.hayPendiente()) {
				return;
			}
			const intervalo = zona.runOutsideAngular(() => setInterval(() => this.ahora.set(Date.now()), 1000));
			alLimpiar(() => clearInterval(intervalo));
		});
		// Sin tiempo, la lista se pide cada 5 segundos hasta que el pedido deja
		// de estar arriba: el servidor lo vence, o el sistema de pago dice que
		// se pagó (RF-4; HU-13 RF-7)
		effect((alLimpiar) => {
			if (!this.sinTiempo()) {
				return;
			}
			const consulta = zona.runOutsideAngular(() => setInterval(() => this.recurso.reload(), ESPERA_SIN_TIEMPO));
			alLimpiar(() => clearInterval(consulta));
		});
		// Si el servidor acomodó una página fuera de rango (RF-8), la dirección
		// muestra la que se ve, sin otra entrada en el historial
		effect(() => {
			if (this.recurso.isLoading() || !this.recurso.hasValue()) {
				return;
			}
			const mostrada = this.recurso.value()?.pagina.pagina;
			// También limpia lo que no es una página («-3», «abc»)
			if (mostrada !== undefined && this.consulta().get('pagina') !== (mostrada === 0 ? null : `${mostrada}`)) {
				this.irAPagina(mostrada, true);
			}
		});
	}

	protected productos(cantidad: number): string {
		return cantidad === 1 ? '1 producto' : `${cantidad} productos`;
	}

	protected irAPagina(pagina: number, reemplazar = false): void {
		// Mientras carga, o antes de la primera o después de la última, el toque se ignora: el botón no se
		// deshabilita, para no perder el foco (revisión total)
		const total = this.datos()?.pagina.totalPaginas ?? 0;
		if (this.recurso.isLoading() || pagina < 0 || (pagina > 0 && pagina >= total)) {
			return;
		}
		void this.router.navigate([], {
			relativeTo: this.ruta,
			queryParams: { pagina: pagina === 0 ? null : pagina },
			replaceUrl: reemplazar,
		});
	}
}
