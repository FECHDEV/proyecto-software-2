import { ChangeDetectionStrategy, Component, computed, input, linkedSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideCircleSlash, LucideWrench } from '@lucide/angular';

import { precio } from '../formato/precios';

// Lo que muestra la tarjeta, ya resuelto por la pantalla: shared/ no conoce los
// modelos de core/. unidad va en minúsculas, o null cuando no se escribe
export interface DatosDeTarjeta {
	nombre: string;
	marca: string | null;
	precio: number;
	unidad: string | null;
	imagen: string | null;
	agotado: boolean;
	enlace: string;
	// Tiene modelo 3D: la marca «3D» sobre la foto (HU-16)
	conModelo: boolean;
}

// La tarjeta de un producto en la tienda (HU-09 RF-6, RF-7): una placa de lona
// clara con la foto sin teñir, el nombre, la marca y el precio con su unidad.
// Toda la placa es un solo enlace al detalle
@Component({
	selector: 'app-tarjeta-producto',
	imports: [RouterLink, LucideCircleSlash, LucideWrench],
	templateUrl: './tarjeta-producto.html',
	styleUrl: './tarjeta-producto.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarjetaProducto {
	readonly datos = input.required<DatosDeTarjeta>();

	// La unidad va en su propia línea, más chica: el monto nunca se corta
	protected readonly precio = computed(() => precio(this.datos().precio));
	// Una foto que no carga muestra el ícono; vuelve a probar si cambia la foto
	protected readonly fotoRota = linkedSignal({ source: () => this.datos().imagen, computation: () => false });
	// Sin foto, o con la foto rota: el fondo oscuro, y la marca «3D» invertida
	protected readonly sinFoto = computed(() => this.datos().imagen === null || this.fotoRota());
}
