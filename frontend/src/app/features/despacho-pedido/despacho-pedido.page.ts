import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DespachoService } from '../../core/api/despacho.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { FichaDePedido } from '../../core/modelos/despacho';
import { comoError, comoErrorApi } from '../../core/modelos/error-api';
import { LineaDePedido, NOMBRES_DE_ESTADO, TONO_DE_ESTADO } from '../../core/modelos/pedido';
import { SesionService } from '../../core/sesion/sesion.service';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { fechaCorta, horaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';

// La ficha de un pedido para el personal (HU-17 RF-4): lo pedido, quién lo
// retira y la cuenta que lo pidió. Pagado, el Empleado y el Administrador
// registran la entrega después de comparar el nombre con el documento (RF-5);
// el Contador solo mira. Ocultar el botón es usabilidad: autoriza el backend
@Component({
	selector: 'app-despacho-pedido',
	imports: [RouterLink, DialogoConfirmacion],
	templateUrl: './despacho-pedido.page.html',
	styleUrl: './despacho-pedido.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DespachoPedidoPage {
	private readonly despacho = inject(DespachoService);
	private readonly sesion = inject(SesionService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
	private readonly injector = inject(Injector);

	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });
	// Lo que no es un número de pedido no llega a la API (RF-12)
	protected readonly idPedido = computed(() => idDeLaUrl(this.parametros().get('id')));

	protected readonly recurso = this.despacho.ficha(this.idPedido);
	protected readonly ficha = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	private readonly estadoDeError = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? null : comoErrorApi(error);
	});
	protected readonly noEncontrado = computed(() => this.idPedido() === null || this.estadoDeError()?.status === 404);
	protected readonly errorDeCarga = computed(() => {
		const error = this.estadoDeError();
		return error === null || error.status === 404 ? '' : mensajeDeError(error);
	});

	private readonly puedeEntregarPorRol = computed(() => {
		const rol = this.sesion.rol();
		return rol === 'EMPLEADO' || rol === 'ADMINISTRADOR';
	});
	protected readonly puedeEntregar = computed(() => this.puedeEntregarPorRol() && this.ficha()?.estado === 'PAGADO');
	protected readonly entregando = signal(false);
	// Se reinicia con otro pedido en la misma pantalla
	protected readonly aviso = linkedSignal({ source: this.idPedido, computation: () => '' });

	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly tono = TONO_DE_ESTADO;
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;
	protected readonly fecha = fechaCorta;

	protected hora(fechaHora: string): string {
		return horaCorta(fechaHora.slice(11));
	}

	// Quién y cuándo (RF-4); los dos vienen siempre con un pedido entregado
	protected entrega(ficha: FichaDePedido): string {
		const cuando = ficha.entregado === null ? '' : ` el ${this.fecha(ficha.entregado)} a las ${this.hora(ficha.entregado)}`;
		const quien = ficha.entregadoPor === null ? '' : ` por ${ficha.entregadoPor}`;
		return `Entregado${cuando}${quien}.`;
	}

	// «Unidad» no se escribe, como en la página del pedido del Cliente
	protected unidad(linea: LineaDePedido): string {
		return linea.unidad === 'UNIDAD' ? '' : ` / ${linea.unidad.toLowerCase()}`;
	}

	protected async entregar(): Promise<void> {
		const ficha = this.ficha();
		if (ficha === undefined || this.entregando()) {
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: '¿Registrar la entrega?',
			mensaje:
				`Pedido N.º ${ficha.idPedido} para ${ficha.nombreRetira}, teléfono ${ficha.telefonoRetira}. ` +
				'Compare el nombre con el documento de quien lo retira: la entrega no se puede deshacer.',
			confirmar: 'Entregar',
		});
		if (!confirmado) {
			return;
		}
		this.aviso.set('');
		this.entregando.set(true);
		try {
			const entregada = await this.despacho.entregar(ficha.idPedido);
			// Mientras tanto se abrió otro pedido: la respuesta ya no es de esta pantalla
			if (this.idPedido() !== ficha.idPedido) {
				return;
			}
			this.recurso.value.set(entregada);
			// El botón ya no está: el foco va al resultado
			this.enfocar('.entregado');
		} catch (rechazo: unknown) {
			const error = comoError(rechazo);
			if (this.idPedido() !== ficha.idPedido) {
				return;
			}
			this.aviso.set(mensajeDeError(error));
			// Otro lo entregó antes, o cambió de estado: se muestra como está ahora (RF-7)
			if (error.codigo === 'PEDIDO_NO_ENTREGABLE') {
				this.recurso.reload();
			}
			this.enfocar('.aviso');
		} finally {
			this.entregando.set(false);
		}
	}

	// Después de dibujar el cambio
	private enfocar(selector: string): void {
		afterNextRender(() => this.elemento.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
			injector: this.injector,
		});
	}
}
