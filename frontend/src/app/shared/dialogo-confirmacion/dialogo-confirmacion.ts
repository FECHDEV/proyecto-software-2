import { ChangeDetectionStrategy, Component, ElementRef, signal, viewChild } from '@angular/core';

export interface PedidoDeConfirmacion {
	titulo: string;
	mensaje: string;
	// El verbo de la acción, no un «Aceptar» genérico: «Desactivar», «Cambiar»
	confirmar: string;
	// La salida segura; por defecto «Cancelar». Se cambia cuando la acción misma
	// es cancelar algo, para no poner «Cancelar» junto a «Cancelar pedido»
	desistir?: string;
}

let siguienteId = 0;

// Diálogo para confirmar una acción que cambia algo importante. Usa el
// <dialog> del navegador con showModal(): atrapa el foco, Escape lo cierra y
// el resto de la página queda inerte mientras está abierto.
@Component({
	selector: 'app-dialogo-confirmacion',
	templateUrl: './dialogo-confirmacion.html',
	styleUrl: './dialogo-confirmacion.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoConfirmacion {
	private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

	protected readonly id = `dialogo-confirmacion-${siguienteId++}`;
	protected readonly pedido = signal<PedidoDeConfirmacion>({ titulo: '', mensaje: '', confirmar: '' });

	private resolver: ((confirmado: boolean) => void) | null = null;
	private confirmado = false;
	private origen: HTMLElement | null = null;

	// Responde true si la persona confirma; false si cancela o cierra con Escape
	abrir(pedido: PedidoDeConfirmacion): Promise<boolean> {
		this.resolver?.(false);
		this.pedido.set(pedido);
		this.confirmado = false;
		this.origen = document.activeElement instanceof HTMLElement ? document.activeElement : null;
		this.dialogo().nativeElement.showModal();
		return new Promise((resolver) => (this.resolver = resolver));
	}

	protected responder(confirmado: boolean): void {
		this.confirmado = confirmado;
		this.dialogo().nativeElement.close();
	}

	// Llega por cualquier cierre: un botón o Escape
	protected alCerrar(): void {
		this.resolver?.(this.confirmado);
		this.resolver = null;
		this.origen?.focus();
		this.origen = null;
	}
}
