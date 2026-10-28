import { ErrorApi } from '../modelos/error-api';
import { mensajeDeCampo, mensajeDeError, mensajeDelCampo, primerMensaje } from './mensajes-error';

function error(codigo: string | undefined, detail = 'detalle técnico del backend'): ErrorApi {
  return { status: 401, detail, codigo };
}

describe('mensajeDeError', () => {
  // HU-08 RF-18: con lo comprometido, si vino; sin él, el mensaje general del código
  it('dice cuánto está comprometido en un conteo menor', () => {
    const rechazo: ErrorApi = { status: 409, detail: 'x', codigo: 'CONTEO_MENOR_QUE_COMPROMETIDO', comprometido: 12.5 };

    expect(mensajeDeError(rechazo)).toBe('Contaron menos de lo que ya está vendido o reservado (12,5). Revise el conteo.');
    expect(mensajeDeError({ ...rechazo, comprometido: undefined })).toBe(
      'Contaron menos de lo que ya está vendido o reservado. Revise el conteo.',
    );
  });

  // HU-11 RF-4, RF-5, RF-14: el carrito explica por qué no agregó, sin decir cuánto stock hay
  it.each([
    ['PRODUCTO_AGOTADO', 'agotado'],
    ['CARRITO_LLENO', '50 productos'],
  ])('traduce %s', (codigo, motivo) => {
    expect(mensajeDeError(error(codigo))).toContain(motivo);
    expect(mensajeDeError(error(codigo))).not.toMatch(/stock/i);
  });

  // HU-12 RF-3, RF-4, RF-5, RF-12, RF-15: cada rechazo del pedido explica qué hacer, de usted y sin
  // decir cuánto stock hay
  it.each([
    ['CARRITO_VACIO', 'vacío'],
    ['PEDIDO_PENDIENTE', 'esperando el pago'],
    ['STOCK_INSUFICIENTE', 'carrito'],
    ['TOTAL_CAMBIADO', 'total'],
    ['PEDIDO_NO_PENDIENTE', 'ya no espera el pago'],
    ['PEDIDO_NO_ENCONTRADO', 'pedido'],
    // HU-13 RF-3, RF-12
    ['COBRO_NO_DISPONIBLE', 'Intente de nuevo'],
    ['COBRO_NO_ENCONTRADO', 'cobro'],
  ])('traduce %s', (codigo, motivo) => {
    const mensaje = mensajeDeError(error(codigo));

    expect(mensaje).toContain(motivo);
    expect(mensaje).not.toBe(mensajeDeError(error('ALGO_RARO')));
    expect(mensaje).not.toMatch(/\d/);
    expect(mensaje).not.toMatch(/\b(tienes|tu|puedes|vuelve)\b/i);
  });

  // Gestión de usuarios: cada operación rechazada explica su motivo, no el mensaje genérico
  it.each([
    ['ROL_PROPIO_NO_MODIFICABLE', 'propio rol'],
    ['CUENTA_PROPIA_NO_DESACTIVABLE', 'propia cuenta'],
    ['ULTIMO_ADMINISTRADOR_ACTIVO', 'administrador activo'],
  ])('traduce %s', (codigo, motivo) => {
    expect(mensajeDeError(error(codigo))).toContain(motivo);
  });

  it('traduce las credenciales incorrectas', () => {
    expect(mensajeDeError(error('CREDENCIALES_INCORRECTAS'))).toBe(
      'El correo o la contraseña no son correctos.',
    );
  });

  it('traduce la cuenta desactivada', () => {
    expect(mensajeDeError(error('CUENTA_DESACTIVADA'))).toContain('desactivada');
  });

  it('traduce el bloqueo por intentos fallidos', () => {
    expect(mensajeDeError(error('INTENTOS_EXCEDIDOS'))).toContain('15 minutos');
  });

  // HU-01 RF-13
  it('traduce REGISTROS_EXCEDIDOS', () => {
    expect(mensajeDeError(error('REGISTROS_EXCEDIDOS'))).toContain('una hora');
  });

  it('traduce el conflicto por operación simultánea', () => {
    expect(mensajeDeError(error('OPERACION_SIMULTANEA'))).toContain('Vuelva a intentar');
  });

  // El bloqueo protege cualquier dato, no solo cuentas: el mensaje no puede
  // hablar de una cuenta
  it('el conflicto por operación simultánea no habla de una cuenta', () => {
    expect(mensajeDeError(error('OPERACION_SIMULTANEA'))).not.toContain('cuenta');
  });

  it.each([
    ['IMAGEN_INVALIDA', 'JPG, PNG o WebP'],
    ['IMAGEN_DEMASIADO_GRANDE', '2 MB'],
    ['IMAGEN_CON_DEMASIADOS_PIXELES', '8000 px'],
  ])('traduce %s', (codigo, motivo) => {
    const mensaje = mensajeDeError(error(codigo));

    expect(mensaje).toContain(motivo);
    expect(mensaje).not.toBe(mensajeDeError(error('ALGO_RARO')));
  });

  it('usa un mensaje propio para un código desconocido, sin mostrar el del backend', () => {
    const mensaje = mensajeDeError(error('ALGO_RARO'));

    expect(mensaje).not.toContain('detalle técnico');
    expect(mensaje).not.toBe('');
  });

  // Un 500 llega sin la extensión `codigo`
  it('un error inesperado también tiene mensaje', () => {
    expect(mensajeDeError({ status: 500, detail: 'Ocurrió un error inesperado.' })).not.toBe('');
  });

  it('sin error devuelve cadena vacía', () => {
    expect(mensajeDeError(null)).toBe('');
  });
});

