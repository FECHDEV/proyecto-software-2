import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal } from '@angular/core';
import { form, FormField, minLength, required, schema, submit, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { mensajeDeError, mensajeDelCampo } from '../../core/api/mensajes-error';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { enfocarCampo, primerCampoRechazado } from '../../shared/formularios/primer-campo-con-error';
import { bytesUtf8 } from '../../shared/validacion/reglas';

interface ContrasenaNueva {
	contrasena: string;
}

// Las mismas reglas que el registro, que son las de RestablecimientoContrasenaRequest
const esquema = schema<ContrasenaNueva>((ruta) => {
	required(ruta.contrasena, { message: 'Elige una contraseña.' });
	minLength(ruta.contrasena, 8, { message: 'La contraseña necesita al menos 8 caracteres.' });
	validate(ruta.contrasena, ({ value }) =>
		bytesUtf8(value()) > 72
			? { kind: 'contrasenaLarga', message: 'Esa contraseña es demasiado larga. Prueba con una más corta.' }
			: null,
	);
});

@Component({
	selector: 'app-restablecer',
	imports: [FormField, RouterLink],
	templateUrl: './restablecer.page.html',
	styleUrl: './restablecer.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestablecerPage {
	private readonly autenticacion = inject(AutenticacionService);
	private readonly router = inject(Router);
	private readonly ruta = inject(ActivatedRoute);
	private readonly elemento = inject(ElementRef<HTMLElement>);

	// El token llega en el enlace del correo y no se muestra nunca: vale tanto
	// como la contraseña que permite cambiar.
	// Recortado: un enlace cortado que solo trae espacios es un enlace sin token
	private readonly token = (this.ruta.snapshot.queryParamMap.get('token') ?? '').trim();
	private readonly nueva = signal<ContrasenaNueva>({ contrasena: '' });
	private readonly errorDelServidor = signal<ErrorApi | null>(null);

	protected readonly formulario = form(this.nueva, esquema);
	protected readonly enviando = signal(false);
	// Sin token no hay nada que hacer en esta pantalla: se explica y se ofrece
	// pedir otro enlace, con el mismo texto que da un token ya usado o vencido.
	protected readonly hayToken = this.token !== '';
	protected readonly avisoDeError = computed(() => mensajeDeError(this.errorDelServidor()));
	protected readonly ayuda = computed(() => this.formulario.contrasena().value().length < 8);

	constructor() {
		if (!this.hayToken) {
			// Mismo mensaje que da un token vencido o ya usado, y del mismo lugar
			this.errorDelServidor.set({
				status: 400,
				detail: 'El enlace no trae token',
				codigo: 'TOKEN_RECUPERACION_INVALIDO',
			});
		}
	}

	protected async enviar(evento: Event): Promise<void> {
		evento.preventDefault();
		this.errorDelServidor.set(null);
		this.enviando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				try {
					await this.autenticacion.restablecerContrasena(this.token, formulario().value().contrasena);
				} catch (error: unknown) {
					const problema = comoError(error);
					this.errorDelServidor.set(problema);
					// El único campo: si el backend lo rechazó, ahí va el foco
					if (primerCampoRechazado(['contrasena'], problema.errores) !== null) {
						this.enfocarContrasena();
					}
					return undefined;
				}
				// Fuera del try: la contraseña ya cambió, y un problema al navegar no
				// puede contarse como un restablecimiento fallido
				try {
					await this.router.navigate(['/inicio-sesion'], {
						queryParams: { aviso: 'contrasena-actualizada' },
					});
				} catch {
					// Navegar puede fallar por fuera de este caso de uso. La contraseña
					// ya cambió, así que solo queda no trabar la pantalla en «Guardando…»
				}
				return undefined;
			},
		});

		this.enviando.set(false);

		if (this.formulario().invalid()) {
			this.enfocarContrasena();
		}
	}

	private enfocarContrasena(): void {
		enfocarCampo(this.elemento.nativeElement, 'contrasena');
	}

	// Lo que detectó la pantalla y, si no hay nada, lo que respondió el backend
	protected mensajeDelCampo(): string {
		return mensajeDelCampo(this.formulario.contrasena(), 'contrasena', this.errorDelServidor());
	}
}

