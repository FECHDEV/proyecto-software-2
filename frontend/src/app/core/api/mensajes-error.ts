import { cantidad } from '../../shared/formato/precios';
import { ErrorApi, errorDelCampo } from '../modelos/error-api';

// Único lugar donde los códigos del backend se vuelven frases para la persona,
// para que el mismo error diga siempre lo mismo en todas las pantallas
// (docs/decisiones.md → Decisiones técnicas del frontend).
const MENSAJES: Record<string, string> = {
	CREDENCIALES_INCORRECTAS: 'El correo o la contraseña no son correctos.',
	CUENTA_DESACTIVADA: 'Esta cuenta está desactivada. Escríbanos para reactivarla.',
	INTENTOS_EXCEDIDOS: 'Demasiados intentos fallidos. Espere 15 minutos y vuelva a intentar.',
	SOLICITUDES_EXCEDIDAS: 'Ya pidió esto varias veces. Espere unos minutos y vuelva a intentar.',
	REGISTROS_EXCEDIDOS: 'Se crearon demasiadas cuentas desde esta conexión. Pruebe de nuevo en una hora.',
	DATOS_INVALIDOS: 'Revise los datos marcados.',
	CUENTA_EXISTENTE: 'Ya existe una cuenta con ese correo.',
	CONTRASENA_ACTUAL_INCORRECTA: 'La contraseña actual no es correcta.',
	TOKEN_RECUPERACION_INVALIDO: 'Este enlace ya no sirve. Pida uno nuevo.',
	TOKEN_VERIFICACION_INVALIDO: 'Este enlace ya no sirve. Pida otro en su carrito o en Mis datos.',
	REENVIOS_EXCEDIDOS: 'Ya le enviamos varios correos. Espere unos minutos y vuelva a intentar.',
	CORREO_YA_VERIFICADO: 'Su correo ya está verificado.',
	CORREO_SIN_VERIFICAR: 'Verifique su correo para confirmar el pedido. Puede pedir el correo otra vez desde su carrito.',
	USUARIO_NO_ENCONTRADO: 'No encontramos ese usuario.',
	ACCESO_DENEGADO: 'Su cuenta no tiene acceso a esta parte del sistema.',
	NO_AUTENTICADO: 'Su sesión terminó. Vuelva a iniciar sesión.',
	ROL_DE_CUENTA_DESACTIVADA: 'No se puede cambiar el rol de una cuenta desactivada. Reactívela primero.',
	TIPO_SOLO_CLIENTE: 'Solo una cuenta de cliente tiene tipo de cliente.',
	PERIODO_INVALIDO: 'El período no es válido. Elija las fechas de nuevo.',
	FORMATO_INVALIDO: 'Ese formato no se puede exportar. Elija Excel o PDF.',
	ROL_PROPIO_NO_MODIFICABLE: 'No puede cambiar su propio rol.',
	CUENTA_PROPIA_NO_DESACTIVABLE: 'No puede desactivar su propia cuenta.',
	ULTIMO_ADMINISTRADOR_ACTIVO: 'Debe quedar al menos un administrador activo. Nombre a otro antes de hacer este cambio.',
	OPERACION_SIMULTANEA: 'Otra persona está modificando estos datos. Vuelva a intentar.',
	CATEGORIA_EXISTENTE: 'Ya existe una categoría con ese nombre.',
	CATEGORIA_NO_ENCONTRADA: 'Esa categoría ya no existe. Recargue la página.',
	CATEGORIA_CON_PRODUCTOS: 'La categoría tiene productos y no se puede borrar. Puede ocultarla.',
	PRODUCTO_NO_ENCONTRADO: 'Ese producto ya no existe. Recargue la página.',
	PRODUCTO_LLENO_DE_IMAGENES: 'El producto ya tiene 5 fotos. Quite una antes de subir otra.',
	FICHA_INVALIDA: 'La ficha técnica tiene dos datos con el mismo nombre.',
	ORDEN_DE_IMAGENES_INVALIDO: 'Las fotos cambiaron mientras las ordenaba. Recargue la página.',
	AJUSTE_SIN_CAMBIO: 'La cantidad contada es igual a lo que hay en el estante: no hay nada que ajustar.',
	CONTEO_MENOR_QUE_COMPROMETIDO: 'Contaron menos de lo que ya está vendido o reservado. Revise el conteo.',
	STOCK_FUERA_DE_RANGO: 'Con esa entrada, lo que hay en el estante pasaría de 9.999.999,99.',
	UNIDAD_INCOMPATIBLE_CON_STOCK:
		'El stock, el mínimo o un pedido abierto de este producto tienen decimales y esa unidad no los admite. Ajuste el stock o espere a que se entregue el pedido.',
	IMAGEN_INVALIDA: 'Ese archivo no es una imagen JPG, PNG o WebP.',
	IMAGEN_DEMASIADO_GRANDE: 'La imagen pesa más de 2 MB. Elija una más liviana.',
	MODELO_INVALIDO: 'Ese archivo no es un modelo 3D .glb.',
	MODELO_DEMASIADO_GRANDE: 'El modelo pesa más de 3 MB. Prepárelo más liviano antes de subirlo.',
	PRODUCTO_AGOTADO: 'Este producto está agotado.',
	CARRITO_LLENO: 'Su carrito ya tiene 50 productos distintos. Quite alguno para agregar otro.',
	CARRITO_VACIO: 'Su carrito está vacío. Agregue algún producto antes de confirmar.',
	PEDIDO_PENDIENTE: 'Ya tiene un pedido esperando el pago. Páguelo o cancélelo antes de confirmar otro.',
	STOCK_INSUFICIENTE: 'Algunos productos ya no se pueden pedir así. Corríjalos en el carrito.',
	TOTAL_CAMBIADO: 'El total del pedido cambió. Revíselo y confirme otra vez.',
	PEDIDO_NO_PENDIENTE: 'Este pedido ya no espera el pago.',
	PEDIDO_NO_ENTREGABLE: 'Este pedido ya no se puede entregar: otra persona lo entregó o cambió de estado.',
	PEDIDO_NO_ENCONTRADO: 'No encontramos ese pedido.',
	COBRO_NO_DISPONIBLE: 'No pudimos generar el cobro. Intente de nuevo en un momento.',
	COBRO_NO_ENCONTRADO: 'Este pedido no tiene un cobro pendiente.',
	SIN_CONEXION: 'No pudimos conectar con el servidor. Intente de nuevo.',
	ERROR_SERVIDOR: 'El servidor tuvo un problema. Vuelva a intentarlo en un momento.',
};

