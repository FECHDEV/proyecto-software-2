import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { VentasService } from '../../core/api/ventas.service';
import { comoError } from '../../core/modelos/error-api';
import {
	Comprador,
	CompradoresEncontrados,
	letrasDe,
	MINIMO_LETRAS_DE_COMPRADOR,
} from '../../core/modelos/venta';

// El comprador de «Nueva venta» (HU-23 RF-4, RF-25 a RF-29): un solo campo.
// Con «@» es el correo exacto y elige directo; sin «@», la lista de Clientes
// activos con ese nombre o apellido, con el correo enmascarado que manda el
// servidor. El comprador elegido y «Volver a consumidor final» los tiene «Nueva
// venta». En un componente propio: los estilos de la pantalla rozaban el
// presupuesto de 6 kB
@Component({
	selector: 'app-busqueda-de-comprador',
	templateUrl: './busqueda-de-comprador.html',
	styleUrl: './busqueda-de-comprador.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BusquedaDeComprador {
	private readonly ventas = inject(VentasService);

	// Con el QR a la vista o mientras se registra, el comprador no cambia (RF-27)
	readonly bloqueada = input(false);
	readonly elegir = output<Comprador>();

	protected readonly texto = signal('');
	protected readonly buscando = signal(false);
	protected readonly error = signal('');
	protected readonly encontrados = signal<CompradoresEncontrados | null>(null);
	// Solo cuenta la respuesta de la última búsqueda: una anterior que llega tarde se descarta
	private busqueda = 0;

	// Con el campo vacío, la lista se va: si se decide vender a consumidor final no queda abierta
	protected escribir(texto: string): void {
		this.texto.set(texto);
		// Con otro texto, la lista de la búsqueda anterior ya no es la que dice el campo, ni la que esté por llegar
		// (/code-review)
		this.descartarLaAnterior();
		this.encontrados.set(null);
	}

	protected async buscar(): Promise<void> {
		if (this.bloqueada()) {
			return;
		}
		// Cualquier respuesta que esté por llegar ya no cuenta, aunque esta búsqueda se corte antes de preguntar
		const esta = this.descartarLaAnterior();
		const texto = this.texto().trim();
		this.encontrados.set(null);
		this.error.set('');
		if (texto === '') {
			this.error.set('Escriba el nombre, el apellido o el correo del cliente.');
			return;
		}
		if (!texto.includes('@') && letrasDe(texto) < MINIMO_LETRAS_DE_COMPRADOR) {
			this.error.set(`Escriba al menos ${MINIMO_LETRAS_DE_COMPRADOR} letras del nombre o el apellido.`);
			return;
		}
		this.buscando.set(true);
		try {
			if (texto.includes('@')) {
				const cuenta = await this.ventas.buscarCliente(texto);
				// Si mientras tanto se cobró con QR o se está registrando, el comprador ya no cambia (RF-27, /code-review)
				if (esta === this.busqueda && !this.bloqueada()) {
					this.elegirCuenta(cuenta);
				}
			} else {
				const encontrados = await this.ventas.buscarClientesPorNombre(texto);
				if (esta === this.busqueda && !this.bloqueada()) {
					this.encontrados.set(encontrados);
				}
			}
		} catch (rechazo: unknown) {
			if (esta === this.busqueda) {
				const error = comoError(rechazo);
				this.error.set(
					error.codigo === 'USUARIO_NO_ENCONTRADO'
						? 'No hay una cuenta de cliente activa con ese correo.'
						: mensajeDeError(error),
				);
			}
		} finally {
			if (esta === this.busqueda) {
				this.buscando.set(false);
			}
		}
	}

	protected tocar(cuenta: Comprador): void {
		if (!this.bloqueada()) {
			this.elegirCuenta(cuenta);
		}
	}

	// Lo que lee el lector de pantalla al llegar los resultados
	protected cuantas(encontrados: CompradoresEncontrados): string {
		const total = encontrados.cuentas.length;
		if (total === 0) {
			return 'No hay una cuenta de cliente activa con ese nombre.';
		}
		const cuentas = total === 1 ? '1 cuenta encontrada.' : `${total} cuentas encontradas.`;
		return encontrados.hayMas ? `${cuentas} Hay más cuentas con ese nombre: escriba el apellido.` : cuentas;
	}

	private descartarLaAnterior(): number {
		this.buscando.set(false);
		return ++this.busqueda;
	}

	private elegirCuenta(cuenta: Comprador): void {
		this.texto.set('');
		this.encontrados.set(null);
		this.elegir.emit(cuenta);
	}
}
