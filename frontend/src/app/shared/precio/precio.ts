import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { precio } from '../formato/precios';

// El precio de un producto (HU-24 RF-11), sin negocio: lo que cobra la tienda y, si tiene descuento, el normal
// tachado y la marca «−15 %». La letra y el color los pone quien lo usa; la unidad («/ metro») también
@Component({
	selector: 'app-precio',
	templateUrl: './precio.html',
	styleUrl: './precio.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Precio {
	readonly precio = input.required<number>();
	readonly precioNormal = input<number | null>(null);
	readonly descuento = input<number | null>(null);

	protected readonly cobrado = computed(() => precio(this.precio()));
	// Sin el normal no hay nada que tachar: se muestra como sin descuento
	protected readonly rebaja = computed(() => {
		const normal = this.precioNormal();
		const descuento = this.descuento();
		return normal === null || descuento === null ? null : { normal: precio(normal), descuento };
	});
}
