import { ErrorApi, errorDelCampo } from '../modelos/error-api';

// Único lugar donde los códigos del backend se vuelven frases para la persona,
// para que el mismo error diga siempre lo mismo en todas las pantallas
// (docs/decisiones.md → Decisiones técnicas del frontend).
const MENSAJES: Record<string, string> = {
	CREDENCIALES_INCORRECTAS: 'El correo o la contraseña no son correctos.',
	CUENTA_DESACTIVADA: 'Esta cuenta está desactivada. Escríbenos para reactivarla.',
	INTENTOS_EXCEDIDOS: 'Demasiados intentos fallidos. Espera 15 minutos y vuelve a intentar.',
	SOLICITUDES_EXCEDIDAS: 'Ya pediste esto varias veces. Espera unos minutos y vuelve a intentar.',
	DATOS_INVALIDOS: 'Revisa los datos marcados.',
	CUENTA_EXISTENTE: 'Ya existe una cuenta con ese correo.',
	CONTRASENA_ACTUAL_INCORRECTA: 'La contraseña actual no es correcta.',
	TOKEN_RECUPERACION_INVALIDO: 'Este enlace ya no sirve. Pide uno nuevo.',
	USUARIO_NO_ENCONTRADO: 'No encontramos ese usuario.',
	ACCESO_DENEGADO: 'Tu cuenta no tiene acceso a esta parte del sistema.',
	NO_AUTENTICADO: 'Tu sesión terminó. Vuelve a iniciar sesión.',
	ROL_DE_CUENTA_DESACTIVADA: 'No se puede cambiar el rol de una cuenta desactivada. Reactívala primero.',
	ROL_PROPIO_NO_MODIFICABLE: 'No puedes cambiar tu propio rol.',
	CUENTA_PROPIA_NO_DESACTIVABLE: 'No puedes desactivar tu propia cuenta.',
	ULTIMO_ADMINISTRADOR_ACTIVO: 'Debe quedar al menos un administrador activo. Nombra a otro antes de hacer este cambio.',
	OPERACION_SIMULTANEA: 'Otra persona está modificando estos datos. Vuelve a intentar.',
	IMAGEN_INVALIDA: 'Ese archivo no es una imagen JPG, PNG o WebP.',
	IMAGEN_DEMASIADO_GRANDE: 'La imagen pesa más de 2 MB. Elige una más liviana.',
	SIN_CONEXION: 'No pudimos conectar con el servidor. Intenta de nuevo.',
};

const GENERICO = 'No pudimos completar la operación. Intenta de nuevo.';

// El `detail` del backend no se muestra tal cual: puede traer detalle técnico.
// Un error inesperado (500) llega sin `codigo` y cae en el mensaje genérico.
export function mensajeDeError(error: ErrorApi | null): string {
	if (error === null) {
		return '';
	}
	return (error.codigo === undefined ? undefined : MENSAJES[error.codigo]) ?? GENERICO;
}

// Los errores por campo del backend son predicados pensados para leerse detrás
// del nombre del dato. Se arman acá, que es el único lugar donde la respuesta
// de la API se vuelve una frase (docs/decisiones.md).
const NOMBRES_DE_CAMPO: Record<string, string> = {
	nombre: 'El nombre',
	apellido: 'El apellido',
	correo: 'El correo',
	contrasena: 'La contraseña',
	contrasenaActual: 'La contraseña actual',
	contrasenaNueva: 'La contraseña nueva',
	telefono: 'El teléfono',
	descripcion: 'La descripción',
	fecha: 'La fecha',
	imagen: 'La imagen',
	precio: 'El precio',
	texto: 'La búsqueda',
	desde: 'La fecha desde',
	hasta: 'La fecha hasta',
	cantidad: 'La cantidad',
	estado: 'El estado',
};

export function mensajeDeCampo(campo: string, mensaje: string): string {
	const nombre = NOMBRES_DE_CAMPO[campo];
	const frase = nombre === undefined ? mensaje : `${nombre} ${mensaje}`;
	return frase.endsWith('.') ? frase : `${frase}.`;
}

// Lo mínimo que hace falta de un campo de Signal Forms para elegir su mensaje
export interface EstadoDelCampo {
	touched(): boolean;
	invalid(): boolean;
	errors(): readonly { message?: string }[];
}

// El mensaje bajo un campo: primero lo que detectó la pantalla (cuando la
// persona ya pasó por el campo) y, si ahí no hay nada, lo que respondió el
// backend para ese campo
export function mensajeDelCampo(estado: EstadoDelCampo, campo: string, error: ErrorApi | null): string {
	if (estado.touched() && estado.invalid()) {
		return primerMensaje(estado.errors());
	}
	const delServidor = errorDelCampo(error, campo);
	return delServidor === null ? '' : mensajeDeCampo(campo, delServidor);
}

// El primer error de un campo que trae mensaje: el que se muestra debajo
export function primerMensaje(errores: readonly { message?: string }[]): string {
	return errores.find((cada) => cada.message !== undefined)?.message ?? '';
}
