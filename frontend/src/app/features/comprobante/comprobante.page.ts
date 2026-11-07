import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { VentasService } from '../../core/api/ventas.service';
import { MARCA } from '../../core/marca';
import { comoError, comoErrorApi } from '../../core/modelos/error-api';
import { NOMBRES_DE_ESTADO, TONO_DE_ESTADO } from '../../core/modelos/pedido';
import { unidadPara } from '../../core/modelos/producto';
import { NOMBRES_DE_MEDIO, Venta } from '../../core/modelos/venta';
import { puedeEntrar } from '../../core/sesion/modulos';
import { SesionService } from '../../core/sesion/sesion.service';
import { fechaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';

// Como el backend (AnulacionRequest)
const MAXIMO_MOTIVO = 200;

// El comprobante de una venta del mostrador (HU-23 RF-15, RF-18, RF-19): se
// imprime con el navegador en formato de ticket, sin el encabezado ni los
// botones de la tienda, con «No válido como factura» (sin SIAT). El
// Administrador anula desde acá la venta en efectivo del día, con un motivo
@Component({
	selector: 'app-comprobante',
	imports: [RouterLink],
	templateUrl: './comprobante.page.html',
	styleUrl: './comprobante.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComprobantePage {
	private readonly ventas = inject(VentasService);
	private readonly sesion = inject(SesionService);
	private readonly injector = inject(Injector);

	protected readonly marca = MARCA;
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;
	protected readonly unidadPara = unidadPara;
	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly tono = TONO_DE_ESTADO;
	protected readonly nombresDeMedio = NOMBRES_DE_MEDIO;
	protected readonly maximoMotivo = MAXIMO_MOTIVO;

	// Solo dígitos: lo demás es una dirección que no sirve (casos límite)
	private readonly id = toSignal(
		inject(ActivatedRoute).paramMap.pipe(map((parametros) => idDe(parametros.get('id')))),
		{ requireSync: true },
	);
	protected readonly recurso = this.ventas.venta(this.id);
	// La anulación deja la venta como quedó, sin volver a pedirla
	private readonly anulada = signal<Venta | null>(null);
	protected readonly venta = computed(() => {
		const anulada = this.anulada();
		return anulada !== null && anulada.idPedido === this.recurso.value()?.idPedido ? anulada : this.recurso.value();
	});
	protected readonly noEncontrada = computed(() => {
		if (this.id() === null) {
			return true;
		}
		const error = this.recurso.error();
		return error !== undefined && comoErrorApi(error).status === 404;
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noEncontrada() ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly esAdministrador = computed(() => this.sesion.rol() === 'ADMINISTRADOR');
	protected readonly vende = computed(() => puedeEntrar(this.sesion.rol(), 'ventas'));

	private readonly dialogo = viewChild<ElementRef<HTMLDialogElement>>('dialogo');
	protected readonly motivo = signal('');
	protected readonly errorDelMotivo = signal('');
	protected readonly anulando = signal(false);
	protected readonly errorDeAnulacion = signal('');

	protected fechaYHora(iso: string): string {
		return `${fechaCorta(iso)} ${iso.slice(11, 16)}`;
	}

	protected imprimir(): void {
		window.print();
	}

	// RF-18: pide el motivo antes de anular
	protected pedirAnulacion(): void {
		this.motivo.set('');
		this.errorDelMotivo.set('');
		this.errorDeAnulacion.set('');
		this.dialogo()?.nativeElement.showModal();
	}

	protected cerrarDialogo(): void {
		this.dialogo()?.nativeElement.close();
	}

	// RF-19: el stock vuelve y la venta queda «Anulado»; RF-20: si ya no se puede, lo dice
	protected async anular(): Promise<void> {
		const venta = this.venta();
		const motivo = this.motivo().trim();
		if (venta === undefined || this.anulando()) {
			return;
		}
		if (motivo === '') {
			this.errorDelMotivo.set('Escriba por qué se anula.');
			return;
		}
		if (motivo.length > MAXIMO_MOTIVO) {
			this.errorDelMotivo.set(`El motivo admite hasta ${MAXIMO_MOTIVO} caracteres.`);
			return;
		}
		this.anulando.set(true);
		try {
			this.anulada.set(await this.ventas.anular(venta.idPedido, motivo));
			this.cerrarDialogo();
			// «Anular venta» ya no está: el foco va al título, que ahora dice «Anulado», y no se pierde en la página
			afterNextRender(() => document.getElementById('titulo-ticket')?.focus(), { injector: this.injector });
		} catch (rechazo: unknown) {
			this.errorDeAnulacion.set(mensajeDeError(comoError(rechazo)));
			this.cerrarDialogo();
			this.recurso.reload();
		} finally {
			this.anulando.set(false);
		}
	}
}

function idDe(texto: string | null): number | null {
	return texto !== null && /^\d{1,15}$/.test(texto) ? Number(texto) : null;
}
