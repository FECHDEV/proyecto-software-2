import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';

import { DespachoService } from '../../core/api/despacho.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import {
	consultaDeDespacho,
	EstadoDeDespacho,
	ESTADOS_DE_DESPACHO,
	FiltrosDeDespacho,
	PedidoEnDespacho,
} from '../../core/modelos/despacho';
import { comoErrorApi } from '../../core/modelos/error-api';
import { NOMBRES_DE_ESTADO, TONO_DE_ESTADO } from '../../core/modelos/pedido';
import { fechaCorta } from '../../shared/formato/fechas';
import { precio } from '../../shared/formato/precios';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';
import { ultimoValor } from '../../shared/recursos/ultimo-valor';

// Cuánto se espera después de la última tecla antes de buscar
const ESPERA_DE_BUSQUEDA_MS = 300;

// Los pedidos de todos los clientes, para el personal (HU-17 RF-1 a RF-3): de
// entrada los pagados, que esperan retiro. El estado, la búsqueda y la página
// viven en la dirección, como en el inventario. Cada fila lleva a su ficha
@Component({
	selector: 'app-despacho',
	imports: [RouterLink],
	templateUrl: './despacho.page.html',
	styleUrl: './despacho.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DespachoPage {
	private readonly despacho = inject(DespachoService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()), { equal: mismosFiltros });

	protected readonly recurso = this.despacho.lista(this.filtros);
	protected readonly pagina = ultimoValor(this.recurso);
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// Lo escrito en la búsqueda, que llega a la dirección con un poco de espera (como en el inventario)
	private textoEnviado: string | null = null;
	protected readonly textoEscrito = linkedSignal<string, string>({
		source: () => this.filtros().texto,
		computation: (texto, previo) => (previo !== undefined && texto === this.textoEnviado ? previo.value : texto),
	});
	private espera: ReturnType<typeof setTimeout> | null = null;

	protected readonly estados = ESTADOS_DE_DESPACHO;
	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly tono = TONO_DE_ESTADO;
	protected readonly fecha = fechaCorta;
	protected readonly precio = precio;

	constructor() {
		inject(DestroyRef).onDestroy(() => this.cancelarEspera());
		// Si el servidor acomodó una página fuera de rango (RF-12), la dirección
		// muestra la que se ve, sin otra entrada en el historial
		effect(() => {
			if (this.recurso.isLoading() || !this.recurso.hasValue()) {
				return;
			}
			const mostrada = this.recurso.value()?.pagina;
			if (mostrada !== undefined && mostrada !== this.filtros().pagina) {
				this.navegar({ ...this.filtros(), pagina: mostrada }, true);
			}
		});
	}

	protected nombreDelEstado(estado: EstadoDeDespacho): string {
		return estado === 'TODOS' ? 'Todos' : NOMBRES_DE_ESTADO[estado];
	}

	protected productos(cantidad: number): string {
		return cantidad === 1 ? '1 producto' : `${cantidad} productos`;
	}

	protected alEscribir(texto: string): void {
		this.textoEscrito.set(texto);
		this.cancelarEspera();
		this.espera = setTimeout(() => {
			// Un espacio de más no cambia la búsqueda: no se vuelve a la primera página
			if (texto.trim() === this.filtros().texto.trim()) {
				return;
			}
			this.textoEnviado = texto.trim();
			this.filtrar({ texto });
		}, ESPERA_DE_BUSQUEDA_MS);
	}

	protected alElegirEstado(valor: string): void {
		this.filtrar({ estado: estadoDe(valor) });
	}

	protected irAPagina(pagina: number): void {
		// Mientras carga, o antes de la primera o después de la última, el toque se ignora: el botón no se
		// deshabilita, para no perder el foco (revisión total)
		const total = this.pagina()?.totalPaginas ?? 0;
		if (this.recurso.isLoading() || pagina < 0 || (pagina > 0 && pagina >= total)) {
			return;
		}
		this.navegar({ ...this.filtros(), pagina });
	}

	// Cambiar un filtro cambia la lista entera: se vuelve a la primera página
	private filtrar(cambios: Partial<FiltrosDeDespacho>): void {
		this.navegar({ ...this.filtros(), ...cambios, pagina: 0 });
	}

	private navegar(filtros: FiltrosDeDespacho, reemplazar = false): void {
		// Solo lo que filtra queda en la dirección, así la lista de entrada es /pedidos
		void this.router.navigate([], {
			relativeTo: this.ruta,
			queryParams: consultaDeDespacho(filtros),
			replaceUrl: reemplazar,
		});
	}

	private cancelarEspera(): void {
		if (this.espera !== null) {
			clearTimeout(this.espera);
			this.espera = null;
		}
	}
}

// La dirección la puede escribir cualquiera: un estado que no existe es «Pagado» (RF-12)
function estadoDe(valor: string | null): EstadoDeDespacho {
	return ESTADOS_DE_DESPACHO.find((estado) => estado === valor) ?? 'PAGADO';
}

function leerFiltros(consulta: ParamMap): FiltrosDeDespacho {
	return {
		estado: estadoDe(consulta.get('estado')),
		texto: consulta.get('texto') ?? '',
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

function mismosFiltros(a: FiltrosDeDespacho, b: FiltrosDeDespacho): boolean {
	return a.estado === b.estado && a.texto === b.texto && a.pagina === b.pagina;
}
