import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { email, form, FormField, required, schema, submit } from '@angular/forms/signals';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { mensajeDeError, primerMensaje } from '../../core/api/mensajes-error';
import { comoError } from '../../core/modelos/error-api';
import { SesionService } from '../../core/sesion/sesion.service';
import { enfocarCampo, primerCampoConError } from '../../shared/formularios/primer-campo-con-error';
import { destinoInterno, parametrosDelDestino } from '../../shared/navegacion/destino-interno';

// Lo que se viene a contar desde otra pantalla. Un solo parámetro con valores
// conocidos, en vez de una bandera por caso.
const AVISOS: Record<string, string> = {
	'cuenta-creada': 'Tu cuenta quedó creada. Inicia sesión para entrar.',
	'contrasena-actualizada': 'Tu contraseña quedó actualizada. Inicia sesión con la nueva.',
};

interface Credenciales {
	correo: string;
	contrasena: string;
}

type CampoDelFormulario = keyof Credenciales;

const ORDEN_DE_CAMPOS: readonly CampoDelFormulario[] = ['correo', 'contrasena'];

const esquema = schema<Credenciales>((ruta) => {
	required(ruta.correo, { message: 'Escribe tu correo.' });
	email(ruta.correo, { message: 'Ese correo no tiene formato de correo.' });
	required(ruta.contrasena, { message: 'Escribe tu contraseña.' });
});

@Component({
	selector: 'app-inicio-sesion',
	imports: [FormField, RouterLink],
	templateUrl: './inicio-sesion.page.html',
	styleUrl: './inicio-sesion.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioSesionPage {
	private readonly autenticacion = inject(AutenticacionService);
	private readonly sesion = inject(SesionService);
	private readonly router = inject(Router);
	private readonly ruta = inject(ActivatedRoute);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly ubicacion = inject(Location);

	protected readonly primerMensaje = primerMensaje;

	private readonly parametros = this.ruta.snapshot.queryParamMap;
	private readonly credenciales = signal<Credenciales>({
		correo: correoDeAlta(this.parametros),
		contrasena: '',
	});

	// Se llega acá desde el registro o desde el restablecimiento
	protected readonly aviso = signal(avisoDe(this.parametros.get('aviso')));

	// Quien no tiene cuenta la crea y vuelve igual a donde iba
	protected readonly paraElRegistro = parametrosDelDestino(this.parametros.get('destino'));

	constructor() {
		// Los parámetros ya se leyeron. Se sacan de la dirección para que el correo
		// no quede en el historial y el aviso no reaparezca al recargar; el destino
		// que dejó el guard sí se conserva.
		if (this.parametros.has('aviso') || this.parametros.has('correo')) {
			const destino = this.parametros.get('destino');
			this.ubicacion.replaceState(
				'/inicio-sesion',
				destino === null ? '' : `destino=${encodeURIComponent(destino)}`,
			);
		}
	}
	protected readonly formulario = form(this.credenciales, esquema);
	protected readonly enviando = signal(false);
	protected readonly avisoDeError = signal('');

	protected async enviar(evento: Event): Promise<void> {
		evento.preventDefault();
		// El aviso ya cumplió: a partir de acá manda lo que pase al entrar
		this.aviso.set('');
		this.avisoDeError.set('');
		this.enviando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				const { correo, contrasena } = formulario().value();
				try {
					this.sesion.iniciar(await this.autenticacion.iniciarSesion(correo, contrasena));
					// Por URL y no por segmentos: el destino puede traer sus propios
					// parámetros, y como segmento el ? se codificaría como %3F
					await this.router.navigateByUrl(this.destino());
				} catch (error: unknown) {
					this.avisoDeError.set(mensajeDeError(comoError(error)));
				}
				return undefined;
			},
		});

		this.enviando.set(false);

		if (this.formulario().invalid()) {
			this.enfocarPrimerError();
		}
	}

	// El primer campo con error recibe el foco, para no dejar a quien usa teclado
	// o lector de pantalla buscando qué falló. Va después del siguiente pintado:
	// si se hace antes, el refresco de la vista se lleva el foco.
	private enfocarPrimerError(): void {
		const campo = primerCampoConError(ORDEN_DE_CAMPOS, {
			correo: this.formulario.correo().invalid(),
			contrasena: this.formulario.contrasena().invalid(),
		});

		enfocarCampo(this.elemento.nativeElement, campo);
	}

	// A dónde ir después de entrar: la ruta que la persona quería, si es del sitio
	private destino(): string {
		return destinoInterno(this.ruta.snapshot.queryParamMap.get('destino')) ?? '/';
	}
}

// El valor llega por la URL: solo cuentan las claves propias de la tabla, para
// que `?aviso=constructor` no termine pintando una función.
function avisoDe(clave: string | null): string {
	return clave !== null && Object.hasOwn(AVISOS, clave) ? AVISOS[clave] : '';
}

// El correo llega por la URL, así que se usa solo si parece un correo y entra
// en el largo que admite la cuenta; si no, el campo arranca vacío.
function correoDeAlta(parametros: ParamMap): string {
	const correo = parametros.get('correo') ?? '';
	return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo) && correo.length <= 120 ? correo : '';
}
