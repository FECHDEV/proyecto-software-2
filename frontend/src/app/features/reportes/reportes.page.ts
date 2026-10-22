import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideFileSpreadsheet, LucideFileText } from '@lucide/angular';

import { FormatoDeExportacion, ReportesService } from '../../core/api/reportes.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { comoError } from '../../core/modelos/error-api';

import {
	Atajo,
	ATAJOS,
	consultaDeReportes,
	FiltrosDeReportes,
	hoyEnBolivia,
	leerConsultaDeReportes,
	periodoDeAtajo,
	Seccion,
	SECCIONES,
} from '../../core/modelos/reportes';
import { NOMBRES_DE_TIPO, TIPOS_DE_CLIENTE } from '../../core/modelos/usuario';
import { descargarArchivo } from '../../shared/descarga/descargar-archivo';
import { ReportesClientes } from './reportes-clientes';
import { ReportesPerdidos } from './reportes-perdidos';
import { ReportesProductos } from './reportes-productos';
import { ReportesResumen } from './reportes-resumen';
import { ReportesSinVenta } from './reportes-sin-venta';

// Los reportes de ventas e ingresos, para el Contador y el Administrador
// (HU-18). La sección, el período, el tipo de cliente y la página viven en la
// dirección, como en el inventario y los pedidos; cada sección pide solo lo suyo
@Component({
	selector: 'app-reportes',
	imports: [
		RouterLink,
		LucideFileSpreadsheet,
		LucideFileText,
		ReportesResumen,
		ReportesProductos,
		ReportesSinVenta,
		ReportesClientes,
		ReportesPerdidos,
	],
	templateUrl: './reportes.page.html',
	styleUrl: './reportes.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesPage {
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);
	private readonly reportes = inject(ReportesService);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	// El día de Bolivia (RF-3), de nuevo con cada cambio de la dirección: una pestaña abierta de un día al otro sigue
	// al día
	protected readonly hoy = computed(() => {
		this.consulta();
		return hoyEnBolivia();
	});
	private readonly lectura = computed(() => leerConsultaDeReportes((clave) => this.consulta().get(clave), this.hoy()));
	protected readonly filtros = computed(() => this.lectura().filtros, { equal: mismosFiltros });
	protected readonly avisoDePeriodo = signal('');
	// HU-19: el formato que se está preparando, o null
	protected readonly exportando = signal<FormatoDeExportacion | null>(null);
	protected readonly errorDeExportacion = signal('');

	protected readonly secciones = SECCIONES;
	protected readonly atajos = ATAJOS;
	protected readonly tipos = TIPOS_DE_CLIENTE;
	protected readonly nombresDeTipo = NOMBRES_DE_TIPO;
	protected readonly atajoActivo = computed(() => {
		const { desde, hasta } = this.filtros();
		return ATAJOS.find((atajo) => {
			const periodo = periodoDeAtajo(atajo.valor, this.hoy());
			return periodo.desde === desde && periodo.hasta === hasta;
		})?.valor;
	});
	// «Sin venderse» no depende del período ni del tipo; «Clientes», del período
	protected readonly usaPeriodo = computed(() => !['sin-venta', 'clientes'].includes(this.filtros().seccion));
	protected readonly usaTipo = computed(() => this.filtros().seccion !== 'sin-venta');

	constructor() {
		// Un período que no servía se lee como lo más cercano (RF-3): la dirección
		// pasa a mostrar el que se ve, sin otra entrada en el historial, y se avisa
		effect(() => {
			if (this.lectura().ajustado) {
				this.avisoDePeriodo.set('Ajustamos el período al más cercano que se puede consultar.');
				this.navegar(this.filtros(), true);
			}
		});
	}

	protected consultaDeSeccion(seccion: Seccion): Record<string, string | number> {
		return consultaDeReportes({ ...this.filtros(), seccion, pagina: 0 }, this.hoy());
	}

	// HU-19: todo el reporte con los filtros de la dirección, en cualquier sección
	// (RF-2); uno a la vez, aunque se haga doble clic (RF-12). Los botones van con
	// aria-disabled y no disabled, para que el foco no se pierda mientras espera
	protected async exportar(formato: FormatoDeExportacion): Promise<void> {
		if (this.exportando() !== null) {
			return;
		}
		this.exportando.set(formato);
		this.errorDeExportacion.set('');
		try {
			const { archivo, nombre } = await this.reportes.exportar(this.filtros(), formato);
			descargarArchivo(archivo, nombre);
		} catch (error) {
			this.errorDeExportacion.set(mensajeDeError(comoError(error)));
		} finally {
			this.exportando.set(null);
		}
	}

	// El día del momento del clic: la pestaña pudo quedar abierta de un día al otro
	protected alElegirAtajo(atajo: Atajo): void {
		this.filtrar(periodoDeAtajo(atajo, hoyEnBolivia()));
	}

	// Un campo vaciado no cambia nada: el período siempre tiene sus dos fechas
	protected alCambiarFecha(campo: 'desde' | 'hasta', valor: string): void {
		if (valor !== '' && valor !== this.filtros()[campo]) {
			this.filtrar({ [campo]: valor });
		}
	}

	protected alElegirTipo(valor: string): void {
		this.filtrar({ tipo: TIPOS_DE_CLIENTE.find((tipo) => tipo === valor) ?? null });
	}

	// Lo que cambia una sección (orden, días, página). Cambiar un filtro cambia
	// la lista entera: se vuelve a la primera página
	protected alCambiarSeccion(cambios: Partial<FiltrosDeReportes>): void {
		if (cambios.pagina !== undefined) {
			this.navegar({ ...this.filtros(), ...cambios });
		} else {
			this.filtrar(cambios);
		}
	}

	private filtrar(cambios: Partial<FiltrosDeReportes>): void {
		this.avisoDePeriodo.set('');
		this.navegar({ ...this.filtros(), ...cambios, pagina: 0 });
	}

	private navegar(filtros: FiltrosDeReportes, reemplazar = false): void {
		void this.router.navigate([], {
			relativeTo: this.ruta,
			queryParams: consultaDeReportes(filtros, this.hoy()),
			replaceUrl: reemplazar,
		});
	}
}

function mismosFiltros(a: FiltrosDeReportes, b: FiltrosDeReportes): boolean {
	return (
		a.seccion === b.seccion &&
		a.desde === b.desde &&
		a.hasta === b.hasta &&
		a.tipo === b.tipo &&
		a.orden === b.orden &&
		a.dias === b.dias &&
		a.pagina === b.pagina
	);
}
