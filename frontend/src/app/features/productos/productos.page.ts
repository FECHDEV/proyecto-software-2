import {
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	inject,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { LucidePackage, LucidePlus } from '@lucide/angular';

import { CategoriasService } from '../../core/api/categorias.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { ProductosService } from '../../core/api/productos.service';
import { comoError, comoErrorApi } from '../../core/modelos/error-api';
import { FiltrosDeProductos, NOMBRES_DE_UNIDAD, ProductoResumen } from '../../core/modelos/producto';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { precio } from '../../shared/formato/precios';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';
import { ultimoValor } from '../../shared/recursos/ultimo-valor';

// Cuánto se espera después de la última tecla antes de buscar
const ESPERA_DE_BUSQUEDA_MS = 300;

// Lista de productos de la gestión (HU-07 RF-10). Los filtros y la página viven
// en la URL, como en «Usuarios»: recargar o compartir el enlace muestra la misma lista.
@Component({
	selector: 'app-productos',
	imports: [RouterLink, DialogoConfirmacion, LucidePackage, LucidePlus],
	templateUrl: './productos.page.html',
	styleUrl: './productos.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductosPage {
	private readonly productos = inject(ProductosService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()));

	protected readonly listado = this.productos.listado(this.filtros);
	protected readonly pagina = ultimoValor(this.listado);
	protected readonly errorDeCarga = computed(() => {
		const error = this.listado.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// Las categorías para el filtro, también las ocultas: sus productos se gestionan igual
	protected readonly recursoDeCategorias = inject(CategoriasService).gestion();
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

	protected readonly procesando = signal<ReadonlySet<number>>(new Set());
	protected readonly avisoDeError = signal('');
	protected readonly avisoDeExito = signal('');

	protected readonly precio = precio;
	protected readonly nombresDeUnidad = NOMBRES_DE_UNIDAD;

	constructor() {
		inject(DestroyRef).onDestroy(() => this.cancelarEspera());
	}

	protected urlDeImagen(producto: ProductoResumen): string | null {
		return producto.imagenPrincipal === null
			? null
			: this.productos.urlDeImagen(producto.idProducto, producto.imagenPrincipal);
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

	protected irAPagina(pagina: number): void {
		// Mientras carga, o antes de la primera o después de la última, el toque se ignora: el botón no se
		// deshabilita, para no perder el foco (revisión total)
		const total = this.pagina()?.totalPaginas ?? 0;
		if (this.listado.isLoading() || pagina < 0 || (pagina > 0 && pagina >= total)) {
			return;
		}
		this.navegar({ ...this.filtros(), pagina });
	}

	// Desactivar lo saca de la venta y pide confirmación (RF-9); activarlo solo lo devuelve
	protected async alCambiarActivo(producto: ProductoResumen): Promise<void> {
		if (this.procesando().has(producto.idProducto)) {
			return;
		}
		const desactivar = producto.activo;
		if (desactivar) {
			const confirmado = await this.dialogo().abrir({
				titulo: `¿Desactivar «${producto.nombre}»?`,
				mensaje: 'Deja de venderse, pero se conserva para los pedidos y los reportes. Puede activarlo cuando quiera.',
				confirmar: 'Desactivar',
			});
			if (!confirmado) {
				return;
			}
		}
		this.procesando.update((ids) => new Set(ids).add(producto.idProducto));
		this.avisoDeError.set('');
		this.avisoDeExito.set('');
		try {
			const actualizado = await this.productos.cambiarActivo(producto.idProducto, !desactivar);
			const filtro = this.filtros().activo;
			if (filtro !== null && filtro !== actualizado.activo) {
				// Con el filtro de estado, el producto sale de la lista: se vuelve a pedir
				this.listado.reload();
			} else {
				this.actualizarFila(actualizado.idProducto, actualizado.activo);
			}
			this.avisoDeExito.set(`«${actualizado.nombre}» quedó ${actualizado.activo ? 'activo' : 'inactivo'}.`);
		} catch (error: unknown) {
			this.avisoDeError.set(mensajeDeError(comoError(error)));
		} finally {
			this.procesando.update((ids) => {
				const restantes = new Set(ids);
				restantes.delete(producto.idProducto);
				return restantes;
			});
		}
	}

	// La fila se actualiza con lo que devolvió el backend, sin volver a pedir la lista
	private actualizarFila(idProducto: number, activo: boolean): void {
		this.pagina.update((pagina) =>
				pagina === undefined
					? pagina
					: {
							...pagina,
							contenido: pagina.contenido.map((cada) =>
								cada.idProducto === idProducto ? { ...cada, activo } : cada,
							),
						},
		);
	}

	// Cambiar un filtro cambia la lista entera: se vuelve a la primera página
	private filtrar(cambios: Partial<FiltrosDeProductos>): void {
		this.navegar({ ...this.filtros(), ...cambios, pagina: 0 });
	}

	private navegar(filtros: FiltrosDeProductos): void {
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

// La URL la puede escribir cualquiera: lo que no es válido se descarta en vez
// de llegar al backend y volver como un 400
function leerFiltros(consulta: ParamMap): FiltrosDeProductos {
	return {
		categoria: idDeLaUrl(consulta.get('categoria')),
		activo: estadoDe(consulta.get('activo')),
		texto: consulta.get('texto') ?? '',
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

// Solo lo que filtra queda en la URL, así la lista sin filtros es /productos
function aConsulta(filtros: FiltrosDeProductos): Record<string, string | number | boolean | undefined> {
	const texto = filtros.texto.trim();
	return {
		categoria: filtros.categoria ?? undefined,
		activo: filtros.activo ?? undefined,
		texto: texto === '' ? undefined : texto,
		pagina: filtros.pagina > 0 ? filtros.pagina : undefined,
	};
}
