import {
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	linkedSignal,
	signal,
} from '@angular/core';
import {
	form,
	FormField,
	maxLength,
	minLength,
	required,
	schema,
	submit,
	validate,
} from '@angular/forms/signals';

import { CuentaService } from '../../core/api/cuenta.service';
import { EstadoDelCampo, mensajeDeError, mensajeDelCampo } from '../../core/api/mensajes-error';
import { CambioDeContrasena, DatosPersonales } from '../../core/modelos/cuenta';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { EstadoCuenta, NOMBRES_DE_ESTADO, NOMBRES_DE_ROL, Rol, Usuario } from '../../core/modelos/usuario';
import { ConCambiosSinGuardar } from '../../core/guards/cambios-sin-guardar.guard';
import { SesionService } from '../../core/sesion/sesion.service';
import { iguales } from '../../shared/formularios/iguales';
import {
	enfocarCampo,
	primerCampoConError,
	primerCampoRechazado,
} from '../../shared/formularios/primer-campo-con-error';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';
import { fechaCorta } from '../../shared/formato/fechas';
import { bytesUtf8 } from '../../shared/validacion/reglas';

type CampoDeDatos = keyof DatosPersonales;

const CAMPOS_DE_DATOS: readonly CampoDeDatos[] = ['nombre', 'apellido', 'telefono'];

type CampoDeContrasena = keyof CambioDeContrasena;

const CAMPOS_DE_CONTRASENA: readonly CampoDeContrasena[] = ['contrasenaActual', 'contrasenaNueva'];

const SIN_CONTRASENAS: CambioDeContrasena = { contrasenaActual: '', contrasenaNueva: '' };

// Las reglas de DatosPersonalesRequest, que son las del registro
const esquemaDeDatos = schema<DatosPersonales>((ruta) => {
	required(ruta.nombre, { message: 'Escribe tu nombre.' });
	sinEspaciosSolos(ruta.nombre, 'Escribe tu nombre.');
	maxLength(ruta.nombre, 80, { message: 'El nombre admite hasta 80 caracteres.' });

	required(ruta.apellido, { message: 'Escribe tu apellido.' });
	sinEspaciosSolos(ruta.apellido, 'Escribe tu apellido.');
	maxLength(ruta.apellido, 80, { message: 'El apellido admite hasta 80 caracteres.' });

	maxLength(ruta.telefono, 20, { message: 'El teléfono admite hasta 20 caracteres.' });
});

// Las de CambioContrasenaRequest: la actual solo tiene que estar; la nueva
// sigue las reglas de la contraseña del registro
const esquemaDeContrasena = schema<CambioDeContrasena>((ruta) => {
	required(ruta.contrasenaActual, { message: 'Escribe tu contraseña actual.' });

	required(ruta.contrasenaNueva, { message: 'Elige una contraseña nueva.' });
	minLength(ruta.contrasenaNueva, 8, {
		message: 'La contraseña nueva necesita al menos 8 caracteres.',
	});
	// El límite del backend es de bytes, no de caracteres: es el de BCrypt
	validate(ruta.contrasenaNueva, ({ value }) =>
		bytesUtf8(value()) > 72
			? { kind: 'contrasenaLarga', message: 'Esa contraseña es demasiado larga. Prueba con una más corta.' }
			: null,
	);
});

// Pantalla de Mis datos. La cuenta es siempre la de la sesión: el backend la saca
// del token, así que acá no hay ningún id.
@Component({
	selector: 'app-mis-datos',
	imports: [FormField],
	templateUrl: './mis-datos.page.html',
	styleUrl: './mis-datos.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '(window:beforeunload)': 'alCerrarLaPestana($event)' },
})
export class MisDatosPage implements ConCambiosSinGuardar {
	private readonly cuenta = inject(CuentaService);
	private readonly sesion = inject(SesionService);
	private readonly elemento = inject(ElementRef<HTMLElement>);

	protected readonly recurso = this.cuenta.datosPersonales();
	protected readonly usuario = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	// Un 401 lo resuelve el interceptor cerrando la sesión; cualquier otro fallo
	// de la lectura se ofrece reintentar
	protected readonly cargaFallida = computed(() => this.recurso.error() !== undefined);

	// --- Datos personales ---

	// Se rellena con lo que devuelve el backend, al cargar y después de guardar
	private readonly datos = linkedSignal<DatosPersonales>(() => aFormulario(this.recurso.value()));
	protected readonly formularioDeDatos = form(this.datos, esquemaDeDatos);
	protected readonly guardandoDatos = signal(false);
	private readonly errorDeDatos = signal<ErrorApi | null>(null);
	private readonly datosEnviados = signal<DatosPersonales | null>(null);
	private readonly datosGuardados = signal<DatosPersonales | null>(null);

	// La respuesta del backend describe lo que se envió: al corregir un dato deja
	// de valer, igual que en el registro
	private readonly errorDeDatosVigente = computed(() =>
		iguales(this.datosEnviados(), this.formularioDeDatos().value()) ? this.errorDeDatos() : null,
	);
	protected readonly avisoDeDatos = computed(() => mensajeDeError(this.errorDeDatosVigente()));
	// La confirmación habla de lo que está en pantalla: si se edita, ya no
	protected readonly datosAlDia = computed(() =>
		iguales(this.datosGuardados(), this.formularioDeDatos().value()),
	);

	// --- Contraseña ---

	private readonly contrasenas = signal<CambioDeContrasena>({ ...SIN_CONTRASENAS });
	protected readonly formularioDeContrasena = form(this.contrasenas, esquemaDeContrasena);
	protected readonly cambiandoContrasena = signal(false);
	private readonly errorDeContrasena = signal<ErrorApi | null>(null);
	private readonly contrasenasEnviadas = signal<CambioDeContrasena | null>(null);
	private readonly contrasenaCambiada = signal(false);

