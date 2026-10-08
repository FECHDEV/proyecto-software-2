import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal } from '@angular/core';
import {
	email,
	form,
	FormField,
	maxLength,
	minLength,
	required,
	schema,
	submit,
	validate,
} from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { EstadoDelCampo, mensajeDeError, mensajeDelCampo } from '../../core/api/mensajes-error';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { DatosDeRegistro } from '../../core/modelos/registro';
import {
	enfocarCampo,
	primerCampoConError,
	primerCampoRechazado,
} from '../../shared/formularios/primer-campo-con-error';
import { parametrosDelDestino } from '../../shared/navegacion/destino-interno';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';
import { bytesUtf8 } from '../../shared/validacion/reglas';

type CampoDelFormulario = keyof DatosDeRegistro;

const ORDEN_DE_CAMPOS: readonly CampoDelFormulario[] = [
	'nombre',
	'apellido',
	'correo',
	'contrasena',
	'telefono',
];

const SIN_DATOS: DatosDeRegistro = {
	nombre: '',
	apellido: '',
	correo: '',
	contrasena: '',
	telefono: '',
};

// Las mismas reglas que valida el backend en RegistroClienteRequest. Acá se
// repiten para no ir y volver por un dato que ya se sabe que está mal; el
// backend sigue siendo quien decide.
const esquema = schema<DatosDeRegistro>((ruta) => {
	required(ruta.nombre, { message: 'Escribe tu nombre.' });
	sinEspaciosSolos(ruta.nombre, 'Escribe tu nombre.');
	maxLength(ruta.nombre, 80, { message: 'El nombre admite hasta 80 caracteres.' });

	required(ruta.apellido, { message: 'Escribe tu apellido.' });
	sinEspaciosSolos(ruta.apellido, 'Escribe tu apellido.');
	maxLength(ruta.apellido, 80, { message: 'El apellido admite hasta 80 caracteres.' });

	required(ruta.correo, { message: 'Escribe tu correo.' });
	sinEspaciosSolos(ruta.correo, 'Escribe tu correo.');
	email(ruta.correo, { message: 'Ese correo no tiene formato de correo.' });
	maxLength(ruta.correo, 120, { message: 'El correo admite hasta 120 caracteres.' });

	required(ruta.contrasena, { message: 'Elige una contraseña.' });
	minLength(ruta.contrasena, 8, { message: 'La contraseña necesita al menos 8 caracteres.' });
	// El límite del backend es de bytes, no de caracteres: es el de BCrypt
	validate(ruta.contrasena, ({ value }) =>
		bytesUtf8(value()) > 72
			? { kind: 'contrasenaLarga', message: 'Esa contraseña es demasiado larga. Prueba con una más corta.' }
			: null,
	);

	maxLength(ruta.telefono, 20, { message: 'El teléfono admite hasta 20 caracteres.' });
});

@Component({
	selector: 'app-registro',
	imports: [FormField, RouterLink],
	templateUrl: './registro.page.html',
	styleUrl: './registro.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistroPage {
	private readonly autenticacion = inject(AutenticacionService);
	private readonly router = inject(Router);
	private readonly elemento = inject(ElementRef<HTMLElement>);

	// A dónde iba la persona antes de crear la cuenta: vuelve ahí al entrar
	protected readonly paraIniciarSesion = parametrosDelDestino(
		inject(ActivatedRoute).snapshot.queryParamMap.get('destino'),
	);

	private readonly datos = signal<DatosDeRegistro>({ ...SIN_DATOS });

	protected readonly formulario = form(this.datos, esquema);
	protected readonly enviando = signal(false);
	// Lo que respondió el backend y con qué datos se lo pidió
	private readonly errorDelServidor = signal<ErrorApi | null>(null);
	private readonly datosEnviados = signal<DatosDeRegistro | null>(null);

	// La respuesta del backend habla de los datos que se enviaron: en cuanto se
	// corrige uno, deja de describir lo que hay en pantalla y se descarta.
	private readonly errorVigente = computed<ErrorApi | null>(() => {
		const enviados = this.datosEnviados();
		if (enviados === null) {
			return null;
		}
		const actuales = this.formulario().value();
		return ORDEN_DE_CAMPOS.every((campo) => actuales[campo] === enviados[campo])
			? this.errorDelServidor()
			: null;
	});

	protected readonly avisoDeError = computed(() => mensajeDeError(this.errorVigente()));
	// La ayuda del mínimo acompaña hasta que se cumple; después sobra
	protected readonly ayudaContrasena = computed(() => this.formulario.contrasena().value().length < 8);

	protected async enviar(evento: Event): Promise<void> {
		evento.preventDefault();
		this.errorDelServidor.set(null);
		this.datosEnviados.set(null);
		this.enviando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				const datos = formulario().value();
				try {
					await this.autenticacion.registrarCliente(datos);
				} catch (error: unknown) {
					this.mostrarError(datos, comoError(error));
					return undefined;
				}
				// Fuera del try: la cuenta ya está creada, y un problema al navegar
				// no puede contarse como un registro fallido
				try {
					await this.router.navigate(['/inicio-sesion'], {
						queryParams: {
							aviso: 'cuenta-creada',
							correo: datos.correo.trim().toLowerCase(),
							...this.paraIniciarSesion,
						},
					});
				} catch {
					// Navegar puede fallar por fuera de este caso de uso. La cuenta ya
					// está creada, así que solo hay que no dejar la pantalla trabada.
				}
				return undefined;
			},
		});

		this.enviando.set(false);

		if (this.formulario().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(ORDEN_DE_CAMPOS, {
					nombre: this.formulario.nombre().invalid(),
					apellido: this.formulario.apellido().invalid(),
					correo: this.formulario.correo().invalid(),
					contrasena: this.formulario.contrasena().invalid(),
					telefono: this.formulario.telefono().invalid(),
				}),
			);
		}
	}

	// El mensaje que va debajo de un campo: primero lo que detectó la pantalla y,
	// si ahí no hay nada, lo que respondió el backend para ese campo.
	protected mensaje(estado: EstadoDelCampo, campo: CampoDelFormulario): string {
		return mensajeDelCampo(estado, campo, this.errorVigente());
	}

	private mostrarError(datos: DatosDeRegistro, error: ErrorApi): void {
		this.datosEnviados.set(datos);
		this.errorDelServidor.set(error);
		// FA-02: el correo es el único dato a cambiar, así que ahí va el foco
		if (error.codigo === 'CUENTA_EXISTENTE') {
			enfocarCampo(this.elemento.nativeElement, 'correo');
		} else {
			enfocarCampo(this.elemento.nativeElement, primerCampoRechazado(ORDEN_DE_CAMPOS, error.errores));
		}
	}
}

