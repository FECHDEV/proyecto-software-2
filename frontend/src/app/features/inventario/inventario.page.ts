import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { LucideCircleSlash, LucideTriangleAlert } from '@lucide/angular';
import { comoErrorApi } from '../../core/modelos/error-api';

import { InventarioService } from '../../core/api/inventario.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { FiltrosDeInventario, ProductoEnInventario } from '../../core/modelos/inventario';
import { unidadPara } from '../../core/modelos/producto';
import { cantidad } from '../../shared/formato/precios';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';
import { ultimoValor } from '../../shared/recursos/ultimo-valor';

// Cuánto se espera después de la última tecla antes de buscar
const ESPERA_DE_BUSQUEDA_MS = 300;

// Inventario (HU-08 RF-10): el stock de cada producto. Los filtros y la página
// viven en la URL, como en «Productos». Cada fila lleva a su stock y su historial.
@Component({
	selector: 'app-inventario',
	imports: [RouterLink, LucideCircleSlash, LucideTriangleAlert],
	templateUrl: './inventario.page.html',
	styleUrl: './inventario.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventarioPage {
	private readonly inventario = inject(InventarioService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()));

	protected readonly listado = this.inventario.listado(this.filtros);
	protected readonly pagina = ultimoValor(this.listado);
	protected readonly errorDeCarga = computed(() => {
		const error = this.listado.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// Todas, también las ocultas: el inventario lista sus productos
	protected readonly recursoDeCategorias = this.inventario.categorias();
	protected readonly categorias = computed(() =>
		this.recursoDeCategorias.hasValue() ? (this.recursoDeCategorias.value() ?? []) : [],
	);

	// Lo escrito en la búsqueda, que llega a la URL con un poco de espera (como en «Usuarios»)
	private textoEnviado: string | null = null;
	protected readonly textoEscrito = linkedSignal<string, string>({
		source: () => this.filtros().texto,
		computation: (texto, previo) =>
			previo !== undefined && texto === this.textoEnviado ? previo.value : texto,
	});
	private espera: ReturnType<typeof setTimeout> | null = null;

	protected readonly cantidad = cantidad;
	protected readonly unidadPara = unidadPara;

	constructor() {
		inject(DestroyRef).onDestroy(() => this.cancelarEspera());
	}

	protected alEscribir(texto: string): void {
		this.textoEscrito.set(texto);
		this.cancelarEspera();
		this.espera = setTimeout(() => {
			this.textoEnviado = texto.trim();
			this.filtrar({ texto });
		}, ESPERA_DE_BUSQUEDA_MS);
	}

	protected alElegirCategoria(valor: string): void {
		this.filtrar({ categoria: idDeLaUrl(valor) });
	}

	protected alElegirEstado(valor: string): void {
		this.filtrar({ activo: estadoDe(valor) });
	}

	protected alMarcarStockBajo(marcado: boolean): void {
		this.filtrar({ soloBajo: marcado });
	}

	protected irAPagina(pagina: number): void {
		// Mientras carga, o antes de la primera o después de la última, el toque se ignora: el botón no se
		// deshabilita, para no perder el foco (revisión total)
		const total = this.pagina()?.totalPaginas ?? 0;
		if (this.listado.isLoading() || pagina < 0 || (pagina > 0 && pagina >= total)) {
			return;
		}
		this.navegar({ ...this.filtros(), pagina });
	}

	// Cambiar un filtro cambia la lista entera: se vuelve a la primera página
	private filtrar(cambios: Partial<FiltrosDeInventario>): void {
		this.navegar({ ...this.filtros(), ...cambios, pagina: 0 });
	}

	private navegar(filtros: FiltrosDeInventario): void {
		void this.router.navigate([], { relativeTo: this.ruta, queryParams: aConsulta(filtros) });
	}

	private cancelarEspera(): void {
		if (this.espera !== null) {
			clearTimeout(this.espera);
			this.espera = null;
		}
	}
}

function estadoDe(valor: string | null): boolean | null {
	return valor === 'true' ? true : valor === 'false' ? false : null;
}

// La URL la puede escribir cualquiera: lo que no es válido se descarta
function leerFiltros(consulta: ParamMap): FiltrosDeInventario {
	return {
		texto: consulta.get('texto') ?? '',
		categoria: idDeLaUrl(consulta.get('categoria')),
		activo: estadoDe(consulta.get('activo')),
		soloBajo: consulta.get('bajo') === 'true',
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

// Solo lo que filtra queda en la URL, así la lista sin filtros es /inventario
function aConsulta(filtros: FiltrosDeInventario): Record<string, string | number | boolean | undefined> {
	const texto = filtros.texto.trim();
	return {
		texto: texto === '' ? undefined : texto,
		categoria: filtros.categoria ?? undefined,
		activo: filtros.activo ?? undefined,
		bajo: filtros.soloBajo ? true : undefined,
		pagina: filtros.pagina > 0 ? filtros.pagina : undefined,
	};
}
