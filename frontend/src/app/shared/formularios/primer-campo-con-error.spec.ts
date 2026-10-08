import { enfocarCampo, primerCampoConError, primerCampoRechazado } from './primer-campo-con-error';

const ORDEN = ['correo', 'contrasena'] as const;

describe('primerCampoConError', () => {
  it('devuelve el primero del orden cuando hay varios con error', () => {
    expect(primerCampoConError(ORDEN, { correo: true, contrasena: true })).toBe('correo');
  });

  it('saltea los campos correctos', () => {
    expect(primerCampoConError(ORDEN, { correo: false, contrasena: true })).toBe('contrasena');
  });

  it('devuelve null si no hay errores', () => {
    expect(primerCampoConError(ORDEN, { correo: false, contrasena: false })).toBeNull();
  });

  // El orden lo decide quien llama: es el de la pantalla, no el del objeto
  it('respeta el orden recibido aunque el objeto tenga otro', () => {
    const orden = ['apellido', 'nombre'] as const;

    expect(primerCampoConError(orden, { nombre: true, apellido: true })).toBe('apellido');
  });
});

// Los errores que devuelve el backend por campo, en el orden de la pantalla
describe('primerCampoRechazado', () => {
  it('devuelve el primero del orden que el backend rechazó', () => {
    const errores = [
      { campo: 'contrasena', mensaje: 'debe tener al menos 8 caracteres' },
      { campo: 'correo', mensaje: 'no tiene formato de correo' },
    ];

    expect(primerCampoRechazado(ORDEN, errores)).toBe('correo');
  });

  it('ignora campos que no están en la pantalla', () => {
    expect(primerCampoRechazado(ORDEN, [{ campo: 'rol', mensaje: 'x' }])).toBeNull();
  });

  it('sin errores por campo devuelve null', () => {
    expect(primerCampoRechazado(ORDEN, undefined)).toBeNull();
  });
});

describe('enfocarCampo', () => {
  afterEach(() => (document.body.innerHTML = ''));

  it('pone el foco en el campo con ese id dentro del contenedor', () => {
    document.body.innerHTML = '<form><input id="correo" /><input id="contrasena" /></form>';
    const contenedor = document.querySelector('form') as HTMLElement;

    enfocarCampo(contenedor, 'contrasena');

    expect(document.activeElement?.id).toBe('contrasena');
  });

  it('sin campo, o si no existe, no hace nada', () => {
    document.body.innerHTML = '<form><input id="correo" /></form>';
    const contenedor = document.querySelector('form') as HTMLElement;

    expect(() => enfocarCampo(contenedor, null)).not.toThrow();
    expect(() => enfocarCampo(contenedor, 'no-existe')).not.toThrow();
    expect(document.activeElement).toBe(document.body);
  });
});
