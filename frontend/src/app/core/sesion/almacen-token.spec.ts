import { Sesion } from '../modelos/sesion';
import { borrarSesion, CLAVE_SESION, guardarSesion, leerSesion } from './almacen-token';

const ANA: Sesion['usuario'] = {
  idUsuario: 2,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  telefono: null,
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-14T10:00:00',
};

function sesion(expiracion: string): Sesion {
  return { token: 'jwt.de.prueba', tipo: 'Bearer', expiracion, usuario: ANA };
}

const AHORA = new Date('2026-09-17T12:00:00Z');

describe('almacén del token', () => {
  beforeEach(() => localStorage.clear());

  it('devuelve la sesión guardada mientras el token esté vigente', () => {
    guardarSesion(sesion('2026-09-17T20:00:00Z'));

    expect(leerSesion(AHORA)).toEqual(sesion('2026-09-17T20:00:00Z'));
  });

  it('descarta y borra la sesión vencida', () => {
    guardarSesion(sesion('2026-09-17T10:00:00Z'));

    expect(leerSesion(AHORA)).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
  });

  it('devuelve null si no hay nada guardado', () => {
    expect(leerSesion(AHORA)).toBeNull();
  });

  it('devuelve null si el dato guardado no es una sesión', () => {
    localStorage.setItem(CLAVE_SESION, '{ esto no es json');

    expect(leerSesion(AHORA)).toBeNull();
  });

  it('devuelve null si al dato guardado le faltan campos', () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify({ token: 'suelto' }));

    expect(leerSesion(AHORA)).toBeNull();
  });

  it('borrarSesion deja el almacenamiento limpio', () => {
    guardarSesion(sesion('2026-09-17T20:00:00Z'));

    borrarSesion();

    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
  });

  // En modo privado o con el almacenamiento lleno, localStorage lanza
  it('no rompe si el navegador no deja escribir', () => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = () => {
      throw new DOMException('almacenamiento no disponible');
    };

    expect(() => guardarSesion(sesion('2026-09-17T20:00:00Z'))).not.toThrow();

    Storage.prototype.setItem = original;
  });

  it('no rompe si el navegador no deja leer', () => {
    const original = Storage.prototype.getItem;
    Storage.prototype.getItem = () => {
      throw new DOMException('almacenamiento no disponible');
    };

    expect(leerSesion(AHORA)).toBeNull();

    Storage.prototype.getItem = original;
  });
});