// El backend manda el mensaje del campo como predicado, para leerse detrás del
// nombre del dato: "es obligatorio", "admite hasta 80 caracteres"
describe('mensajeDeCampo', () => {
  it('nombra los campos de búsqueda', () => {
    expect(mensajeDeCampo('texto', 'admite hasta 200 caracteres')).toBe('La búsqueda admite hasta 200 caracteres.');
    expect(mensajeDeCampo('desde', 'no tiene un formato válido')).toBe('La fecha desde no tiene un formato válido.');
    expect(mensajeDeCampo('hasta', 'no puede ser anterior a la fecha desde')).toBe(
      'La fecha hasta no puede ser anterior a la fecha desde.',
    );
  });

  it('arma una frase con el nombre del campo', () => {
    expect(mensajeDeCampo('nombre', 'es obligatorio')).toBe('El nombre es obligatorio.');
    expect(mensajeDeCampo('contrasena', 'admite hasta 72 bytes')).toBe(
      'La contraseña admite hasta 72 bytes.',
    );
  });

  // Los dos campos del cambio de contraseña
  it('nombra las contraseñas actual y nueva', () => {
    expect(mensajeDeCampo('contrasenaActual', 'es obligatoria')).toBe(
      'La contraseña actual es obligatoria.',
    );
    expect(mensajeDeCampo('contrasenaNueva', 'debe tener al menos 8 caracteres')).toBe(
      'La contraseña nueva debe tener al menos 8 caracteres.',
    );
  });

  it('nombra los campos de uso común', () => {
    expect(mensajeDeCampo('fecha', 'no puede ser anterior a hoy')).toBe('La fecha no puede ser anterior a hoy.');
    expect(mensajeDeCampo('descripcion', 'es demasiado larga')).toBe('La descripción es demasiado larga.');
    expect(mensajeDeCampo('imagen', 'es obligatoria')).toBe('La imagen es obligatoria.');
    expect(mensajeDeCampo('precio', 'debe ser mayor que 0')).toBe('El precio debe ser mayor que 0.');
    expect(mensajeDeCampo('cantidad', 'debe ser de al menos 1')).toBe('La cantidad debe ser de al menos 1.');
  });

  // HU-12 RF-6
  it('nombra los datos de quien retira', () => {
    expect(mensajeDeCampo('nombreRetira', 'es obligatorio')).toBe('El nombre de quien retira es obligatorio.');
    expect(mensajeDeCampo('telefonoRetira', 'admite hasta 20 caracteres')).toBe(
      'El teléfono de quien retira admite hasta 20 caracteres.',
    );
  });

  it('no duplica el punto final', () => {
    expect(mensajeDeCampo('correo', 'no tiene formato de correo.')).toBe(
      'El correo no tiene formato de correo.',
    );
  });

  // Un campo que todavía no esté en la tabla no puede quedarse sin mensaje
  it('un campo desconocido se muestra tal como vino', () => {
    expect(mensajeDeCampo('otroCampo', 'no es válido')).toBe('no es válido.');
  });
});

// El mensaje que se muestra bajo un campo: primero lo que detectó la pantalla y,
// si ahí no hay nada, lo que respondió el backend para ese campo
describe('mensajeDelCampo', () => {
  function estado(tocado: boolean, errores: string[]) {
    return {
      touched: () => tocado,
      invalid: () => errores.length > 0,
      errors: () => errores.map((message) => ({ message })),
    };
  }

  const RECHAZO: ErrorApi = {
    status: 400,
    detail: 'Hay campos a corregir.',
    codigo: 'DATOS_INVALIDOS',
    errores: [{ campo: 'descripcion', mensaje: 'admite hasta 200 caracteres' }],
  };

  it('muestra el error de la pantalla cuando el campo ya se tocó', () => {
    expect(mensajeDelCampo(estado(true, ['Escribe la descripción.']), 'descripcion', RECHAZO)).toBe('Escribe la descripción.');
  });

  it('sin tocar, el error de la pantalla todavía no se muestra', () => {
    expect(mensajeDelCampo(estado(false, ['Escribe la descripción.']), 'descripcion', null)).toBe('');
  });

  it('si la pantalla no ve nada, muestra lo que rechazó el backend', () => {
    expect(mensajeDelCampo(estado(true, []), 'descripcion', RECHAZO)).toBe('La descripción admite hasta 200 caracteres.');
  });

  it('un campo sin problemas no muestra nada', () => {
    expect(mensajeDelCampo(estado(true, []), 'nombre', RECHAZO)).toBe('');
  });
});

describe('primerMensaje', () => {
  it('devuelve el primer error que trae mensaje', () => {
    expect(primerMensaje([{}, { message: 'Escriba su correo.' }, { message: 'otro' }])).toBe('Escriba su correo.');
  });

  it('sin mensajes devuelve vacío', () => {
    expect(primerMensaje([])).toBe('');
    expect(primerMensaje([{}])).toBe('');
  });
});
