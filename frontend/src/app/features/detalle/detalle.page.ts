import { HttpErrorResponse } from '@angular/common/http';
import {
	afterRenderEffect,
	ChangeDetectionStrategy,
	Component,
	computed,
	effect,
	ElementRef,
	inject,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideCircleCheck, LucideCircleSlash, LucidePencil } from '@lucide/angular';
import { comoErrorApi } from '../../core/modelos/error-api';

import { CatalogoService } from '../../core/api/catalogo.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { MARCA } from '../../core/marca';
import { aTarjeta, idDeProducto, urlDeImagen, urlDeModelo } from '../../core/modelos/catalogo';
import { SesionService } from '../../core/sesion/sesion.service';
import { Carrusel } from '../../shared/carrusel/carrusel';
import { precio } from '../../shared/formato/precios';
import { Galeria } from '../../shared/galeria/galeria';
import { TarjetaProducto } from '../../shared/tarjeta-producto/tarjeta-producto';
import { AgregarAlCarrito } from './agregar-al-carrito';

// El detalle de un producto en la tienda (HU-10), con o sin sesión: fotos,
// datos, disponibilidad sin la cantidad, descripción, ficha técnica y «Más de»
// su categoría. Un producto que no se puede listar se ve igual que uno que no
// existe (RF-8). La compra la hace AgregarAlCarrito (HU-11)
@Component({
	selector: 'app-detalle',
	imports: [
		AgregarAlCarrito,
		RouterLink,
		Carrusel,
		Galeria,
		TarjetaProducto,
		LucideCircleCheck,
		LucideCircleSlash,
		LucidePencil,
	],
	templateUrl: './detalle.page.html',
	styleUrl: './detalle.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetallePage {
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });
	private readonly sesion = inject(SesionService);
	private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

	// null si la dirección no trae un número de producto: no se consulta (RF-9)
	protected readonly id = computed(() => idDeProducto(this.parametros().get('id')));
	protected readonly recurso = inject(CatalogoService).detalle(this.id);

	protected readonly producto = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));

	protected readonly noDisponible = computed(() => {
		const error = this.recurso.error();
		return this.id() === null || (error instanceof HttpErrorResponse && error.status === 404);
	});

	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noDisponible() ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly fotos = computed(() => {
		const producto = this.producto();
		return producto === undefined ? [] : producto.imagenes.map((imagen) => urlDeImagen(producto.idProducto, imagen));
	});

	// HU-16: la dirección del modelo 3D, o null; la galería ofrece «Ver en 3D»
	protected readonly modelo = computed(() => {
		const producto = this.producto();
		return producto?.modelo ? urlDeModelo(producto.idProducto, producto.modelo) : null;
	});

	protected readonly precio = computed(() => {
		const producto = this.producto();
		return producto === undefined ? '' : precio(producto.precio);
	});

	// «Unidad» no se escribe junto al precio, como en la tarjeta
	protected readonly unidad = computed(() => {
		const unidad = this.producto()?.unidad;
		return unidad === undefined || unidad === 'UNIDAD' ? null : unidad.toLowerCase();
	});

	protected readonly relacionados = computed(() => this.producto()?.relacionados.map(aTarjeta) ?? []);

	// Usabilidad: el permiso de editar lo valida el backend (RF-10)
	protected readonly esAdministrador = computed(() => this.sesion.rol() === 'ADMINISTRADOR');

	constructor() {
		const pestana = inject(Title);
		effect(() => {
			const nombre = this.producto()?.nombre;
			if (nombre !== undefined) {
				pestana.setTitle(`${nombre} · ${MARCA.nombre}`);
			}
		});

		// Al pasar de un producto a otro desde «Más de…», el foco va a su nombre.
		// La página ya vuelve arriba al cambiar de ruta (provideDesplazamiento)
		let anterior: number | null = null;
		afterRenderEffect(() => {
			const idProducto = this.producto()?.idProducto;
			const titulo = this.titulo()?.nativeElement;
			if (idProducto === undefined || titulo === undefined || idProducto === anterior) {
				return;
			}
			if (anterior !== null) {
				titulo.focus({ preventScroll: true });
			}
			anterior = idProducto;
		});
	}
}
