import {
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	ElementRef,
	inject,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import {
	LucideChevronDown,
	LucideChevronLeft,
	LucideChevronRight,
	LucideSearch,
	LucideSlidersHorizontal,
} from '@lucide/angular';

import { CatalogoService } from '../../core/api/catalogo.service';
import { CategoriasService } from '../../core/api/categorias.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import {
	aTarjeta,
	BUSQUEDA_MAXIMA,
	FiltrosDeCatalogo,
	NOMBRES_DE_ORDEN,
	OrdenCatalogo,
	ORDENES,
	SIN_FILTROS,
} from '../../core/modelos/catalogo';
import { cantidad, leerPrecio } from '../../shared/formato/precios';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';
import { TarjetaProducto } from '../../shared/tarjeta-producto/tarjeta-producto';
import { comoErrorApi } from '../../core/modelos/error-api';

// Cuánto se espera después de la última tecla antes de buscar
const ESPERA_DE_BUSQUEDA_MS = 300;
// Cuántos números de página se ven alrededor de la actual
// 5: con las dos flechas entran en una fila del celular
const PAGINAS_VISIBLES = 5;

type CampoDePrecio = 'precioMin' | 'precioMax';

// El catálogo (HU-09 RF-8 a RF-14): búsqueda, filtros, orden y páginas de 24.
// Todo vive en la dirección, como en las listas de la gestión: se comparte, se
// vuelve atrás y se recarga sin perder nada
@Component({
	selector: 'app-catalogo',
	imports: [
		TarjetaProducto,
		LucideChevronDown,
		LucideChevronLeft,
		LucideChevronRight,
		LucideSearch,
		LucideSlidersHorizontal,
	],
	templateUrl: './catalogo.page.html',
	styleUrl: './catalogo.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoPage {
	private readonly catalogo = inject(CatalogoService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);
	private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()));

	protected readonly listado = this.catalogo.listado(this.filtros);
	protected readonly pagina = computed(() => (this.listado.hasValue() ? this.listado.value() : undefined));
	protected readonly tarjetas = computed(() => this.pagina()?.contenido.map(aTarjeta) ?? []);
	protected readonly errorDeCarga = computed(() => {
		const error = this.listado.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});
	// La página y el orden no son filtros
	protected readonly hayFiltros = computed(
		() => JSON.stringify(aConsulta({ ...this.filtros(), pagina: 0, orden: 'RECIENTES' })) !== '{}',
	);

	protected readonly recursoDeCategorias = inject(CategoriasService).catalogo();
	protected readonly categorias = computed(() =>
		this.recursoDeCategorias.hasValue() ? (this.recursoDeCategorias.value() ?? []) : [],
	);
	private readonly recursoDeMarcas = this.catalogo.marcas();
	protected readonly marcas = computed(() => (this.recursoDeMarcas.hasValue() ? (this.recursoDeMarcas.value() ?? []) : []));

	// Lo escrito en la búsqueda, que llega a la dirección con un poco de espera
	protected readonly textoEscrito = linkedSignal<string, string>({
		source: () => this.filtros().q,
		// Lo escrito se conserva mientras la dirección diga lo mismo (los espacios no
		// viajan); si cambia por otra vía (Atrás, la búsqueda del encabezado), manda la dirección
		computation: (texto, previo) => (previo !== undefined && previo.value.trim() === texto ? previo.value : texto),
	});
	private espera: ReturnType<typeof setTimeout> | null = null;

	// Los precios escritos, tal cual, hasta que se aplican; y por qué no se aplicaron
	protected readonly precioEscrito = linkedSignal(() => ({
		precioMin: textoDePrecio(this.filtros().precioMin),
		precioMax: textoDePrecio(this.filtros().precioMax),
	}));
	protected readonly problemaDePrecio = signal<Partial<Record<CampoDePrecio, string>>>({});

	// En el celular los filtros se pliegan detrás de un botón
	protected readonly filtrosAbiertos = signal(false);

	protected readonly ordenes = ORDENES;
	protected readonly nombresDeOrden = NOMBRES_DE_ORDEN;
	protected readonly busquedaMaxima = BUSQUEDA_MAXIMA;
	protected readonly cantidad = cantidad;

	// Las cercanas a la actual y, siempre, la primera y la última; null es un salto («…») (revisión total)
	protected readonly numeros = computed((): (number | null)[] => {
		const pagina = this.pagina();
		if (pagina === undefined || pagina.totalPaginas <= 1) {
			return [];
		}
		const ultima = pagina.totalPaginas - 1;
		const desde = Math.max(0, Math.min(pagina.pagina - 2, pagina.totalPaginas - PAGINAS_VISIBLES));
		const hasta = Math.min(pagina.totalPaginas, desde + PAGINAS_VISIBLES);
		const cercanas = Array.from({ length: hasta - desde }, (_, i) => desde + i);
		// Un «…» que escondería una sola página la muestra: ocupa lo mismo
		const antes = desde === 0 ? [] : desde === 1 ? [0] : desde === 2 ? [0, 1] : [0, null];
		const despues = hasta > ultima ? [] : hasta === ultima ? [ultima] : hasta === ultima - 1 ? [hasta, ultima] : [null, ultima];
		return [...antes, ...cercanas, ...despues];
	});

	constructor() {
		inject(DestroyRef).onDestroy(() => this.cancelarEspera());
	}

	protected alEscribir(texto: string): void {
		this.textoEscrito.set(texto);
		this.cancelarEspera();
		this.espera = setTimeout(() => {
			this.filtrar({ q: texto });
		}, ESPERA_DE_BUSQUEDA_MS);
	}

	protected buscarYa(): void {
		this.cancelarEspera();
		this.filtrar({ q: this.textoEscrito() });
	}

	protected alElegirCategoria(valor: string): void {
		this.filtrar({ categoria: idDeLaUrl(valor) });
	}

	protected alElegirMarca(valor: string): void {
		this.filtrar({ marca: valor === '' ? null : valor });
	}

	protected alElegirOrden(valor: string): void {
		this.filtrar({ orden: ordenDe(valor) });
	}

	protected alMarcarDisponibles(marcado: boolean): void {
		this.filtrar({ disponibles: marcado });
	}

	protected alEscribirPrecio(campo: CampoDePrecio, texto: string): void {
		this.precioEscrito.update((previo) => ({ ...previo, [campo]: texto }));
	}

	// HU-09 RF-14: un precio que no se entiende, o un mínimo mayor que el máximo,
	// se explica junto al campo y no llega a la dirección
	protected aplicarPrecios(): void {
		const escrito = this.precioEscrito();
		const problemas: Partial<Record<CampoDePrecio, string>> = {};
		const leidos: Record<CampoDePrecio, number | null> = { precioMin: null, precioMax: null };
		for (const campo of ['precioMin', 'precioMax'] as const) {
			const texto = escrito[campo].trim();
			if (texto === '') {
				continue;
			}
			const valor = leerPrecio(texto);
			if (valor === null) {
				problemas[campo] = 'Escriba un precio con números, por ejemplo 99,50.';
			} else {
				leidos[campo] = valor;
			}
		}
		if (
			problemas.precioMin === undefined &&
			problemas.precioMax === undefined &&
			leidos.precioMin !== null &&
			leidos.precioMax !== null &&
			leidos.precioMin > leidos.precioMax
		) {
			problemas.precioMin = 'El mínimo no puede ser mayor que el máximo.';
		}
		this.problemaDePrecio.set(problemas);
		if (Object.keys(problemas).length === 0) {
			this.filtrar(leidos);
		}
	}

	protected quitarFiltros(): void {
		this.cancelarEspera();
		this.problemaDePrecio.set({});
		this.navegar(SIN_FILTROS);
	}

	// Las flechas usan aria-disabled y no disabled: en el extremo no hacen nada y
	// conservan el foco. Al cambiar de página, el foco sube al título con la vista
	protected irAPagina(pagina: number): void {
		const total = this.pagina()?.totalPaginas ?? 0;
		if (pagina < 0 || (total > 0 && pagina >= total && pagina !== 0)) {
			return;
		}
		this.navegar({ ...this.filtros(), pagina });
		if (typeof window.scrollTo === 'function') {
			window.scrollTo({ top: 0 });
		}
		this.titulo()?.nativeElement.focus({ preventScroll: true });
	}

	// Cambiar un filtro cambia la lista entera: se vuelve a la primera página
	private filtrar(cambios: Partial<FiltrosDeCatalogo>): void {
		this.navegar({ ...this.filtros(), ...cambios, pagina: 0 });
	}

	private navegar(filtros: FiltrosDeCatalogo): void {
		// Los precios escritos vuelven a ser los de la dirección: sus avisos ya no aplican
		this.problemaDePrecio.set({});
		void this.router.navigate([], { relativeTo: this.ruta, queryParams: aConsulta(filtros) });
	}

	private cancelarEspera(): void {
		if (this.espera !== null) {
			clearTimeout(this.espera);
			this.espera = null;
		}
	}
}

