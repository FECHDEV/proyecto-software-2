import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { email, form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { mensajeDeError, primerMensaje } from '../../core/api/mensajes-error';
import { enfocarCampo } from '../../shared/formularios/primer-campo-con-error';
import { comoError } from '../../core/modelos/error-api';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';

interface Solicitud {
	correo: string;
}

const esquema = schema<Solicitud>((ruta) => {
	required(ruta.correo, { message: 'Escribe tu correo.' });
	sinEspaciosSolos(ruta.correo, 'Escribe tu correo.');
	email(ruta.correo, { message: 'Ese correo no tiene formato de correo.' });
	maxLength(ruta.correo, 120, { message: 'El correo admite hasta 120 caracteres.' });
});

@Component({
	selector: 'app-recuperacion',
	imports: [FormField, RouterLink],
	templateUrl: './recuperacion.page.html',
	styleUrl: './recuperacion.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecuperacionPage {
	private readonly autenticacion = inject(AutenticacionService);
	private readonly elemento = inject(ElementRef<HTMLElement>);

	protected readonly primerMensaje = primerMensaje;

	private readonly solicitud = signal<Solicitud>({ correo: '' });

	protected readonly formulario = form(this.solicitud, esquema);
	protected readonly enviando = signal(false);
	protected readonly avisoDeError = signal('');
	// La confirmación reemplaza al formulario y es la misma para cualquier
	// correo: si cambiara según exista o no la cuenta, la pantalla diría quién
	// está registrado.
	protected readonly pedida = signal(false);

	protected async enviar(evento: Event): Promise<void> {
		evento.preventDefault();
		this.avisoDeError.set('');
		this.enviando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				try {
					await this.autenticacion.solicitarRecuperacion(formulario().value().correo);
					this.pedida.set(true);
					this.enfocarConfirmacion();
				} catch (error: unknown) {
					this.avisoDeError.set(mensajeDeError(comoError(error)));
				}
				return undefined;
			},
		});

		this.enviando.set(false);

		if (this.formulario().invalid()) {
			enfocarCampo(this.elemento.nativeElement, 'correo');
		}
	}

	// El formulario que tenía el foco desaparece: sin esto, quien navega con
	// teclado o lector de pantalla queda al principio del documento sin saber
	// que pasó algo.
	private enfocarConfirmacion(): void {
		const contenedor = this.elemento.nativeElement as HTMLElement;
		queueMicrotask(() => contenedor.querySelector<HTMLElement>('.aviso.exito')?.focus());
	}
}
