import { comoError, errorDelCampo, esErrorApi } from './error-api';

// Cuerpo tal como lo manda el backend (ManejadorErrores → ProblemDetail)
const CREDENCIALES = {
  type: 'about:blank',
  title: 'Unauthorized',
  status: 401,
  detail: 'Correo o contraseña incorrectos.',
  codigo: 'CREDENCIALES_INCORRECTAS',
};

const DATOS_INVALIDOS = {
  type: 'about:blank',
  title: 'Bad Request',
  status: 400,
  detail: 'Hay campos a corregir.',
  codigo: 'DATOS_INVALIDOS',
  errores: [
    { campo: 'correo', mensaje: 'no tiene formato de correo' },
    { campo: 'contrasena', mensaje: 'es obligatoria' },
  ],
};

describe('esErrorApi', () => {
  it('reconoce una respuesta de error del backend', () => {
    expect(esErrorApi(CREDENCIALES)).toBe(true);
  });

  // Un 500 sale sin la extensión `codigo`
  it('reconoce un error inesperado, que no trae código', () => {
    expect(
      esErrorApi({ type: 'about:blank', title: 'Internal Server Error', status: 500, detail: 'Ocurrió un error inesperado.' }),
    ).toBe(true);
  });

  it('rechaza lo que no es una respuesta de error', () => {
    expect(esErrorApi(null)).toBe(false);
    expect(esErrorApi('vaya')).toBe(false);
    expect(esErrorApi({ mensaje: 'formato viejo' })).toBe(false);
  });
});

describe('errorDelCampo', () => {
  it('encuentra el mensaje de un campo', () => {
    expect(errorDelCampo(DATOS_INVALIDOS, 'correo')).toBe('no tiene formato de correo');
  });

  it('devuelve null si ese campo no tiene error', () => {
    expect(errorDelCampo(DATOS_INVALIDOS, 'telefono')).toBeNull();
    expect(errorDelCampo(CREDENCIALES, 'correo')).toBeNull();
    expect(errorDelCampo(null, 'correo')).toBeNull();
  });
});

// Los servicios rechazan con un ErrorApi; cualquier otra cosa (un error de
// programación, una promesa rechazada sin cuerpo) se trata como falta de conexión
describe('comoError', () => {
  it('deja pasar un error del backend', () => {
    expect(comoError(CREDENCIALES)).toBe(CREDENCIALES);
  });

  it('lo demás es SIN_CONEXION', () => {
    expect(comoError(new Error('vaya'))).toMatchObject({ status: 0, codigo: 'SIN_CONEXION' });
    expect(comoError(undefined)).toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