function ordenDe(valor: string | null): OrdenCatalogo {
	return ORDENES.find((orden) => orden === valor) ?? 'RECIENTES';
}

function textoDePrecio(valor: number | null): string {
	return valor === null ? '' : String(valor).replace('.', ',');
}

// La dirección la puede escribir cualquiera: lo que no es válido se descarta
// (HU-09 RF-13), y la búsqueda se recorta al largo que acepta la API
function leerFiltros(consulta: ParamMap): FiltrosDeCatalogo {
	const precioMin = leerPrecio(consulta.get('precioMin') ?? '');
	const precioMax = leerPrecio(consulta.get('precioMax') ?? '');
	const invertidos = precioMin !== null && precioMax !== null && precioMin > precioMax;
	return {
		q: (consulta.get('q') ?? '').slice(0, BUSQUEDA_MAXIMA),
		categoria: idDeLaUrl(consulta.get('categoria')),
		marca: consulta.get('marca')?.trim() || null,
		precioMin: invertidos ? null : precioMin,
		precioMax: invertidos ? null : precioMax,
		disponibles: consulta.get('disponibles') === 'true',
		orden: ordenDe(consulta.get('orden')),
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

// Solo lo que filtra queda en la dirección, así el catálogo sin filtros es /catalogo
function aConsulta(filtros: FiltrosDeCatalogo): Record<string, string | number | boolean | undefined> {
	const q = filtros.q.trim();
	const resultado: Record<string, string | number | boolean | undefined> = {
		q: q === '' ? undefined : q,
		categoria: filtros.categoria ?? undefined,
		marca: filtros.marca ?? undefined,
		precioMin: filtros.precioMin ?? undefined,
		precioMax: filtros.precioMax ?? undefined,
		disponibles: filtros.disponibles ? true : undefined,
		orden: filtros.orden === 'RECIENTES' ? undefined : filtros.orden,
		pagina: filtros.pagina > 0 ? filtros.pagina : undefined,
	};
	return Object.fromEntries(Object.entries(resultado).filter(([, valor]) => valor !== undefined));
}