	private readonly errorDeContrasenaVigente = computed(() =>
		iguales(this.contrasenasEnviadas(), this.formularioDeContrasena().value())
			? this.errorDeContrasena()
			: null,
	);
	protected readonly avisoDeContrasena = computed(() => mensajeDeError(this.errorDeContrasenaVigente()));
	// Los campos se vacían al cambiarla: en cuanto se escribe otra vez, la
	// confirmación ya no habla de lo que hay en pantalla
	protected readonly contrasenaAlDia = computed(
		() => this.contrasenaCambiada() && iguales(SIN_CONTRASENAS, this.formularioDeContrasena().value()),
	);
	// La ayuda del mínimo acompaña hasta que se cumple, como en el registro
	protected readonly ayudaContrasenaNueva = computed(
		() => this.formularioDeContrasena.contrasenaNueva().value().length < 8,
	);

	// --- Cambios sin guardar ---

	// Datos distintos de los cargados, o una contraseña a medio escribir
	private readonly hayCambios = computed(() => {
		const cargado = this.usuario();
		if (cargado === undefined) {
			return false;
		}
		return (
			!iguales(aFormulario(cargado), this.formularioDeDatos().value()) ||
			!iguales(SIN_CONTRASENAS, this.formularioDeContrasena().value())
		);
	});

	// Lo consulta cambiosSinGuardarGuard antes de navegar a otra pantalla
	tieneCambiosSinGuardar(): boolean {
		return this.hayCambios();
	}

	// Cerrar la pestaña o recargar: el navegador muestra su propio aviso
	protected alCerrarLaPestana(evento: BeforeUnloadEvent): void {
		if (this.hayCambios()) {
			evento.preventDefault();
		}
	}

	protected nombreDeRol(rol: Rol): string {
		return NOMBRES_DE_ROL[rol];
	}

	protected nombreDeEstado(estado: EstadoCuenta): string {
		return NOMBRES_DE_ESTADO[estado];
	}

	protected readonly fecha = fechaCorta;

	protected async guardarDatos(evento: Event): Promise<void> {
		evento.preventDefault();
		this.errorDeDatos.set(null);
		this.datosEnviados.set(null);
		this.datosGuardados.set(null);
		this.guardandoDatos.set(true);

		await submit(this.formularioDeDatos, {
			action: async (formulario) => {
				const enviados = formulario().value();
				try {
					const usuario = await this.cuenta.actualizarDatos(enviados);
					this.recurso.value.set(usuario);
					this.sesion.actualizarUsuario(usuario);
					this.datosGuardados.set(aFormulario(usuario));
				} catch (error: unknown) {
					const problema = comoError(error);
					this.datosEnviados.set(enviados);
					this.errorDeDatos.set(problema);
					enfocarCampo(this.elemento.nativeElement, primerCampoRechazado(CAMPOS_DE_DATOS, problema.errores));
				}
				return undefined;
			},
		});

		this.guardandoDatos.set(false);

		if (this.formularioDeDatos().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(CAMPOS_DE_DATOS, {
					nombre: this.formularioDeDatos.nombre().invalid(),
					apellido: this.formularioDeDatos.apellido().invalid(),
					telefono: this.formularioDeDatos.telefono().invalid(),
				}),
			);
		}
	}

	protected async cambiarContrasena(evento: Event): Promise<void> {
		evento.preventDefault();
		this.errorDeContrasena.set(null);
		this.contrasenasEnviadas.set(null);
		this.contrasenaCambiada.set(false);
		this.cambiandoContrasena.set(true);

		await submit(this.formularioDeContrasena, {
			action: async (formulario) => {
				const enviadas = formulario().value();
				try {
					// El token con que se pidió el cambio deja de valer desde la
					// siguiente petición (RF-13): la sesión sigue con el nuevo
					this.sesion.iniciar(await this.cuenta.cambiarContrasena(enviadas));
					this.formularioDeContrasena().reset({ ...SIN_CONTRASENAS });
					this.contrasenaCambiada.set(true);
				} catch (error: unknown) {
					const problema = comoError(error);
					this.contrasenasEnviadas.set(enviadas);
					this.errorDeContrasena.set(problema);
					// RF-11: la actual es el dato a corregir
					if (problema.codigo === 'CONTRASENA_ACTUAL_INCORRECTA') {
						enfocarCampo(this.elemento.nativeElement, 'contrasenaActual');
					} else {
						enfocarCampo(this.elemento.nativeElement, primerCampoRechazado(CAMPOS_DE_CONTRASENA, problema.errores));
					}
				}
				return undefined;
			},
		});

		this.cambiandoContrasena.set(false);

		if (this.formularioDeContrasena().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(CAMPOS_DE_CONTRASENA, {
					contrasenaActual: this.formularioDeContrasena.contrasenaActual().invalid(),
					contrasenaNueva: this.formularioDeContrasena.contrasenaNueva().invalid(),
				}),
			);
		}
	}

	protected mensajeDeContrasena(estado: EstadoDelCampo, campo: CampoDeContrasena): string {
		return mensajeDelCampo(estado, campo, this.errorDeContrasenaVigente());
	}

	protected mensajeDeDatos(estado: EstadoDelCampo, campo: CampoDeDatos): string {
		return mensajeDelCampo(estado, campo, this.errorDeDatosVigente());
	}
}

function aFormulario(usuario: Usuario | undefined): DatosPersonales {
	return {
		nombre: usuario?.nombre ?? '',
		apellido: usuario?.apellido ?? '',
		telefono: usuario?.telefono ?? '',
	};
}