const GENERICO = 'No pudimos completar la operación. Intente de nuevo.';

// El `detail` del backend no se muestra tal cual: puede traer detalle técnico.
// Un error inesperado (500) llega sin `codigo` y cae en el mensaje genérico.
export function mensajeDeError(error: ErrorApi | null): string {
	if (error === null) {
		return '';
	}
	if (error.codigo === 'CONTEO_MENOR_QUE_COMPROMETIDO' && error.comprometido !== undefined) {
		return `Contaron menos de lo que ya está vendido o reservado (${cantidad(error.comprometido)}). Revise el conteo.`;
	}
	return error.codigo === undefined ? GENERICO : mensajeDelCodigo(error.codigo);
}

// Lo que también se controla en el navegador antes de enviar (un modelo de más de 3 MB, HU-15)
export function mensajeDelCodigo(codigo: string): string {
	return MENSAJES[codigo] ?? GENERICO;
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
	marca: 'La marca',
	idCategoria: 'La categoría',
	unidad: 'La unidad de venta',
	ficha: 'La ficha técnica',
	fecha: 'La fecha',
	imagen: 'La imagen',
	precio: 'El precio',
	costo: 'El costo',
	texto: 'La búsqueda',
	desde: 'La fecha desde',
	hasta: 'La fecha hasta',
	cantidad: 'La cantidad',
	contada: 'La cantidad contada',
	minimo: 'El mínimo',
	motivo: 'El motivo',
	nota: 'La nota',
	estado: 'El estado',
	nombreRetira: 'El nombre de quien retira',
	telefonoRetira: 'El teléfono de quien retira',
	totalEsperado: 'El total',
};

export function mensajeDeCampo(campo: string, mensaje: string): string {
	const nombre = NOMBRES_DE_CAMPO[campo];
	const frase = nombre === undefined ? mensaje : `${nombre} ${mensaje}`;
	return frase.endsWith('.') ? frase : `${frase}.`;
}

// Una operación rechazada en una frase: el primer dato rechazado, con su
// campo; si no hay ninguno, el código
export function mensajeDelRechazo(error: ErrorApi): string {
	const primero = error.errores?.[0];
	return primero === undefined ? mensajeDeError(error) : mensajeDeCampo(primero.campo, primero.mensaje);
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
