import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ResultadoSimulado } from '../../core/modelos/pedido';
import { Venta } from '../../core/modelos/venta';
import { CodigoQr } from '../../shared/codigo-qr/codigo-qr';
import { comoReloj } from '../../shared/formato/tiempo';

// El QR de una venta del mostrador mientras espera el pago (HU-23 RF-7 a RF-9):
// el código, el tiempo que queda, «Ya pagó», «Cancelar venta» y, con el
// simulador, sus botones. Solo muestra: consultar, cancelar y simular los hace
// «Nueva venta». En un componente propio: los estilos de la pantalla pasaban el
// presupuesto de 6 kB
@Component({
	selector: 'app-qr-de-venta',
	imports: [CodigoQr],
	templateUrl: './qr-de-venta.html',
	styleUrl: './qr-de-venta.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QrDeVenta {
	readonly venta = input.required<Venta>();
	readonly segundos = input.required<number>();
	readonly aviso = input('');
	readonly cancelando = input(false);
	readonly simulando = input(false);
	readonly yaPago = output<void>();
	readonly cancelar = output<void>();
	readonly simular = output<ResultadoSimulado>();

	protected readonly reloj = comoReloj;
}
