import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';

import { VentasService } from '../../core/api/ventas.service';
import { unidadPara } from '../../core/modelos/producto';
import { ProductoParaVenta } from '../../core/modelos/venta';
import { cantidad, precio } from '../../shared/formato/precios';

// Cuánto se espera después de la última tecla antes de buscar
const ESPERA_DE_BUSQUEDA_MS = 300;

// La búsqueda de «Nueva venta» (HU-23 RF-1, RF-3): los productos que el
// catálogo vende, con su precio y lo que hay disponible. Tocar uno lo agrega a
// la venta, que decide «Nueva venta». En un componente propio: los estilos de la
// pantalla pasaban el presupuesto de 6 kB
@Component({
	selector: 'app-busqueda-de-productos',
	templateUrl: './busqueda-de-productos.html',
	styleUrl: './busqueda-de-productos.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	// Una región con su título, como la sección que era dentro de la pantalla
	host: { role: 'region', 'aria-labelledby': 'titulo-buscar' },
})
export class BusquedaDeProductos {
	private readonly ventas = inject(VentasService);

	// Con el QR a la vista o mientras se registra, no se agrega nada
	readonly bloqueada = input(false);
	readonly elegir = output<ProductoParaVenta>();

	protected readonly precio = precio;
	protected readonly unidadPara = unidadPara;
	protected readonly textoBuscado = signal('');
	private readonly buscado = signal('');
	protected readonly resultados = this.ventas.productos(this.buscado);
	private espera: ReturnType<typeof setTimeout> | null = null;

	constructor() {
		inject(DestroyRef).onDestroy(() => this.cancelarEspera());
	}

	protected alBuscar(texto: string): void {
		this.textoBuscado.set(texto);
		this.cancelarEspera();
		this.espera = setTimeout(() => this.buscado.set(texto), ESPERA_DE_BUSQUEDA_MS);
	}

	protected tocar(producto: ProductoParaVenta): void {
		if (!this.bloqueada()) {
			this.elegir.emit(producto);
		}
	}

	protected disponible(producto: ProductoParaVenta): string {
		return `${cantidad(producto.disponible)} ${unidadPara(producto.unidad, producto.disponible)}`;
	}

	private cancelarEspera(): void {
		if (this.espera !== null) {
			clearTimeout(this.espera);
			this.espera = null;
		}
	}
}
